package com.monitor.search.controller;

import com.monitor.search.model.SearchResult;
import com.monitor.search.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<SearchResult> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /search?q={}&page={}&size={}", q, page, size);
        return ResponseEntity.ok(searchService.search(q, page, size));
    }

    @GetMapping("/indexed")
    public ResponseEntity<SearchResult> indexedSearch(@RequestParam String q) {
        log.info("GET /search/indexed?q={}", q);
        return ResponseEntity.ok(searchService.indexedSearch(q));
    }

    @GetMapping("/full")
    public ResponseEntity<SearchResult> fullScanSearch(@RequestParam String q) {
        log.info("GET /search/full?q={}", q);
        return ResponseEntity.ok(searchService.fullScanSearch(q));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(searchService.getStats());
    }

    @PostMapping("/simulate/wildcard-flood")
    public ResponseEntity<Map<String, Object>> simulateWildcardFlood() {
        log.warn("POST /search/simulate/wildcard-flood");
        return ResponseEntity.ok(searchService.simulateWildcardFlood());
    }

    @PostMapping("/simulate/index-rebuild")
    public ResponseEntity<Map<String, Object>> simulateIndexRebuild() {
        log.warn("POST /search/simulate/index-rebuild");
        return ResponseEntity.ok(searchService.simulateIndexRebuild());
    }

    @PostMapping("/simulate/slow-query")
    public ResponseEntity<Map<String, Object>> simulateSlowQuery() throws InterruptedException {
        log.warn("POST /search/simulate/slow-query");
        return ResponseEntity.ok(searchService.simulateSlowQuery());
    }
}
