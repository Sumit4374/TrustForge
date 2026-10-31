package com.sumit.trustforge.catalog;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {
    private final CategoryCatalog catalog;
    public CatalogController(CategoryCatalog catalog) { this.catalog = catalog; }
    @GetMapping(value="/api/v1/catalog/categories", produces=MediaType.APPLICATION_JSON_VALUE)
    public String categories() { return catalog.categories(); }
}
