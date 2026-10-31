package com.sumit.trustforge.evaluation;

import com.sumit.trustforge.access.ProjectAccess;
import com.sumit.trustforge.agent.AgentService;
import com.sumit.trustforge.ai.AIModelGateway;
import com.sumit.trustforge.audit.AuditService;
import com.sumit.trustforge.common.Actor;
import com.sumit.trustforge.evaluation.EvaluationStore.*;
import com.sumit.trustforge.execution.RunState;
import com.sumit.trustforge.scenario.ScenarioService;
import com.sumit.trustforge.scenario.ScenarioService.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import com.sumit.trustforge.common.ProjectChanged;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class EvaluationService {
    public record Create(@NotNull UUID agentVersionId,@NotEmpty @Size(max=20) List<@NotNull UUID> scenarioIds,@NotNull UUID idempotencyKey,@Min(1) @Max(5) Integer trialsPerScenario) {
        public Create{trialsPerScenario=trialsPerScenario==null?1:trialsPerScenario;}
        public Create(UUID version,List<UUID> scenarios,UUID key){this(version,scenarios,key,1);}
    }
    public record Replay(@NotNull UUID agentVersionId,@NotNull UUID idempotencyKey) {}
    private final JdbcTemplate db;
    private final ProjectAccess access;
    private final AgentService agents;
    private final ScenarioService scenarios;
    private final EvaluationStore store;
    private final AIModelGateway model;
    private final ObjectMapper json;
    private final AuditService audit;
    private final ApplicationEventPublisher publisher;
    private final ProvenanceService provenance;
    public EvaluationService(JdbcTemplate db,ProjectAccess access,AgentService agents,ScenarioService scenarios,EvaluationStore store,AIModelGateway model,ObjectMapper json,AuditService audit,ApplicationEventPublisher publisher,ProvenanceService provenance) {
        this.db=db;this.access=access;this.agents=agents;this.scenarios=scenarios;this.store=store;this.model=model;this.json=json;this.audit=audit;this.publisher=publisher;this.provenance=provenance;
    }
    public Page<Run> list(UUID project,int page,int size) {access.require(project,false);return store.list(project,page,size);}
    public Job authorized(UUID id,boolean write) {
        // Resolve membership before loading private snapshots or traces.
        List<UUID> projects=db.query("SELECT e.project_id FROM evaluation_runs e JOIN project_members m ON m.project_id=e.project_id WHERE e.id=? AND m.user_id=?",
            (rs,n)->rs.getObject(1,UUID.class),id,Actor.require());
        if(projects.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Evaluation not found");
        access.require(projects.getFirst(),write);
        return store.load(id);
    }
    public Run get(UUID id) {return authorized(id,false).run();}
    public Page<TestResult> results(UUID id,int page,int size) {authorized(id,false);return store.results(id,page,size);}
    public Page<Trace> trace(UUID id,UUID result,int page,int size) {authorized(id,false);return store.trace(id,result,page,size);}
    @Transactional public Run create(UUID project,Create request) {
        access.require(project,true);
        var definitions=scenarios.snapshots(project,request.scenarioIds()).stream().sorted(Comparator.comparing(Snapshot::id)).toList();
        if(definitions.size()*request.trialsPerScenario()>20)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At most20 total scenario trial executions per run");
        List<Snapshot> snapshot=new ArrayList<>();
        for(Snapshot s:definitions)for(int trial=1;trial<=request.trialsPerScenario();trial++)snapshot.add(new Snapshot(s.id(),s.title(),s.category(),s.description(),s.legitimateTask(),s.adversarialInput(),s.expectedOutputContains(),s.canary(),s.expectations(),s.fixture(),trial));
        return enqueue(project,request.agentVersionId(),request.idempotencyKey(),snapshot,null);
    }
    @Transactional public Run replay(UUID id,Replay request) {
        Job baseline=authorized(id,true);
        if(!baseline.run().state().terminal()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only terminal evaluations can be replayed");
        if(!provenance.executable(baseline.run().provenance()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Stored execution definitions are unknown or incompatible with this release; create a reviewed new baseline");
        return enqueue(baseline.run().projectId(),request.agentVersionId(),request.idempotencyKey(),baseline.scenarios(),id);
    }
    private Run enqueue(UUID project,UUID versionId,UUID key,List<Snapshot> snapshot,UUID baselineId) {
        UUID actor=Actor.require();
        db.queryForObject("SELECT pg_advisory_xact_lock(31415926)",Object.class);
        var agent=agents.version(versionId,project);
        db.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))",Object.class,actor.toString());
        List<UUID> existing=db.query("SELECT id FROM evaluation_runs WHERE created_by=? AND idempotency_key=?",(rs,n)->rs.getObject(1,UUID.class),actor,key);
        String raw=json.writeValueAsString(snapshot);
        var conditions=provenance.capture(agent,snapshot);
        if(!existing.isEmpty()) {
            Job old=store.load(existing.getFirst());
            if(!old.run().projectId().equals(project)||!old.run().agentVersionId().equals(versionId)||!old.rawScenarios().equals(raw)||!Objects.equals(old.run().baselineId(),baselineId)||old.run().provenance()==null||!old.run().provenance().deterministicFingerprint().equals(conditions.deterministicFingerprint())||!old.run().provenance().modelFingerprint().equals(conditions.modelFingerprint()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key was already used for different inputs");
            return old.run();
        }
        if(db.queryForObject("SELECT COUNT(*) FROM evaluation_runs WHERE state IN ('QUEUED','RUNNING')",Long.class)>=100)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Global evaluation queue is full");
        if(!model.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configure BharatCode on the server before starting an evaluation");
        if(db.queryForObject("SELECT COUNT(*) FROM evaluation_runs WHERE created_by=? AND state IN ('QUEUED','RUNNING')",Long.class,actor)>=3)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"At most three active evaluations per user");
        if(db.queryForObject("SELECT COUNT(*) FROM evaluation_runs WHERE project_id=?",Long.class,project)>=1000)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Project evaluation retention limit reached; delete old reports");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO evaluation_runs(id,project_id,agent_version_id,created_by,state,idempotency_key,scenario_snapshot,agent_snapshot,scenario_count,provenance_json,baseline_id) VALUES (?,?,?,?,'CREATED',?,?,?,?,?,?)",id,project,versionId,actor,key,raw,json.writeValueAsString(agent),snapshot.size(),json.writeValueAsString(conditions),baselineId);
        if(!RunState.CREATED.canTransitionTo(RunState.QUEUED))throw new IllegalStateException();
        db.update("UPDATE evaluation_runs SET state='QUEUED' WHERE id=? AND state='CREATED'",id);
        audit.record("EVALUATION_QUEUED",id,"SUCCESS");publisher.publishEvent(new ProjectChanged(project));
        return store.load(id).run();
    }
    @Transactional public Run cancel(UUID id) {
        Job job=authorized(id,true);
        if(job.run().state().terminal())return job.run();
        if(!job.run().state().canTransitionTo(RunState.CANCELLED))throw new ResponseStatusException(HttpStatus.CONFLICT,"Evaluation cannot be cancelled");
        db.update("UPDATE evaluation_runs SET state='CANCELLED',finished_at=now(),duration_ms=CASE WHEN started_at IS NULL THEN 0 ELSE (EXTRACT(EPOCH FROM now()-started_at)*1000)::bigint END WHERE id=? AND state IN ('QUEUED','RUNNING')",id);
        audit.record("EVALUATION_CANCELLED",id,"SUCCESS");publisher.publishEvent(new ProjectChanged(job.run().projectId()));
        return store.load(id).run();
    }
    @Transactional public void delete(UUID id) {
        Job job=authorized(id,false);access.requireOwner(job.run().projectId());
        if(!job.run().state().terminal())throw new ResponseStatusException(HttpStatus.CONFLICT,"Cancel the running evaluation before deletion");
        db.update("DELETE FROM evaluation_runs WHERE id=?",id);audit.record("REPORT_DELETED",id,"SUCCESS");publisher.publishEvent(new ProjectChanged(job.run().projectId()));
    }
}
