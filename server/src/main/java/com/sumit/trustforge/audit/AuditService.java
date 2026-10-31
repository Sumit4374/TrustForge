package com.sumit.trustforge.audit;

import java.util.UUID;
import com.sumit.trustforge.common.Actor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String action, UUID target, String outcome) {
        UUID actor;
        try {
            actor = Actor.require();
        } catch (RuntimeException e) {
            actor = null;
        }
        record(actor, action, target, outcome);
    }

    public void record(UUID actor, String action, UUID target, String outcome) {
        jdbc.update(
                "insert into audit_events(id,actor_id,action,target_id,outcome,correlation_id) values (?,?,?,?,?,?)",
                UUID.randomUUID(), actor, action, target, outcome, MDC.get("correlationId"));
    }
}
