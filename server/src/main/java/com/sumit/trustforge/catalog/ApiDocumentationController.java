package com.sumit.trustforge.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiDocumentationController {
    private final String specification;
    public ApiDocumentationController() throws IOException {
        try (var stream = new ClassPathResource("openapi.json").getInputStream()) {
            specification = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @GetMapping(value="/api/v1/openapi", produces=MediaType.APPLICATION_JSON_VALUE)
    public String openapi() { return specification; }
}
