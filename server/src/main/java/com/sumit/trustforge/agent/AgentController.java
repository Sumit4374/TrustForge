package com.sumit.trustforge.agent;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class AgentController {
    public record AgentInput(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 16000) String instructions,
            @NotNull @Size(max = 8) List<@NotBlank @Size(max = 64) String> allowedTools,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 180) String> allowedRecipients,
            boolean allowSend, @Min(1) @Max(12) int maxSteps, @Min(5) @Max(180) int timeoutSeconds,
            @NotBlank @Size(max = 100) String model) {
    }

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/tools")
    List<AgentService.ToolDto> tools() {
        return service.tools();
    }

    @GetMapping("/api/v1/projects/{project}/agents")
    List<AgentService.AgentDto> list(@PathVariable UUID project) {
        return service.list(project);
    }

    @PostMapping("/api/v1/projects/{project}/agents")
    @ResponseStatus(HttpStatus.CREATED)
    AgentService.AgentDto create(@PathVariable UUID project, @Valid @RequestBody AgentInput body) {
        return service.create(project, body);
    }

    @GetMapping("/api/v1/projects/{project}/agents/{agent}")
    AgentService.AgentDto get(@PathVariable UUID project, @PathVariable UUID agent) {
        return service.get(project, agent);
    }

    @PostMapping("/api/v1/projects/{project}/agents/{agent}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    AgentService.AgentVersionDto version(@PathVariable UUID project, @PathVariable UUID agent,
            @Valid @RequestBody AgentInput body) {
        return service.addVersion(project, agent, body);
    }

    @GetMapping("/api/v1/agent-versions/{id}")
    AgentService.AgentVersionDto version(@PathVariable UUID id) {
        return service.getVersion(id);
    }
}
