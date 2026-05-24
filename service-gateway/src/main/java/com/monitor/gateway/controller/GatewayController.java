package com.monitor.gateway.controller;

import com.monitor.gateway.service.GatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API Gateway REST Controller — routes requests to downstream services.
 */
@RestController
@RequestMapping("/gateway")
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);
    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @GetMapping("/feed/{userId}")
    public ResponseEntity<Map<String, Object>> getFeed(@PathVariable String userId) {
        log.info("GET /gateway/feed/{}", userId);
        return ResponseEntity.ok(gatewayService.getFeed(userId));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q) {
        log.info("GET /gateway/search?q={}", q);
        return ResponseEntity.ok(gatewayService.search(q));
    }

    @PostMapping("/media/upload")
    public ResponseEntity<Map<String, Object>> uploadMedia() {
        log.info("POST /gateway/media/upload");
        return ResponseEntity.ok(Map.of(
                "route", "media",
                "message", "Media upload routed to Media Processing Service (port 8083)",
                "uploadEndpoint", "POST http://localhost:8083/media/upload"
        ));
    }

    @GetMapping("/health/all")
    public ResponseEntity<Map<String, Object>> getAllHealth() {
        log.info("GET /gateway/health/all");
        return ResponseEntity.ok(gatewayService.getAllHealth());
    }

    @PostMapping("/simulate/traffic-flood")
    public ResponseEntity<Map<String, Object>> simulateTrafficFlood() {
        log.warn("POST /gateway/simulate/traffic-flood");
        return ResponseEntity.ok(gatewayService.simulateTrafficFlood());
    }

    @PostMapping("/simulate/slow-upstream")
    public ResponseEntity<Map<String, Object>> simulateSlowUpstream() throws InterruptedException {
        log.warn("POST /gateway/simulate/slow-upstream");
        return ResponseEntity.ok(gatewayService.simulateSlowUpstream());
    }

    @PostMapping("/simulate/rate-limit-breach")
    public ResponseEntity<Map<String, Object>> simulateRateLimitBreach() {
        log.warn("POST /gateway/simulate/rate-limit-breach");
        return ResponseEntity.ok(gatewayService.simulateRateLimitBreach());
    }
}
