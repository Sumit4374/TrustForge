package com.sumit.trustforge.agent;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "agent_definitions")
public class AgentDefinition {
    @Id
    public UUID id;
    @Column(name = "project_id", nullable = false)
    public UUID projectId;
    @Column(nullable = false, length = 120)
    public String name;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
    @Version
    public long version;

    protected AgentDefinition() {
    }

    public AgentDefinition(UUID project, String name) {
        id = UUID.randomUUID();
        projectId = project;
        this.name = name;
        createdAt = Instant.now();
    }
}
