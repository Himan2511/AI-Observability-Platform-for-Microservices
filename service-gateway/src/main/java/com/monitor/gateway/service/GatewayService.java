package com.monitor.gateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core gateway service handling routing to all 3 downstream services
 * and the simulation endpoints for anomaly generation.
 */
@Service
public class GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayService.class);

    private final RestTemplate restTemplate;
    private final KafkaProducerService kafkaProducerService;
    private final MeterRegistry meterRegistry;
    private final Counter feedRequestCounter;
    private final Counter searchRequestCounter;
    private final Counter mediaRequestCounter;
    private final Counter searchFailureCounter;
    private final Counter mediaFailureCounter;
    private final Counter recommendationFailureCounter;

    @Value("${services.recommendation.base-url}")
    private String recommendationBaseUrl;

    @Value("${services.search.base-url}")
    private String searchBaseUrl;

    @Value("${services.media.base-url}")
    private String mediaBaseUrl;

    public GatewayService(
            RestTemplate restTemplate,
            KafkaProducerService kafkaProducerService,
            MeterRegistry meterRegistry,
            @Qualifier("gatewayRequestsTotalFeed")              Counter feedRequestCounter,
            @Qualifier("gatewayRequestsTotalSearch")            Counter searchRequestCounter,
            @Qualifier("gatewayRequestsTotalMedia")             Counter mediaRequestCounter,
            @Qualifier("gatewayUpstreamFailuresSearch")         Counter searchFailureCounter,
            @Qualifier("gatewayUpstreamFailuresMedia")          Counter mediaFailureCounter,
            @Qualifier("gatewayUpstreamFailuresRecommendation") Counter recommendationFailureCounter
    ) {
        this.restTemplate = restTemplate;
        this.kafkaProducerService = kafkaProducerService;
        this.meterRegistry = meterRegistry;
        this.feedRequestCounter = feedRequestCounter;
        this.searchRequestCounter = searchRequestCounter;
        this.mediaRequestCounter = mediaRequestCounter;
        this.searchFailureCounter = searchFailureCounter;
        this.mediaFailureCounter = mediaFailureCounter;
        this.recommendationFailureCounter = recommendationFailureCounter;
    }

    public Map<String, Object> getFeed(String userId) {
        feedRequestCounter.increment();
        return Timer.builder("gateway.routing.latency")
                .tag("route", "feed")
                .description("Latency of routing requests to recommendation service")
                .register(meterRegistry)
                .record(() -> {
                    try {
                        log.info("Routing GET /feed/{} → recommendation service", userId);
                        ResponseEntity<Map> response = restTemplate.getForEntity(
                                recommendationBaseUrl + "/recommend/" + userId, Map.class);
                        return response.getBody();
                    } catch (HttpServerErrorException ex) {
                        recommendationFailureCounter.increment();
                        kafkaProducerService.publishUpstreamError("recommendation", ex.getStatusCode().value());
                        log.error("Recommendation service returned {}", ex.getStatusCode(), ex);
                        throw ex;
                    } catch (ResourceAccessException ex) {
                        recommendationFailureCounter.increment();
                        kafkaProducerService.publishUpstreamError("recommendation", 503);
                        log.error("Recommendation service unreachable", ex);
                        throw ex;
                    }
                });
    }

    public Map<String, Object> search(String query) {
        searchRequestCounter.increment();
        return Timer.builder("gateway.routing.latency")
                .tag("route", "search")
                .description("Latency of routing search requests")
                .register(meterRegistry)
                .record(() -> {
                    try {
                        log.info("Routing GET /search?q={} → search service", query);
                        ResponseEntity<Map> response = restTemplate.getForEntity(
                                searchBaseUrl + "/search?q=" + query, Map.class);
                        return response.getBody();
                    } catch (HttpServerErrorException ex) {
                        searchFailureCounter.increment();
                        kafkaProducerService.publishUpstreamError("search", ex.getStatusCode().value());
                        log.error("Search service returned {}", ex.getStatusCode(), ex);
                        throw ex;
                    } catch (ResourceAccessException ex) {
                        searchFailureCounter.increment();
                        kafkaProducerService.publishUpstreamError("search", 503);
                        log.error("Search service unreachable", ex);
                        throw ex;
                    }
                });
    }

    public Map<String, Object> getAllHealth() {
        Map<String, Object> result = new HashMap<>();
        String[] services = {"recommendation", "search", "media"};
        String[] urls = {
            recommendationBaseUrl + "/actuator/health",
            searchBaseUrl + "/actuator/health",
            mediaBaseUrl + "/actuator/health"
        };
        for (int i = 0; i < services.length; i++) {
            try {
                ResponseEntity<Map> resp = restTemplate.getForEntity(urls[i], Map.class);
                result.put(services[i], resp.getBody());
            } catch (Exception ex) {
                result.put(services[i], Map.of("status", "DOWN", "error", ex.getMessage()));
            }
        }
        return result;
    }

    public Map<String, Object> simulateTrafficFlood() {
        int concurrentRequests = 500;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        log.warn("[SIMULATE] Starting traffic flood: {} concurrent requests", concurrentRequests);
        kafkaProducerService.publishTrafficFloodEvent(concurrentRequests);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<?>[] futures = new CompletableFuture[concurrentRequests];
            for (int i = 0; i < concurrentRequests; i++) {
                int finalI = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        String target = (finalI % 3 == 0) ? searchBaseUrl + "/search?q=flood"
                                : (finalI % 3 == 1) ? recommendationBaseUrl + "/recommend/user" + finalI
                                : mediaBaseUrl + "/actuator/health";
                        restTemplate.getForObject(target, String.class);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                }, pool);
            }
            CompletableFuture.allOf(futures).join();
        }

        return Map.of(
                "simulation", "TRAFFIC_FLOOD",
                "totalRequests", concurrentRequests,
                "succeeded", successCount.get(),
                "failed", failureCount.get()
        );
    }

    public Map<String, Object> simulateSlowUpstream() throws InterruptedException {
        long delayMs = 3000L;
        log.warn("[SIMULATE] Simulating slow upstream with {}ms delay", delayMs);
        kafkaProducerService.publishSlowUpstreamEvent("all", delayMs);
        Thread.sleep(delayMs);
        return Map.of(
                "simulation", "SLOW_UPSTREAM",
                "artificialDelayMs", delayMs,
                "message", "Upstream latency simulation complete — all routes delayed by " + delayMs + "ms"
        );
    }

    public Map<String, Object> simulateRateLimitBreach() {
        int burstRequests = 200;
        log.warn("[SIMULATE] Simulating rate-limit breach with {} burst requests", burstRequests);
        AtomicInteger sent = new AtomicInteger(0);
        try (ExecutorService pool = Executors.newFixedThreadPool(50)) {
            CompletableFuture<?>[] futures = new CompletableFuture[burstRequests];
            for (int i = 0; i < burstRequests; i++) {
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        restTemplate.getForObject(searchBaseUrl + "/search?q=rate-test", String.class);
                        sent.incrementAndGet();
                    } catch (Exception ignored) {}
                }, pool);
            }
            CompletableFuture.allOf(futures).join();
        }
        kafkaProducerService.publishTrafficFloodEvent(burstRequests);
        return Map.of(
                "simulation", "RATE_LIMIT_BREACH",
                "burstRequests", burstRequests,
                "requestsSent", sent.get(),
                "message", "Rate limit breach simulation complete"
        );
    }
}
