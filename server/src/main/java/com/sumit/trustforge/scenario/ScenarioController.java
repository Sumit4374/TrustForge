package com.sumit.trustforge.scenario;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/scenarios")
public class ScenarioController {
    private final ScenarioService service;
    public ScenarioController(ScenarioService service) {this.service=service;}
    @GetMapping public ScenarioService.Page<ScenarioService.Scenario> list(@PathVariable UUID projectId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="30") int size) {return service.list(projectId,page,size);}
    @PostMapping public ScenarioService.Scenario create(@PathVariable UUID projectId,@RequestBody @Valid ScenarioService.Create request) {return service.create(projectId,request);}
    @PostMapping("/{scenarioId}/approve") public ScenarioService.Scenario approve(@PathVariable UUID projectId,@PathVariable UUID scenarioId) {return service.approve(projectId,scenarioId);}
    @PatchMapping("/{scenarioId}/expectations") public ScenarioService.Scenario review(@PathVariable UUID projectId,@PathVariable UUID scenarioId,@RequestBody @Valid ExpectedOutcome request){return service.reviewExpectations(projectId,scenarioId,request);}
    @PostMapping("/generate") public List<ScenarioService.Scenario> generate(@PathVariable UUID projectId,@RequestBody @Valid ScenarioService.Generate request) {return service.generate(projectId,request);}
}
