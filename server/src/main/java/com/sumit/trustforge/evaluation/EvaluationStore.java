package com.sumit.trustforge.evaluation;

import com.sumit.trustforge.agent.AgentService.AgentVersionDto;
import com.sumit.trustforge.execution.AssertionEvaluator.Assertion;
import com.sumit.trustforge.execution.RunState;
import com.sumit.trustforge.execution.ExecutionEvidence;
import com.sumit.trustforge.scenario.ScenarioService.Snapshot;
import com.sumit.trustforge.scenario.ScenarioService.Page;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class EvaluationStore {
    public record Run(UUID id,UUID projectId,UUID agentVersionId,RunState state,int scenarioCount,int completedCount,
        int passedCount,int failedCount,int inconclusiveCount,int preventedCalls,int legitimateTasks,long durationMs,
        String error,Instant createdAt,Instant startedAt,Instant finishedAt,ProvenanceService.Provenance provenance,UUID baselineId) {
        public Run(UUID id,UUID projectId,UUID agentVersionId,RunState state,int scenarioCount,int completedCount,int passedCount,int failedCount,int inconclusiveCount,int preventedCalls,int legitimateTasks,long durationMs,String error,Instant createdAt,Instant startedAt,Instant finishedAt){this(id,projectId,agentVersionId,state,scenarioCount,completedCount,passedCount,failedCount,inconclusiveCount,preventedCalls,legitimateTasks,durationMs,error,createdAt,startedAt,finishedAt,null,null);}
    }
    public record Job(Run run,UUID actorId,List<Snapshot> scenarios,AgentVersionDto agent,String rawScenarios) {}
    public record TestResult(UUID id,UUID scenarioId,String title,String category,String outcome,String output,
        List<Assertion> assertions,int preventedCalls,int unauthorizedAttempts,boolean secretExposed,boolean legitimateTaskCompleted,long durationMs,String error,ExecutionEvidence evidence,int trialIndex) {
        public TestResult(UUID id,UUID scenarioId,String title,String category,String outcome,String output,List<Assertion> assertions,int preventedCalls,int unauthorizedAttempts,boolean secretExposed,boolean taskCompleted,long durationMs,String error){this(id,scenarioId,title,category,outcome,output,assertions,preventedCalls,unauthorizedAttempts,secretExposed,taskCompleted,durationMs,error,null,1);}
        public TestResult(UUID id,UUID scenarioId,String title,String category,String outcome,String output,List<Assertion> assertions,int preventedCalls,int unauthorizedAttempts,boolean secretExposed,boolean taskCompleted,long durationMs,String error,ExecutionEvidence evidence){this(id,scenarioId,title,category,outcome,output,assertions,preventedCalls,unauthorizedAttempts,secretExposed,taskCompleted,durationMs,error,evidence,1);}
    }
        
    public record Trace(UUID id,UUID resultId,int step,String event,String detail,Instant createdAt) {}
    public record Event(int step,String event,String detail) {}
    public record Violation(String tool,String reason) {}
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public EvaluationStore(JdbcTemplate db,ObjectMapper json) {this.db=db;this.json=json;}
    public Job load(UUID id) {
        List<Job> rows=db.query("SELECT * FROM evaluation_runs WHERE id=?",(rs,n)->new Job(mapRun(rs,n),rs.getObject("created_by",UUID.class),
            List.of(json.readValue(rs.getString("scenario_snapshot"),Snapshot[].class)),
            json.readValue(rs.getString("agent_snapshot"),AgentVersionDto.class),rs.getString("scenario_snapshot")),id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Evaluation not found");
        return rows.getFirst();
    }
    public Page<Run> list(UUID project,int page,int size) {
        page=Math.clamp(page,0,100000);size=Math.clamp(size,1,100);
        return new Page<>(db.query("SELECT * FROM evaluation_runs WHERE project_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",this::mapRun,project,size,(long)page*size),
            db.queryForObject("SELECT COUNT(*) FROM evaluation_runs WHERE project_id=?",Long.class,project),page,size);
    }
    public Page<TestResult> results(UUID id,int page,int size) {
        page=Math.clamp(page,0,100000);size=Math.clamp(size,1,100);
        return new Page<>(db.query("SELECT * FROM evaluation_test_results WHERE evaluation_id=? ORDER BY created_at,id LIMIT ? OFFSET ?",this::mapResult,id,size,(long)page*size),
            db.queryForObject("SELECT COUNT(*) FROM evaluation_test_results WHERE evaluation_id=?",Long.class,id),page,size);
    }
    public Page<Trace> trace(UUID id,UUID resultId,int page,int size) {
        page=Math.clamp(page,0,100000);size=Math.clamp(size,1,100);
        String where="evaluation_id=?"+(resultId==null?"":" AND result_id=?");
        List<Object> params=new ArrayList<>();params.add(id);if(resultId!=null)params.add(resultId);
        long count=Objects.requireNonNull(db.queryForObject("SELECT COUNT(*) FROM execution_steps WHERE "+where,Long.class,params.toArray()));
        params.add(size);params.add((long)page*size);
        return new Page<>(db.query("SELECT * FROM execution_steps WHERE "+where+" ORDER BY created_at,result_id,sequence_no,id LIMIT ? OFFSET ?",(rs,n)->new Trace(
            rs.getObject("id",UUID.class),rs.getObject("result_id",UUID.class),rs.getInt("step"),rs.getString("event"),rs.getString("detail"),rs.getTimestamp("created_at").toInstant()),params.toArray()),count,page,size);
    }
    public void saveResult(UUID runId,TestResult result,List<Event> events,List<Violation> violations) {
        db.update("""
            INSERT INTO evaluation_test_results(id,evaluation_id,scenario_id,title,category,outcome,output,assertions,prevented_calls,unauthorized_attempts,secret_exposed,legitimate_task_completed,duration_ms,error,evidence_json,trial_index)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,result.id(),runId,result.scenarioId(),result.title(),result.category(),result.outcome(),result.output(),json.writeValueAsString(result.assertions()),
            result.preventedCalls(),result.unauthorizedAttempts(),result.secretExposed(),result.legitimateTaskCompleted(),result.durationMs(),result.error(),result.evidence()==null?null:json.writeValueAsString(result.evidence()),result.trialIndex());
        for(int position=0;position<events.size();position++) {
            Event event=events.get(position);
            db.update("INSERT INTO execution_steps(id,evaluation_id,result_id,step,event,detail,sequence_no) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),runId,result.id(),event.step(),event.event(),event.detail(),position);
        }
        for(Violation v:violations)db.update("INSERT INTO policy_violations(id,evaluation_id,result_id,tool,reason,prevented) VALUES (?,?,?,?,?,true)",UUID.randomUUID(),runId,result.id(),v.tool(),v.reason());
        db.update("""
            UPDATE evaluation_runs SET completed_count=completed_count+1,passed_count=passed_count+?,failed_count=failed_count+?,
            inconclusive_count=inconclusive_count+?,prevented_calls=prevented_calls+?,legitimate_tasks=legitimate_tasks+? WHERE id=?
            ""","PASSED".equals(result.outcome())?1:0,"FAILED".equals(result.outcome())?1:0,"INCONCLUSIVE".equals(result.outcome())?1:0,
            result.preventedCalls(),result.legitimateTaskCompleted()?1:0,runId);
    }
    public record BenchmarkDataset(List<TestResult> results,int plannedExecutions,List<String> providers) {}
    public BenchmarkDataset recentBenchmarks(UUID actor) {
        List<TestResult> results=new ArrayList<>();Map<UUID,Integer> planned=new HashMap<>();Set<String> providers=new TreeSet<>();
        db.query("""
            WITH recent AS (
              SELECT e.id,e.scenario_count,e.provenance_json FROM evaluation_runs e
              JOIN project_members m ON m.project_id=e.project_id
              WHERE m.user_id=? AND e.state IN ('COMPLETED','FAILED','TIMED_OUT','CANCELLED')
              ORDER BY e.finished_at DESC NULLS LAST,e.id LIMIT 20
            )
            SELECT recent.id AS run_id,recent.scenario_count AS planned,recent.provenance_json AS conditions,t.*
            FROM recent LEFT JOIN evaluation_test_results t ON t.evaluation_id=recent.id ORDER BY recent.id,t.created_at,t.id
            """,rs->{
                planned.put(rs.getObject("run_id",UUID.class),rs.getInt("planned"));
                String raw=rs.getString("conditions");var provenance=raw==null?null:json.readValue(raw,ProvenanceService.Provenance.class);
                providers.add(provenance==null?"LEGACY_UNKNOWN":provenance.provider().provider());
                if(rs.getObject("id")!=null)results.add(mapResult(rs,0));
            },actor);
        return new BenchmarkDataset(List.copyOf(results),planned.values().stream().mapToInt(Integer::intValue).sum(),List.copyOf(providers));
    }
    private TestResult mapResult(ResultSet rs,int n)throws SQLException {
        return new TestResult(rs.getObject("id",UUID.class),rs.getObject("scenario_id",UUID.class),rs.getString("title"),rs.getString("category"),rs.getString("outcome"),rs.getString("output"),
            List.of(json.readValue(rs.getString("assertions"),Assertion[].class)),rs.getInt("prevented_calls"),rs.getInt("unauthorized_attempts"),rs.getBoolean("secret_exposed"),rs.getBoolean("legitimate_task_completed"),rs.getLong("duration_ms"),rs.getString("error"),rs.getString("evidence_json")==null?null:json.readValue(rs.getString("evidence_json"),ExecutionEvidence.class),rs.getInt("trial_index"));
    }
    private Run mapRun(ResultSet rs,int n)throws SQLException {
        return new Run(rs.getObject("id",UUID.class),rs.getObject("project_id",UUID.class),rs.getObject("agent_version_id",UUID.class),RunState.valueOf(rs.getString("state")),
            rs.getInt("scenario_count"),rs.getInt("completed_count"),rs.getInt("passed_count"),rs.getInt("failed_count"),rs.getInt("inconclusive_count"),rs.getInt("prevented_calls"),rs.getInt("legitimate_tasks"),
            rs.getLong("duration_ms"),rs.getString("error"),instant(rs,"created_at"),instant(rs,"started_at"),instant(rs,"finished_at"),rs.getString("provenance_json")==null?null:json.readValue(rs.getString("provenance_json"),ProvenanceService.Provenance.class),rs.getObject("baseline_id",UUID.class));
    }
    private static Instant instant(ResultSet rs,String field)throws SQLException{return rs.getTimestamp(field)==null?null:rs.getTimestamp(field).toInstant();}
}
