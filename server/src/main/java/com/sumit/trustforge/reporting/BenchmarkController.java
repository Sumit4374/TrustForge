package com.sumit.trustforge.reporting;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class BenchmarkController {
    private final BenchmarkService benchmarks;
    public BenchmarkController(BenchmarkService benchmarks){this.benchmarks=benchmarks;}
    @GetMapping("/reports/{id}/benchmarks") BenchmarkService.Summary report(@PathVariable UUID id){return benchmarks.report(id);}
    @GetMapping("/dashboard/benchmarks") BenchmarkService.Summary dashboard(){return benchmarks.dashboard();}
}
