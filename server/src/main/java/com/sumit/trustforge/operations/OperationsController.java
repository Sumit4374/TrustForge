package com.sumit.trustforge.operations;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class OperationsController {
    private final OperationsService operations;
    public OperationsController(OperationsService operations) { this.operations=operations; }
    @GetMapping("/api/v1/admin/health")
    public Map<String,Object> health() { return operations.health(); }
}
