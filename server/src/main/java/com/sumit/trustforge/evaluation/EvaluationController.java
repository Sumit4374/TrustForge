package com.sumit.trustforge.evaluation;
import com.sumit.trustforge.evaluation.EvaluationStore.*;
import com.sumit.trustforge.scenario.ScenarioService.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class EvaluationController {
    private final EvaluationService service;
    public EvaluationController(EvaluationService service){this.service=service;}
    @GetMapping("/projects/{project}/evaluations") public Page<Run> list(@PathVariable UUID project,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="30")int size){return service.list(project,page,size);}
    @PostMapping("/projects/{project}/evaluations") public Run create(@PathVariable UUID project,@Valid @RequestBody EvaluationService.Create request){return service.create(project,request);}
    @GetMapping("/evaluations/{id}") public Run get(@PathVariable UUID id){return service.get(id);}
    @GetMapping("/evaluations/{id}/results") public Page<TestResult> results(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="30")int size){return service.results(id,page,size);}
    @GetMapping("/evaluations/{id}/trace") public Page<Trace> trace(@PathVariable UUID id,@RequestParam(required=false) UUID resultId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return service.trace(id,resultId,page,size);}
    @PostMapping("/evaluations/{id}/cancel") public Run cancel(@PathVariable UUID id){return service.cancel(id);}
    @PostMapping("/evaluations/{id}/replay") public Run replay(@PathVariable UUID id,@Valid @RequestBody EvaluationService.Replay request){return service.replay(id,request);}
}
