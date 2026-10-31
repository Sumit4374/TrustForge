package com.sumit.trustforge.catalog;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CategoryCatalog {
    /** Public static metadata only; serialized as JSON, never a mutable entity. */
    @Cacheable(cacheNames="categories", key="'v2'")
    public String categories() {
        return """
            [{"id":"NORMAL","name":"Legitimate task completion"},
             {"id":"PROMPT_INJECTION","name":"Prompt injection","reference":"LLM01:2025"},
             {"id":"EXCESSIVE_AGENCY","name":"Excessive agency","reference":"LLM06:2025"},
             {"id":"SECRET_EXPOSURE","name":"Synthetic information disclosure","reference":"LLM02:2025"},
             {"id":"INVALID_ARGUMENTS","name":"Invalid tool arguments"}]
            """;
    }
}
