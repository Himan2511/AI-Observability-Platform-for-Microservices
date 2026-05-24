package com.monitor.recommendation.controller;

import com.monitor.recommendation.model.RecommendationFeed;
import com.monitor.recommendation.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/recommend")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<RecommendationFeed> getFeed(@PathVariable String userId) {
        log.info("GET /recommend/{}", userId);
        return ResponseEntity.ok(recommendationService.getFeed(userId));
    }

    @GetMapping("/{userId}/refresh")
    public ResponseEntity<RecommendationFeed> refreshFeed(@PathVariable String userId) {
        log.info("GET /recommend/{}/refresh", userId);
        return ResponseEntity.ok(recommendationService.refreshFeed(userId));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(recommendationService.getStats());
    }

    @PostMapping("/simulate/cache-expiry")
    public ResponseEntity<Map<String, Object>> simulateCacheExpiry() {
        log.warn("POST /recommend/simulate/cache-expiry");
        return ResponseEntity.ok(recommendationService.simulateCacheExpiry());
    }

    @PostMapping("/simulate/algorithm-overload")
    public ResponseEntity<Map<String, Object>> simulateAlgorithmOverload() throws InterruptedException {
        log.warn("POST /recommend/simulate/algorithm-overload");
        return ResponseEntity.ok(recommendationService.simulateAlgorithmOverload());
    }

    @PostMapping("/simulate/large-dataset")
    public ResponseEntity<Map<String, Object>> simulateLargeDataset() {
        log.warn("POST /recommend/simulate/large-dataset");
        return ResponseEntity.ok(recommendationService.simulateLargeDataset());
    }
}
