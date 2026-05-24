package com.monitor.recommendation.service;

import com.monitor.recommendation.model.RecommendationFeed;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Core Recommendation Service with Redis cache and scoring algorithm.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final MeterRegistry meterRegistry;
    private final KafkaProducerService kafkaProducerService;
    private final StringRedisTemplate redisTemplate;

    @Value("${recommendation.dataset.size:10000}")
    private int datasetSize;

    @Value("${recommendation.cache.ttl-minutes:5}")
    private long cacheTtlMinutes;

    @Value("${recommendation.cache.miss-rate-threshold:0.70}")
    private double cacheMissRateThreshold;

    @Value("${recommendation.feed.size:20}")
    private int feedSize;

    private List<Map<String, Object>> contentDataset;

    private Counter cacheHitsCounter;
    private Counter cacheMissesCounter;
    private DistributionSummary feedSizeHistogram;

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits     = new AtomicLong(0);
    private final AtomicLong cacheMisses   = new AtomicLong(0);
    private final AtomicLong algorithmRuns = new AtomicLong(0);

    public RecommendationService(MeterRegistry meterRegistry,
                                  KafkaProducerService kafkaProducerService,
                                  StringRedisTemplate redisTemplate) {
        this.meterRegistry = meterRegistry;
        this.kafkaProducerService = kafkaProducerService;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        buildContentDataset(datasetSize);
        registerMetrics();
        log.info("RecommendationService initialised with {} content items", contentDataset.size());
    }

    private void buildContentDataset(int size) {
        String[] categories = {"technology", "science", "health", "finance", "sports", "travel", "entertainment", "politics"};
        String[] qualities  = {"viral", "trending", "popular", "niche", "evergreen", "breaking"};
        Random random = new Random(42);
        contentDataset = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("contentId", "content-" + i);
            item.put("title", "Article " + i + ": " + qualities[i % qualities.length] + " " + categories[i % categories.length]);
            item.put("category", categories[i % categories.length]);
            item.put("baseEngagement", random.nextDouble());
            item.put("baseRelevance",  random.nextDouble());
            item.put("freshness", random.nextDouble());
            contentDataset.add(item);
        }
    }

    private void registerMetrics() {
        cacheHitsCounter = Counter.builder("recommendation.cache.hits")
                .description("Number of Redis cache hits").register(meterRegistry);
        cacheMissesCounter = Counter.builder("recommendation.cache.misses")
                .description("Number of Redis cache misses").register(meterRegistry);
        feedSizeHistogram = DistributionSummary.builder("recommendation.feed.size")
                .description("Items per generated feed").publishPercentiles(0.5, 0.95).register(meterRegistry);
    }

    @Timed(value = "recommendation.algorithm.duration", description = "Duration of recommendation algorithm")
    public RecommendationFeed getFeed(String userId) {
        totalRequests.incrementAndGet();
        String cacheKey = "feed:" + userId;
        String cached = safeGet(cacheKey);
        if (cached != null) {
            cacheHitsCounter.increment();
            cacheHits.incrementAndGet();
            log.debug("Cache HIT for userId={}", userId);
            return buildCachedResponse(userId, cached);
        }
        cacheMissesCounter.increment();
        cacheMisses.incrementAndGet();
        log.debug("Cache MISS for userId={} — running scoring algorithm", userId);
        checkMissRateThreshold();
        return runAlgorithmAndCache(userId, cacheKey);
    }

    @Timed(value = "recommendation.algorithm.duration", description = "Duration of recommendation algorithm")
    public RecommendationFeed refreshFeed(String userId) {
        totalRequests.incrementAndGet();
        cacheMissesCounter.increment();
        cacheMisses.incrementAndGet();
        log.info("Force refresh for userId={}", userId);
        return runAlgorithmAndCache(userId, "feed:" + userId);
    }

    public Map<String, Object> getStats() {
        long total  = totalRequests.get();
        long hits   = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate  = total > 0 ? (double) hits  / total : 0;
        double missRate = total > 0 ? (double) misses / total : 0;
        return Map.of(
                "totalRequests", total, "cacheHits", hits, "cacheMisses", misses,
                "cacheHitRate", String.format("%.1f%%", hitRate * 100),
                "cacheMissRate", String.format("%.1f%%", missRate * 100),
                "algorithmRuns", algorithmRuns.get(), "datasetSize", contentDataset.size()
        );
    }

    public Map<String, Object> simulateCacheExpiry() {
        log.warn("[SIMULATE] Clearing all recommendation cache keys");
        Set<String> keys = redisTemplate.keys("feed:*");
        int cleared = 0;
        if (keys != null && !keys.isEmpty()) { redisTemplate.delete(keys); cleared = keys.size(); }
        kafkaProducerService.publishThunderingHerd(1.0);
        return Map.of("simulation", "CACHE_EXPIRY", "keysCleared", cleared,
                "message", "All feed cache keys cleared — thundering herd incoming");
    }

    public Map<String, Object> simulateAlgorithmOverload() throws InterruptedException {
        int concurrentUsers = 1000;
        log.warn("[SIMULATE] Algorithm overload — {} users simultaneously", concurrentUsers);
        kafkaProducerService.publishAlgorithmOverload(concurrentUsers);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrentUsers; i++) {
                final String userId = "sim-user-" + i;
                pool.submit(() -> {
                    try { runScoringAlgorithm(userId, feedSize); }
                    finally { latch.countDown(); }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
        }
        return Map.of("simulation", "ALGORITHM_OVERLOAD", "concurrentUsers", concurrentUsers,
                "message", "Algorithm overload complete — check CPU metrics");
    }

    public Map<String, Object> simulateLargeDataset() {
        int originalSize = contentDataset.size();
        log.warn("[SIMULATE] Expanding dataset 10x: {} → {}", originalSize, originalSize * 10);
        buildContentDataset(originalSize * 10);
        return Map.of("simulation", "LARGE_DATASET", "originalSize", originalSize,
                "newSize", contentDataset.size(), "message", "Dataset expanded 10x");
    }

    private RecommendationFeed runAlgorithmAndCache(String userId, String cacheKey) {
        long start = System.currentTimeMillis();
        algorithmRuns.incrementAndGet();
        List<RecommendationFeed.FeedItem> items = runScoringAlgorithm(userId, feedSize);
        long durationMs = System.currentTimeMillis() - start;
        feedSizeHistogram.record(items.size());
        String cacheValue = String.join(",", items.stream().map(RecommendationFeed.FeedItem::getContentId).toList());
        safeSet(cacheKey, cacheValue, Duration.ofMinutes(cacheTtlMinutes));
        log.info("Algorithm complete for userId={}: {}ms, {} items", userId, durationMs, items.size());
        return RecommendationFeed.builder().userId(userId).fromCache(false)
                .algorithmDurationMs(durationMs).totalItems(items.size())
                .generatedAt(Instant.now()).items(items).build();
    }

    private List<RecommendationFeed.FeedItem> runScoringAlgorithm(String userId, int limit) {
        int userSeed = userId.hashCode();
        Random userRandom = new Random(userSeed);
        double[] categoryPreference = new double[8];
        for (int i = 0; i < 8; i++) { categoryPreference[i] = userRandom.nextDouble(); }

        String[] categories = {"technology", "science", "health", "finance", "sports", "travel", "entertainment", "politics"};
        Map<String, Integer> categoryIndex = new HashMap<>();
        for (int i = 0; i < categories.length; i++) { categoryIndex.put(categories[i], i); }

        return contentDataset.stream()
                .map(item -> {
                    int catIdx = categoryIndex.getOrDefault((String) item.get("category"), 0);
                    double relevance  = (double) item.get("baseRelevance") * categoryPreference[catIdx];
                    double engagement = (double) item.get("baseEngagement") * (1 + userRandom.nextDouble() * 0.2);
                    double freshness  = (double) item.get("freshness");
                    double finalScore = (relevance * 0.5) + (engagement * 0.3) + (freshness * 0.2);
                    return RecommendationFeed.FeedItem.builder()
                            .contentId((String) item.get("contentId")).title((String) item.get("title"))
                            .category((String) item.get("category")).relevanceScore(relevance)
                            .engagementScore(engagement).finalScore(finalScore).build();
                })
                .sorted(Comparator.comparingDouble(RecommendationFeed.FeedItem::getFinalScore).reversed())
                .limit(limit).collect(Collectors.toList());
    }

    private RecommendationFeed buildCachedResponse(String userId, String cached) {
        List<RecommendationFeed.FeedItem> items = Arrays.stream(cached.split(","))
                .map(id -> RecommendationFeed.FeedItem.builder().contentId(id).title("Cached: " + id).finalScore(1.0).build())
                .collect(Collectors.toList());
        return RecommendationFeed.builder().userId(userId).fromCache(true)
                .algorithmDurationMs(0).totalItems(items.size())
                .generatedAt(Instant.now()).items(items).build();
    }

    private void checkMissRateThreshold() {
        long total = totalRequests.get();
        long misses = cacheMisses.get();
        if (total > 10) {
            double missRate = (double) misses / total;
            if (missRate >= cacheMissRateThreshold) { kafkaProducerService.publishThunderingHerd(missRate); }
        }
    }

    private String safeGet(String key) {
        try { return redisTemplate.opsForValue().get(key); }
        catch (Exception e) { log.warn("Redis GET failed for key={}: {}", key, e.getMessage()); return null; }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try { redisTemplate.opsForValue().set(key, value, ttl); }
        catch (Exception e) { log.warn("Redis SET failed for key={}: {}", key, e.getMessage()); }
    }
}
