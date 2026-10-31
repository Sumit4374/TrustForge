package com.sumit.trustforge.execution;

import com.sumit.trustforge.access.ProjectAccess;
import com.sumit.trustforge.audit.AuditService;
import com.sumit.trustforge.evaluation.EvaluationStore;
import com.sumit.trustforge.evaluation.EvaluationStore.*;
import com.sumit.trustforge.scenario.ScenarioService.Snapshot;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;

/** Single API instance, durable PostgreSQL queue, at most two concurrent model workflows. */
@Component
@EnableScheduling
public class ExecutionOrchestrator {
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"evaluation-worker");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Semaphore capacity=new Semaphore(2);
    private final Set<UUID> activeWorkers=ConcurrentHashMap.newKeySet();
    private final JdbcTemplate db;
    private final EvaluationStore store;
    private final ProjectAccess access;
    private final ScenarioExecutor executor;
    private final AuditService audit;
    private final MeterRegistry metrics;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher publisher;
    public ExecutionOrchestrator(JdbcTemplate db,EvaluationStore store,ProjectAccess access,AuditService audit,
        MeterRegistry metrics,PlatformTransactionManager manager,ApplicationEventPublisher publisher,ScenarioExecutor executor) {
        this.db=db;this.store=store;this.access=access;this.executor=executor;this.audit=audit;this.metrics=metrics;this.tx=new TransactionTemplate(manager);this.publisher=publisher;
        metrics.gauge("trustforge.worker.active",workers,ThreadPoolExecutor::getActiveCount);
        metrics.gauge("trustforge.worker.queue.depth",db,jdbc -> {
            try {return jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_runs WHERE state='QUEUED'",Long.class).doubleValue();}
            catch(RuntimeException unavailable){return Double.NaN;}
        });
    }
    @EventListener(ApplicationReadyEvent.class) public void recover() {
        // A single worker-owning API instance is supported. Interrupted inference is never marked complete.
        tx.executeWithoutResult(status -> {
            record InterruptedRun(UUID id,UUID project,UUID actor) {}
            List<InterruptedRun> interrupted=db.query("SELECT id,project_id,created_by FROM evaluation_runs WHERE state='RUNNING' FOR UPDATE",(rs,n)->
                new InterruptedRun(rs.getObject("id",UUID.class),rs.getObject("project_id",UUID.class),rs.getObject("created_by",UUID.class)));
            for(InterruptedRun run:interrupted) {
                db.update("UPDATE evaluation_runs SET state='FAILED',finished_at=now(),error='Worker restarted during inference; replay stored scenarios to retry' WHERE id=? AND state='RUNNING'",run.id());
                try {
                    org.slf4j.MDC.put("correlationId",run.id().toString());
                    audit.record(run.actor(),"EVALUATION_RECOVERED",run.id(),"FAILURE");
                } finally {org.slf4j.MDC.remove("correlationId");}
                publisher.publishEvent(new com.sumit.trustforge.common.ProjectChanged(run.project()));
                metrics.counter("trustforge.evaluation.recovered").increment();
            }
        });
    }
    /** Reconcile abandoned work after transient persistence failure; never retry a model side effect. */
    public synchronized void reconcileAbandoned() {
        tx.executeWithoutResult(status->{
            var abandoned=db.query("SELECT id FROM evaluation_runs WHERE state='RUNNING' ORDER BY started_at,id LIMIT 100 FOR UPDATE",(r,n)->r.getObject(1,UUID.class));
            for(UUID id:abandoned)if(!activeWorkers.contains(id)) {
                int changed=db.update("UPDATE evaluation_runs SET state='FAILED',finished_at=now(),error='Worker lost persistence during execution; replay stored scenarios to retry' WHERE id=? AND state='RUNNING'",id);
                if(changed>0){UUID project=db.queryForObject("SELECT project_id FROM evaluation_runs WHERE id=?",UUID.class,id);UUID actor=db.queryForObject("SELECT created_by FROM evaluation_runs WHERE id=?",UUID.class,id);audit.record(actor,"EVALUATION_RECOVERED",id,"FAILURE");publisher.publishEvent(new com.sumit.trustforge.common.ProjectChanged(project));metrics.counter("trustforge.evaluation.recovered").increment();}
            }
        });
    }
    @Scheduled(fixedDelay=1500,initialDelay=3000) public synchronized void dispatch() {
        try{reconcileAbandoned();}catch(RuntimeException unavailable){return;}
        while(capacity.tryAcquire()) {
            UUID id;
            try {
                id=tx.execute(status->{
                    List<UUID> next=db.query("SELECT id FROM evaluation_runs WHERE state='QUEUED' ORDER BY created_at,id FOR UPDATE SKIP LOCKED LIMIT 1",(rs,n)->rs.getObject(1,UUID.class));
                    if(next.isEmpty())return null;
                    UUID candidate=next.getFirst();
                    if(!RunState.QUEUED.canTransitionTo(RunState.RUNNING))throw new IllegalStateException();
                    db.update("UPDATE evaluation_runs SET state='RUNNING',started_at=now() WHERE id=? AND state='QUEUED'",candidate);
                    UUID project=db.queryForObject("SELECT project_id FROM evaluation_runs WHERE id=?",UUID.class,candidate);
                    publisher.publishEvent(new com.sumit.trustforge.common.ProjectChanged(project));
                    return candidate;
                });
            } catch(Exception ex) {capacity.release();return;}
            if(id==null){capacity.release();return;}
            activeWorkers.add(id);
            try {workers.execute(()->{try{org.slf4j.MDC.put("correlationId",id.toString());execute(id);}catch(RuntimeException failed){org.slf4j.LoggerFactory.getLogger(getClass()).warn("worker_persistence_interrupted evaluation={}",id);}finally{org.slf4j.MDC.remove("correlationId");activeWorkers.remove(id);capacity.release();}});}
            catch(RejectedExecutionException ex){activeWorkers.remove(id);capacity.release();try{db.update("UPDATE evaluation_runs SET state='QUEUED',started_at=NULL WHERE id=? AND state='RUNNING'",id);}catch(RuntimeException unavailable){/* Reconciled as FAILED when persistence returns. */}return;}
        }
    }
    private void execute(UUID id) {
        long started=System.nanoTime();
        Job job;
        try {job=store.load(id);}catch(Exception ex){return;}
        try {
            for(Snapshot scenario:job.scenarios()) {
                if(!active(id))return;
                access.require(job.run().projectId(),job.actorId(),true);
                ScenarioExecution test=executeScenario(job,scenario);
                tx.executeWithoutResult(status->{
                    // Serialize result persistence against cancellation/deletion.
                    List<String> states=db.query("SELECT state FROM evaluation_runs WHERE id=? FOR UPDATE",(rs,n)->rs.getString(1),id);
                    if(states.isEmpty() || !"RUNNING".equals(states.getFirst()))return;
                    store.saveResult(id,test.result(),test.events(),test.violations());
                    publisher.publishEvent(new com.sumit.trustforge.common.ProjectChanged(job.run().projectId()));
                });
                if(test.timedOut()){finish(job,RunState.TIMED_OUT,"Execution deadline reached",started);return;}
                if(test.providerFailed()){finish(job,RunState.FAILED,test.result().error(),started);return;}
            }
            finish(job,RunState.COMPLETED,null,started);
        } catch(Exception ex) {finish(job,RunState.FAILED,"Execution stopped: access revoked or persistence failure",started);}
    }
    private boolean active(UUID id) {
        List<String> states=db.query("SELECT state FROM evaluation_runs WHERE id=?",(rs,n)->rs.getString(1),id);
        return !Thread.currentThread().isInterrupted()&&!states.isEmpty()&&"RUNNING".equals(states.getFirst());
    }
    private void finish(Job job,RunState state,String error,long start) {
        if(!RunState.RUNNING.canTransitionTo(state))throw new IllegalStateException();
        tx.executeWithoutResult(status->{
            int changed=db.update("UPDATE evaluation_runs SET state=?,error=?,finished_at=now(),duration_ms=? WHERE id=? AND state='RUNNING'",state.name(),error,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),job.run().id());
            if(changed>0){audit.record(job.actorId(),"EVALUATION_"+state.name(),job.run().id(),state==RunState.COMPLETED?"SUCCESS":"FAILURE");publisher.publishEvent(new com.sumit.trustforge.common.ProjectChanged(job.run().projectId()));}
        });
        metrics.timer("trustforge.evaluation.duration").record(System.nanoTime()-start,TimeUnit.NANOSECONDS);
        if(state!=RunState.COMPLETED)metrics.counter("trustforge.evaluation.errors","state",state.name()).increment();
    }
    ScenarioExecution executeScenario(Job job,Snapshot scenario){return executor.execute(job,scenario,()->active(job.run().id()));}
    @PreDestroy public void shutdown(){workers.shutdownNow();}
}
