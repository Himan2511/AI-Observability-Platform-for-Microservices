package com.monitor.search.service;

import com.monitor.search.model.SearchResult;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Core Search Service — simulates full-text search over a 100,000-document in-memory dataset.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final MeterRegistry meterRegistry;
    private final KafkaProducerService kafkaProducerService;

    @Value("${search.dataset.size:100000}")
    private int datasetSize;

    @Value("${search.slow-query-threshold-ms:500}")
    private long slowQueryThresholdMs;

    @Value("${search.slow-query-kafka-threshold-ms:2000}")
    private long kafkaThresholdMs;

    private final List<Map<String, String>> documents = new CopyOnWriteArrayList<>();
    private final List<byte[]> heapPressureHolders    = new CopyOnWriteArrayList<>();

    private Counter totalQueriesCounter;
    private Counter slowQueriesCounter;

    private final AtomicLong totalQueryCount = new AtomicLong(0);
    private final AtomicLong slowQueryCount  = new AtomicLong(0);
    private final AtomicLong totalLatencyMs  = new AtomicLong(0);

    public SearchService(MeterRegistry meterRegistry, KafkaProducerService kafkaProducerService) {
        this.meterRegistry = meterRegistry;
        this.kafkaProducerService = kafkaProducerService;
    }

    @PostConstruct
    public void init() {
        buildIndex();
        registerMetrics();
        log.info("SearchService initialised with {} documents", documents.size());
    }

    public void buildIndex() {
        log.info("Building in-memory search index with {} documents...", datasetSize);
        documents.clear();
        String[] topics = {"technology", "science", "health", "finance", "sports", "travel", "food", "culture"};
        String[] verbs   = {"explores", "discovers", "reveals", "analyzes", "investigates", "transforms"};
        String[] nouns   = {"innovation", "breakthrough", "discovery", "challenge", "opportunity", "revolution"};
        Random random = new Random(42);

        for (int i = 0; i < datasetSize; i++) {
            Map<String, String> doc = new HashMap<>();
            doc.put("id", "doc-" + i);
            doc.put("title", "Article " + i + ": How " + topics[i % topics.length] + " " + verbs[i % verbs.length] + " " + nouns[i % nouns.length]);
            doc.put("content", "This is the detailed content of document " + i + " covering " + topics[random.nextInt(topics.length)] + ". Keywords: " + nouns[random.nextInt(nouns.length)] + ", " + topics[random.nextInt(topics.length)] + ".");
            doc.put("topic", topics[i % topics.length]);
            documents.add(doc);
        }
        log.info("Index built: {} documents ready", documents.size());
    }

    private void registerMetrics() {
        totalQueriesCounter = Counter.builder("search.queries.total")
                .description("Total number of search queries executed").register(meterRegistry);
        slowQueriesCounter = Counter.builder("search.queries.slow")
                .description("Number of search queries exceeding the slow threshold").register(meterRegistry);
        Gauge.builder("search.index.size", documents, List::size)
                .description("Number of documents in the in-memory search index").register(meterRegistry);
        Gauge.builder("search.heap.pressure", heapPressureHolders,
                holders -> holders.stream().mapToLong(arr -> arr.length).sum() / (1024.0 * 1024.0))
                .description("Estimated heap memory held by search operations in MB").baseUnit("megabytes").register(meterRegistry);
    }

    @Timed(value = "search.query.duration", description = "Duration of paginated search queries")
    public SearchResult search(String query, int page, int size) {
        long start = System.currentTimeMillis();
        totalQueriesCounter.increment();
        totalQueryCount.incrementAndGet();

        List<Map<String, String>> results = documents.stream()
                .filter(doc -> doc.get("title").toLowerCase().contains(query.toLowerCase())
                        || doc.get("content").toLowerCase().contains(query.toLowerCase()))
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());

        long durationMs = System.currentTimeMillis() - start;
        recordQueryMetrics(durationMs);

        long totalHits = documents.stream().filter(doc -> doc.get("title").toLowerCase().contains(query.toLowerCase())).count();
        return buildResult(results, query, durationMs, "PAGINATED", totalHits, page, size);
    }

    @Timed(value = "search.query.duration", description = "Duration of indexed search queries")
    public SearchResult indexedSearch(String query) {
        long start = System.currentTimeMillis();
        totalQueriesCounter.increment();
        totalQueryCount.incrementAndGet();

        List<Map<String, String>> results = documents.stream().limit(1000)
                .filter(doc -> doc.get("topic").equalsIgnoreCase(query) || doc.get("title").toLowerCase().contains(query.toLowerCase()))
                .limit(20).collect(Collectors.toList());

        long durationMs = System.currentTimeMillis() - start;
        recordQueryMetrics(durationMs);
        return buildResult(results, query, durationMs, "INDEXED", results.size(), 0, 20);
    }

    @Timed(value = "search.query.duration", description = "Duration of full-scan search queries")
    public SearchResult fullScanSearch(String query) {
        long start = System.currentTimeMillis();
        totalQueriesCounter.increment();
        totalQueryCount.incrementAndGet();

        List<Map<String, String>> results = documents.stream()
                .filter(doc -> doc.get("content").toLowerCase().contains(query.toLowerCase()))
                .sorted((a, b) -> {
                    int scoreA = a.get("content").split(query.toLowerCase(), -1).length;
                    int scoreB = b.get("content").split(query.toLowerCase(), -1).length;
                    return Integer.compare(scoreB, scoreA);
                })
                .limit(50).collect(Collectors.toList());

        long durationMs = System.currentTimeMillis() - start;
        recordQueryMetrics(durationMs);
        return buildResult(results, query, durationMs, "FULL_SCAN", results.size(), 0, 50);
    }

    public Map<String, Object> getStats() {
        long totalQueries = totalQueryCount.get();
        return Map.of(
                "indexSize", documents.size(),
                "totalQueries", totalQueries,
                "slowQueries", slowQueryCount.get(),
                "averageLatencyMs", totalQueries > 0 ? totalLatencyMs.get() / totalQueries : 0,
                "heapPressureMB", heapPressureHolders.stream().mapToLong(a -> a.length).sum() / (1024 * 1024)
        );
    }

    public Map<String, Object> simulateWildcardFlood() {
        log.warn("[SIMULATE] Starting wildcard flood");
        int rounds = 20;
        List<List<Map<String, String>>> retained = new ArrayList<>();

        for (int i = 0; i < rounds; i++) {
            List<Map<String, String>> results = new ArrayList<>(documents);
            retained.add(results);
            long heapUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long heapMax  = Runtime.getRuntime().maxMemory();
            double heapPct = (double) heapUsed / heapMax * 100;
            totalQueriesCounter.increment();
            slowQueriesCounter.increment();
            if (heapPct > 60) { kafkaProducerService.publishHeapPressure(heapPct); }
            log.warn("[SIMULATE] Wildcard flood round {}/{}: heap={:.1f}%", i + 1, rounds, heapPct);
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        retained.clear();
        System.gc();

        return Map.of("simulation", "WILDCARD_FLOOD", "rounds", rounds,
                "message", "Wildcard flood complete — check heap metrics and Loki logs");
    }

    public Map<String, Object> simulateIndexRebuild() {
        log.warn("[SIMULATE] Index rebuild triggered");
        kafkaProducerService.publishIndexRebuild();
        long start = System.currentTimeMillis();
        buildIndex();
        long durationMs = System.currentTimeMillis() - start;
        log.warn("[SIMULATE] Index rebuild complete in {}ms", durationMs);
        return Map.of("simulation", "INDEX_REBUILD", "durationMs", durationMs,
                "documentsIndexed", documents.size(), "message", "Index rebuild complete — check CPU metrics");
    }

    public Map<String, Object> simulateSlowQuery() throws InterruptedException {
        long delayMs = 3500L;
        log.warn("[SIMULATE] Slow query simulation — adding {}ms delay", delayMs);
        long start = System.currentTimeMillis();
        totalQueriesCounter.increment();
        Thread.sleep(delayMs);
        long durationMs = System.currentTimeMillis() - start;
        slowQueriesCounter.increment();
        slowQueryCount.incrementAndGet();
        kafkaProducerService.publishSlowQuery(durationMs);
        log.warn("[SIMULATE] Slow query complete: durationMs={}", durationMs);
        return Map.of("simulation", "SLOW_QUERY", "simulatedDelayMs", delayMs,
                "actualDurationMs", durationMs, "message", "Slow query simulation complete");
    }

    private void recordQueryMetrics(long durationMs) {
        totalLatencyMs.addAndGet(durationMs);
        if (durationMs > slowQueryThresholdMs) {
            slowQueriesCounter.increment();
            slowQueryCount.incrementAndGet();
            log.warn("Slow query detected: {}ms (threshold: {}ms)", durationMs, slowQueryThresholdMs);
        }
        if (durationMs > kafkaThresholdMs) {
            kafkaProducerService.publishSlowQuery(durationMs);
        }
    }

    private SearchResult buildResult(List<Map<String, String>> docs, String query, long durationMs,
                                     String queryType, long totalHits, int page, int size) {
        List<SearchResult.SearchDocument> resultDocs = docs.stream()
                .map(doc -> SearchResult.SearchDocument.builder()
                        .id(doc.get("id"))
                        .title(doc.get("title"))
                        .content(doc.get("content").substring(0, Math.min(150, doc.get("content").length())))
                        .score(Math.random())
                        .build())
                .collect(Collectors.toList());

        return SearchResult.builder()
                .totalHits(totalHits).page(page).pageSize(size)
                .queryTimeMs(durationMs).queryType(queryType).documents(resultDocs).build();
    }
}
