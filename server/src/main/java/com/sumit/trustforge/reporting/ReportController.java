package com.sumit.trustforge.reporting;

import com.sumit.trustforge.evaluation.EvaluationService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportService reports;
    private final EvaluationService evaluations;
    public ReportController(ReportService reports,EvaluationService evaluations){this.reports=reports;this.evaluations=evaluations;}
    @GetMapping("/reports/{id}") public ReportService.Report report(@PathVariable UUID id){return reports.report(id);}
    @GetMapping("/evaluations/compare") public ReportService.Comparison compare(@RequestParam UUID baselineId,@RequestParam UUID candidateId){return reports.compare(baselineId,candidateId);}
    public record ComparisonInput(@jakarta.validation.constraints.NotNull UUID baselineId,@jakarta.validation.constraints.NotNull UUID candidateId) {}
    @PostMapping("/evaluations/compare") public ReportService.Comparison saveComparison(@jakarta.validation.Valid @RequestBody ComparisonInput input){return reports.saveComparison(input.baselineId(),input.candidateId());}
    @GetMapping("/reports/{id}/recommendations") public List<ReportService.Recommendation> recommendations(@PathVariable UUID id){return reports.recommendations(id);}
    @PostMapping("/reports/{id}/recommendations") public ReportService.Recommendation recommend(@PathVariable UUID id){return reports.recommend(id);}
    @DeleteMapping("/reports/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id){evaluations.delete(id);}
    @GetMapping("/reports/{id}/export") public ResponseEntity<String> export(@PathVariable UUID id,@RequestParam(defaultValue="json")String format){
        if(!List.of("json","markdown").contains(format))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Export format must be json or markdown");
        String body="json".equals(format)?reports.exportJson(id):reports.exportMarkdown(id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"trustforge-"+id+("json".equals(format)?".json":".md")+"\"")
            .contentType("json".equals(format)?MediaType.APPLICATION_JSON:MediaType.parseMediaType("text/markdown; charset=UTF-8")).body(body);
    }
}
