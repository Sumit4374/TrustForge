package com.sumit.trustforge.agent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.sumit.trustforge.access.ProjectAccess;
import com.sumit.trustforge.audit.AuditService;
import com.sumit.trustforge.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import com.sumit.trustforge.common.ProjectChanged;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AgentService {
    public record AgentVersionDto(UUID id, UUID agentId, UUID projectId, int version, String name, String instructions,
            List<String> allowedTools, List<String> allowedRecipients, boolean allowSend, int maxSteps,
            int timeoutSeconds, String model) {
    }

    public record AgentDto(UUID id, UUID projectId, String name, List<AgentVersionDto> versions) {
    }

    public record ToolDto(String name, String description, List<String> arguments) {
    }

    private final AgentRepository agents;
    private final ProjectAccess access;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;

    public AgentService(AgentRepository agents, ProjectAccess access, JdbcTemplate jdbc, AuditService audit,
            ObjectMapper mapper, ApplicationEventPublisher events) {
        this.agents = agents;
        this.access = access;
        this.jdbc = jdbc;
        this.audit = audit;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<AgentDto> list(UUID project) {
        access.require(project, false);
        var latest = jdbc.query(
                "select distinct on (agent_id) * from agent_versions where project_id=? order by agent_id,version desc",
                (r, n) -> map(r), project)
                .stream().collect(java.util.stream.Collectors.toMap(AgentVersionDto::agentId,
                        java.util.function.Function.identity()));
        return jdbc.query(
                "select id,project_id,name from agent_definitions where project_id=? order by created_at desc,id limit 100",
                (r, n) -> {
                    UUID id = r.getObject(1, UUID.class);
                    var version = latest.get(id);
                    return new AgentDto(id, r.getObject(2, UUID.class), r.getString(3),
                            version == null ? List.of() : List.of(version));
                }, project);
    }

    @Transactional(readOnly = true)
    public AgentDto get(UUID project, UUID id) {
        access.require(project, false);
        AgentDefinition a = agents.findById(id).filter(x -> x.projectId.equals(project))
                .orElseThrow(ApiException::notFound);
        return new AgentDto(a.id, project, a.name, versions(id));
    }

    @Transactional
    public AgentDto create(UUID project, AgentController.AgentInput body) {
        access.require(project, true);
        validate(body);
        Long count = jdbc.queryForObject("select count(*) from agent_definitions where project_id=?", Long.class,
                project);
        if (count != null && count >= 100)
            throw ApiException.invalid("Agent limit reached (100)");
        AgentDefinition a = agents.saveAndFlush(new AgentDefinition(project, body.name().strip()));
        insertVersion(a, body);
        events.publishEvent(new ProjectChanged(project));
        audit.record("AGENT_CREATE", a.id, "SUCCESS");
        return get(project, a.id);
    }

    @Transactional
    public AgentVersionDto addVersion(UUID project, UUID id, AgentController.AgentInput body) {
        access.require(project, true);
        validate(body);
        // Lock parent serializes version allocation without modifying any existing
        // version.
        var parent = jdbc.query("select id from agent_definitions where id=? and project_id=? for update",
                (r, n) -> r.getObject(1, UUID.class), id, project);
        if (parent.isEmpty())
            throw ApiException.notFound();
        AgentDefinition a = agents.findById(id).orElseThrow(ApiException::notFound);
        a.name = body.name().strip();
        agents.saveAndFlush(a);
        AgentVersionDto v = insertVersion(a, body);
        events.publishEvent(new ProjectChanged(project));
        audit.record("AGENT_VERSION_CREATE", v.id, "SUCCESS");
        return v;
    }

    @Transactional
    public AgentVersionDto addVersionIfLatest(UUID project,UUID agent,UUID expected,AgentController.AgentInput body) {
        access.require(project,true);
        var parent=jdbc.query("select id from agent_definitions where id=? and project_id=? for update",(r,n)->r.getObject(1,UUID.class),agent,project);
        if(parent.isEmpty())throw ApiException.notFound();
        UUID latest=jdbc.queryForObject("select id from agent_versions where agent_id=? order by version desc limit 1",UUID.class,agent);
        if(!expected.equals(latest))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Agent changed since the baseline. Review a proposal based on its latest version.");
        return addVersion(project,agent,body);
    }

    @Transactional(readOnly = true)
    public AgentVersionDto getVersion(UUID id) {
        AgentVersionDto v = snapshot(id);
        access.require(v.projectId, false);
        return v;
    }

    @Transactional(readOnly = true)
    public AgentVersionDto version(UUID id, UUID project) {
        access.require(project, false);
        AgentVersionDto v = snapshot(id);
        if (!v.projectId.equals(project))
            throw ApiException.notFound();
        return v;
    }

    /**
     * Worker-only lookup: the caller must reauthorize its persisted actor and
     * project first.
     */
    @Transactional(readOnly = true)
    public AgentVersionDto snapshot(UUID id) {
        var list = jdbc.query("select * from agent_versions where id=?", (r, n) -> map(r), id);
        if (list.isEmpty())
            throw ApiException.notFound();
        return list.getFirst();
    }

    public List<ToolDto> tools() {
        return jdbc.query("select name,description,arguments from tool_definitions order by name",
                (r, n) -> new ToolDto(r.getString(1), r.getString(2), strings(r.getString(3))));
    }

    private List<AgentVersionDto> versions(UUID agent) {
        return jdbc.query("select * from agent_versions where agent_id=? order by version desc limit 100",
                (r, n) -> map(r), agent);
    }

    private AgentVersionDto map(java.sql.ResultSet r) throws java.sql.SQLException {
        return new AgentVersionDto(r.getObject("id", UUID.class), r.getObject("agent_id", UUID.class),
                r.getObject("project_id", UUID.class), r.getInt("version"), r.getString("name"),
                r.getString("instructions"), strings(r.getString("allowed_tools")),
                strings(r.getString("allowed_recipients")), r.getBoolean("allow_send"), r.getInt("max_steps"),
                r.getInt("timeout_seconds"), r.getString("model"));
    }

    private AgentVersionDto insertVersion(AgentDefinition a, AgentController.AgentInput b) {
        Integer last = jdbc.queryForObject("select coalesce(max(version),0) from agent_versions where agent_id=?",
                Integer.class, a.id);
        int next = (last == null ? 0 : last) + 1;
        if (next > 100)
            throw ApiException.invalid("Agent version limit reached (100)");
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into agent_versions(id,agent_id,project_id,version,name,instructions,allowed_tools,allowed_recipients,allow_send,max_steps,timeout_seconds,model) values (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, a.id, a.projectId, next, b.name().strip(), b.instructions(), json(b.allowedTools()),
                json(b.allowedRecipients()), b.allowSend(), b.maxSteps(), b.timeoutSeconds(), b.model());
        return snapshot(id);
    }

    private void validate(AgentController.AgentInput b) {
        Set<String> known = tools().stream().map(ToolDto::name).collect(java.util.stream.Collectors.toSet());
        if (!known.containsAll(b.allowedTools()))
            throw ApiException.invalid("Unknown simulated tool");
        if (b.allowedTools().size() != Set.copyOf(b.allowedTools()).size())
            throw ApiException.invalid("Duplicate tools");
        for (String to : b.allowedRecipients())
            if (to == null || !to.matches("[A-Za-z0-9._+-]{1,60}@[A-Za-z0-9.-]{1,100}\\.test"))
                throw ApiException.invalid("Use only synthetic recipients ending in .test");
        if (!b.model().matches("[a-zA-Z0-9._-]{1,100}"))
            throw ApiException.invalid("Invalid model identifier");
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> strings(String s) {
        try {
            return mapper.readValue(s, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Invalid persisted JSON", e);
        }
    }
}
