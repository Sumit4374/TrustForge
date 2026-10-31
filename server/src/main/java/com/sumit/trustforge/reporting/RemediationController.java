package com.sumit.trustforge.reporting;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports/{evaluation}/proposals")
public class RemediationController {
    private final RemediationService service;
    public RemediationController(RemediationService service){this.service=service;}
    public record Generate(@NotNull UUID resultId) {}
    public record Review(@NotNull Boolean approve,Boolean acknowledged) {}
    @GetMapping public List<RemediationService.Proposal> list(@PathVariable UUID evaluation){return service.list(evaluation);}
    @PostMapping public RemediationService.Proposal generate(@PathVariable UUID evaluation,@Valid @RequestBody Generate body){return service.generate(evaluation,body.resultId());}
    @PostMapping("/{id}/review") public RemediationService.Proposal review(@PathVariable UUID evaluation,@PathVariable UUID id,@Valid @RequestBody Review body){return service.review(evaluation,id,body.approve(),Boolean.TRUE.equals(body.acknowledged()));}
}
