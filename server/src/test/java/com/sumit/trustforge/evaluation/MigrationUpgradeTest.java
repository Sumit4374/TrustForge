package com.sumit.trustforge.evaluation;

import java.util.*;
import com.sumit.trustforge.agent.AgentService.AgentVersionDto;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** Real upgrade from V3 with historical rows; no expected label/provenance or new grade is invented. */
@Testcontainers
class MigrationUpgradeTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @Test void additiveMigrationPreservesHistoricalSnapshotsAndGrades() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).target("3").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        var json=JsonMapper.builder().build();UUID user=UUID.randomUUID(),project=UUID.randomUUID(),agent=UUID.randomUUID(),version=UUID.randomUUID(),scenario=UUID.randomUUID(),run=UUID.randomUUID(),result=UUID.randomUUID();
        db.update("INSERT INTO users(id,email,name,password_hash,verified,role) VALUES (?,?,?,'synthetic-not-used',true,'USER')",user,"migration@example.test","Migration fixture");
        db.update("INSERT INTO projects(id,name,created_by) VALUES (?,'Legacy fixture',?)",project,user);
        db.update("INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,'OWNER')",project,user);
        db.update("INSERT INTO agent_definitions(id,project_id,name) VALUES (?,?,'Legacy agent')",agent,project);
        var config=new AgentVersionDto(version,agent,project,1,"Legacy agent","Synthetic",List.of("email.classify"),List.of(),false,3,30,"qwen-3.8-27b");
        db.update("INSERT INTO agent_versions(id,agent_id,project_id,version,name,instructions,allowed_tools,allowed_recipients,allow_send,max_steps,timeout_seconds,model) VALUES (?,?,?,1,'Legacy agent','Synthetic','[\"email.classify\"]','[]',false,3,30,'qwen-3.8-27b')",version,agent,project);
        db.update("INSERT INTO test_scenarios(id,project_id,title,category,description,legitimate_task,adversarial_input,expected_output_contains,canary,approved,source,created_by) VALUES (?,?,'Legacy scenario','NORMAL','Legacy','Classify','Synthetic','CLASSIFIED','TF_CANARY_MIGRATION_1234',true,'MANUAL',?)",scenario,project,user);
        var oldSnapshot=Map.of("id",scenario,"title","Legacy scenario","category","NORMAL","description","Legacy","legitimateTask","Classify","adversarialInput","Synthetic","expectedOutputContains","CLASSIFIED","canary","TF_CANARY_MIGRATION_1234");
        String raw=json.writeValueAsString(List.of(oldSnapshot));
        db.update("INSERT INTO evaluation_runs(id,project_id,agent_version_id,created_by,state,idempotency_key,scenario_snapshot,agent_snapshot,scenario_count,completed_count,passed_count) VALUES (?,?,?,?,'COMPLETED',?,?,?,1,1,1)",run,project,version,user,UUID.randomUUID(),raw,json.writeValueAsString(config));
        db.update("INSERT INTO evaluation_test_results(id,evaluation_id,scenario_id,title,category,outcome,output,assertions,prevented_calls,unauthorized_attempts,secret_exposed,legitimate_task_completed,duration_ms) VALUES (?,?,?,'Legacy scenario','NORMAL','PASSED','CLASSIFIED','[]',0,0,false,true,1)",result,run,scenario);
        Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).load().migrate();
        var store=new EvaluationStore(db,json);var loaded=store.load(run);
        assertThat(loaded.rawScenarios()).isEqualTo(raw);assertThat(loaded.scenarios().getFirst().expectations()).isNull();assertThat(loaded.scenarios().getFirst().fixture()).isNull();assertThat(loaded.run().provenance()).isNull();
        var historical=store.results(run,0,20).items().getFirst();assertThat(historical.outcome()).isEqualTo("PASSED");assertThat(historical.evidence()).isNull();
        assertThat(db.queryForObject("SELECT expectations_json FROM test_scenarios WHERE id=?",String.class,scenario)).isNull();
    }
}
