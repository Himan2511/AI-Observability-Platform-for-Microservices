# Phase 1 Documentation — Project Setup & 4 Sample Microservices

**AI Observability Platform for Microservices**
**Phase:** 1 of 6 | **Status:** Complete | **Duration:** ~2–3 days

---

## Overview

Phase 1 establishes the complete **Layer 1** of the observability platform — the four microservices that will be monitored. Each service is a realistic simulation of a backend component found in production systems, instrumented with industry-standard observability tooling.

By the end of Phase 1, you have:
- 4 independently runnable Spring Boot services on ports 8081–8084
- Every service exporting Prometheus-ready metrics at `/actuator/prometheus`
- Distributed traces propagating across all services via Zipkin
- Logs shipping to Grafana Loki with trace IDs in every line
- Kafka events published when anomaly thresholds are crossed
- Simulation endpoints to trigger controllable anomalies

---

## Project Structure

```
ai-observability-platform/          ← Parent POM (aggregator)
├── pom.xml                         ← Multi-module parent, Java 21 baseline
├── service-gateway/                ← Module 1: API Gateway (port 8081)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/monitor/gateway/
│       │   ├── GatewayServiceApplication.java
│       │   ├── config/
│       │   │   ├── RestTemplateConfig.java    ← tracing-aware HTTP client
│       │   │   └── MetricsConfig.java         ← custom Micrometer beans
│       │   ├── controller/
│       │   │   └── GatewayController.java
│       │   ├── service/
│       │   │   ├── GatewayService.java
│       │   │   └── KafkaProducerService.java
│       │   └── model/
│       │       └── KafkaEvent.java
│       └── resources/
│           ├── application.yml
│           └── logback-spring.xml
├── service-search/                 ← Module 2: Search Service (port 8082)
├── service-media/                  ← Module 3: Media Service (port 8083)
├── service-recommendation/         ← Module 4: Recommendation Service (port 8084)
└── ai-engine/                      ← Module 5: AI Engine (port 8080) — built in Phase 3
```

---

## Task 1.1 — Maven Multi-Module Project Scaffold

### What Was Done

The root `pom.xml` was converted from a single Spring Boot application to a **multi-module aggregator POM**.

### Key Design Decisions

| Decision | Rationale |
|---|---|
| `<packaging>pom</packaging>` on root | Marks it as an aggregator, not an app |
| `<dependencyManagement>` with Spring AI BOM | Centralises version management — all modules inherit consistent versions |
| Java 21 baseline | Enables virtual threads (used in traffic flood simulation) |
| Shared `<dependencies>` on parent | Lombok and spring-boot-starter-test go to all modules — no repetition |
| `<pluginManagement>` for spring-boot-maven-plugin | Each module just declares the plugin, inherits config |

### Parent POM Structure

```xml
<groupId>com.monitor</groupId>
<artifactId>ai-observability-platform</artifactId>
<packaging>pom</packaging>

<modules>
  <module>service-gateway</module>
  <module>service-search</module>
  <module>service-media</module>
  <module>service-recommendation</module>
  <module>ai-engine</module>
</modules>
```

### How to Build All Modules

```bash
./mvnw clean package -DskipTests
```

### How to Build a Specific Module

```bash
./mvnw clean package -pl service-gateway -am -DskipTests
```

---

## Task 1.2 — API Gateway Service

**Port:** `8081` | **Package:** `com.monitor.gateway`

### What It Does

The Gateway is the **single entry point** to the system. All external requests pass through it before reaching the three downstream services. It:
- Routes requests to the correct downstream service using `RestTemplate`
- Tracks per-route metrics (requests, latency, upstream failures)
- Detects 5xx errors from downstream services and publishes Kafka events
- Provides simulation endpoints to generate controllable load patterns

### Endpoints

#### Functional Routing

| Method | URL | Downstream Target | Description |
|---|---|---|---|
| `GET` | `/gateway/feed/{userId}` | `service-recommendation:8084` | Get personalised feed |
| `GET` | `/gateway/search?q={query}` | `service-search:8082` | Search across content |
| `POST` | `/gateway/media/upload` | `service-media:8083` | Upload and process media |
| `GET` | `/gateway/health/all` | All 3 services `/actuator/health` | Aggregate health check |

#### Simulation Endpoints

| Method | URL | Effect |
|---|---|---|
| `POST` | `/gateway/simulate/traffic-flood` | Spawns 500 concurrent virtual thread requests to all 3 services |
| `POST` | `/gateway/simulate/slow-upstream` | Sleeps 3s to simulate downstream latency — publishes Kafka event |
| `POST` | `/gateway/simulate/rate-limit-breach` | 200 burst requests to Search Service in parallel |

### Custom Micrometer Metrics

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `gateway.requests.total` | Counter | `route=feed\|search\|media` | Total routed requests per route |
| `gateway.upstream.failures` | Counter | `service=search\|media\|recommendation` | Upstream 5xx/timeout failures |
| `gateway.routing.latency` | Timer | `route=feed\|search\|media` | End-to-end routing latency |

### Kafka Events Published

| Trigger | Topic | Event Type |
|---|---|---|
| Downstream returns 5xx | `error-events` | `UPSTREAM_5XX` |
| Traffic flood simulation | `anomaly-events` | `TRAFFIC_FLOOD` |
| Slow upstream simulation | `anomaly-events` | `SLOW_UPSTREAM` |

### Key Implementation Details

**Tracing-Aware RestTemplate:**
```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(10))
            .build();
}
```
> **Why `RestTemplateBuilder`?** Spring Boot's auto-configured builder instruments the `RestTemplate` with Micrometer Tracing. This ensures B3 headers (`X-B3-TraceId`, `X-B3-SpanId`) are automatically injected into every outbound HTTP request, enabling end-to-end distributed traces in Zipkin.

**Traffic Flood Using Java 21 Virtual Threads:**
```java
try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
    CompletableFuture<?>[] futures = new CompletableFuture[500];
    for (int i = 0; i < 500; i++) {
        futures[i] = CompletableFuture.runAsync(() -> { /* HTTP call */ }, pool);
    }
    CompletableFuture.allOf(futures).join();
}
```
> 500 virtual threads are far more efficient than platform threads for I/O-bound work. This simulates extreme load without crashing the JVM itself.

---

## Task 1.3 — Search Service

**Port:** `8082` | **Package:** `com.monitor.search`

### What It Does

Simulates a full-text search engine operating over a **100,000-document in-memory dataset**. Provides three query strategies with intentionally different performance characteristics.

### Endpoints

#### Functional

| Method | URL | Query Type | Performance |
|---|---|---|---|
| `GET` | `/search?q={query}&page={n}&size={n}` | Stream filter + pagination | Medium |
| `GET` | `/search/indexed?q={query}` | First-1000-doc scan only | Low CPU |
| `GET` | `/search/full?q={query}` | Full dataset scan + sort | High CPU |
| `GET` | `/search/stats` | No scan | Aggregate stats |

#### Simulation

| Method | URL | Effect |
|---|---|---|
| `POST` | `/search/simulate/wildcard-flood` | 20 rounds of full `List` copies — heap pressure + GC pauses |
| `POST` | `/search/simulate/index-rebuild` | Clears and rebuilds 100k doc index — CPU spike |
| `POST` | `/search/simulate/slow-query` | 3.5s `Thread.sleep` — publishes `SLOW_QUERY` Kafka event |

### Custom Micrometer Metrics

| Metric Name | Type | Description |
|---|---|---|
| `search.queries.total` | Counter | Every query execution increments this |
| `search.queries.slow` | Counter | Queries exceeding `slow-query-threshold-ms` (default 500ms) |
| `search.index.size` | Gauge | Live document count — drops during rebuild, rises after |
| `search.heap.pressure` | Gauge (MB) | Sum of byte arrays held by wildcard flood simulation |

### Kafka Events Published

| Trigger | Topic | Event Type |
|---|---|---|
| Query > 2000ms | `anomaly-events` | `SLOW_QUERY` |
| Heap pressure simulation | `anomaly-events` | `HEAP_PRESSURE` |
| Index rebuild simulation | `anomaly-events` | `INDEX_REBUILD` |

### Dataset Initialisation

```java
@PostConstruct
public void init() {
    buildIndex();      // Creates 100,000 synthetic documents
    registerMetrics(); // Registers gauges with live references
}
```

The `search.index.size` gauge holds a **live reference** to the `documents` list:
```java
Gauge.builder("search.index.size", documents, List::size)
     .register(meterRegistry);
```
This means the gauge value changes in real-time when the index is rebuilt or cleared — visible in Prometheus immediately.

---

## Task 1.4 — Media/File Processing Service

**Port:** `8083` | **Package:** `com.monitor.media`

### What It Does

Accepts file uploads and simulates a multi-stage media processing pipeline: **resize → thumbnail → compression**. Tracks concurrent processing jobs and provides simulation endpoints for memory and thread exhaustion scenarios.

### Endpoints

#### Functional

| Method | URL | Description |
|---|---|---|
| `POST` | `/media/upload` | Upload file (or call without file for stub response) |
| `GET` | `/media/{fileId}` | Get processed file metadata |
| `GET` | `/media/jobs/active` | Current active job count + leaked memory info |
| `DELETE` | `/media/{fileId}` | Remove file record from in-memory store |

#### Simulation

| Method | URL | Effect |
|---|---|---|
| `POST` | `/media/simulate/large-upload` | Allocates 500MB byte array, fills all pages |
| `POST` | `/media/simulate/concurrent-jobs` | 20 simultaneous 2-second jobs → thread pressure |
| `POST` | `/media/simulate/memory-leak` | Adds 50MB to a retained list without releasing |

### Custom Micrometer Metrics

| Metric Name | Type | Description |
|---|---|---|
| `media.uploads.total` | Counter | Every upload (real or simulated) |
| `media.processing.active` | Gauge | `AtomicInteger` — live concurrent job count |
| `media.processing.duration` | Timer (`@Timed`) | Per-file processing end-to-end time |
| `media.file.size.bytes` | Distribution Summary | Histogram of file sizes with p50/p75/p95/p99 |

### Kafka Events Published

| Trigger | Topic | Event Type |
|---|---|---|
| Active jobs > `concurrent-jobs-threshold` (10) | `anomaly-events` | `CONCURRENT_JOB_OVERFLOW` |
| Cumulative leaked memory ≥ 100MB | `anomaly-events` | `MEMORY_LEAK_DETECTED` |

### Memory Leak Simulation Design

The memory leak simulation holds a `CopyOnWriteArrayList<byte[]>` at the class level. Each call to `/media/simulate/memory-leak` adds a 50MB `byte[]` to this list and **never removes it**. This:
1. Prevents GC from reclaiming the allocated memory
2. Is persistent across HTTP calls within the same JVM session
3. Is visible in the `search.heap.pressure` gauge and JVM heap metrics in Prometheus

```bash
# Accumulate leaks
curl -X POST http://localhost:8083/media/simulate/memory-leak  # 50MB
curl -X POST http://localhost:8083/media/simulate/memory-leak  # 100MB → Kafka event
curl -X POST http://localhost:8083/media/simulate/memory-leak  # 150MB
```

---

## Task 1.5 — Recommendation/Feed Service

**Port:** `8084` | **Package:** `com.monitor.recommendation`

### What It Does

Generates a personalised content feed for a user by running a **scoring algorithm** over a 10,000-item in-memory dataset, with Redis caching (TTL: 5 minutes). The algorithm is intentionally O(N) per user — scaling the dataset or running it concurrently creates measurable CPU spikes.

### Endpoints

#### Functional

| Method | URL | Description |
|---|---|---|
| `GET` | `/recommend/{userId}` | Cache-first: Redis hit → immediate; miss → algorithm runs |
| `GET` | `/recommend/{userId}/refresh` | Force bypass: always runs algorithm, re-caches |
| `GET` | `/recommend/stats` | Cache hit/miss rate, algorithm run count, dataset size |

#### Simulation

| Method | URL | Effect |
|---|---|---|
| `POST` | `/recommend/simulate/cache-expiry` | Deletes all `feed:*` Redis keys → thundering herd |
| `POST` | `/recommend/simulate/algorithm-overload` | 1000 concurrent algorithm runs via virtual threads |
| `POST` | `/recommend/simulate/large-dataset` | Expands dataset 10x (10k → 100k items) |

### Custom Micrometer Metrics

| Metric Name | Type | Description |
|---|---|---|
| `recommendation.cache.hits` | Counter | Redis cache hits |
| `recommendation.cache.misses` | Counter | Redis cache misses |
| `recommendation.algorithm.duration` | Timer (`@Timed`) | End-to-end algorithm execution time |
| `recommendation.feed.size` | Distribution Summary | Items in each generated feed |

### Kafka Events Published

| Trigger | Topic | Event Type |
|---|---|---|
| Cache miss rate > 70% | `anomaly-events` | `THUNDERING_HERD` |
| Algorithm overload simulation | `anomaly-events` | `ALGORITHM_OVERLOAD` |

### Scoring Algorithm

```java
// For each content item:
double relevance  = baseRelevance  * categoryPreference[userCategoryIdx];
double engagement = baseEngagement * (1 + random.nextDouble() * 0.2);
double freshness  = baseFreshness;
double finalScore = (relevance * 0.5) + (engagement * 0.3) + (freshness * 0.2);
```

Results are sorted descending by `finalScore`, top 20 returned. The algorithm is seeded from `userId.hashCode()` — same user always gets the same feed (deterministic, reproducible).

### Redis Cache Key Schema

```
feed:{userId}   →  "content-0,content-4,content-12,..."   TTL: 5 minutes
```

> **Graceful Redis fallback:** All Redis calls are wrapped in try-catch. If Redis is not running, the service falls back to always running the algorithm. This allows Phase 1 testing without Docker.

---

## Tasks 1.6–1.7 — Actuator + Micrometer (All Services)

### Actuator Configuration

Every service exposes all endpoints:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
```

### Key Actuator Endpoints

| Endpoint | URL | What It Returns |
|---|---|---|
| Health | `/actuator/health` | `{"status":"UP"}` with component detail |
| Prometheus | `/actuator/prometheus` | Full metrics scrape output |
| Metrics | `/actuator/metrics` | Available metric names |
| Info | `/actuator/info` | Application info |
| Loggers | `/actuator/loggers` | Runtime log level management |
| Env | `/actuator/env` | Configuration properties |

### Prometheus Scrape Verification

```bash
# Verify each service
curl http://localhost:8081/actuator/prometheus | grep gateway_requests
curl http://localhost:8082/actuator/prometheus | grep search_queries
curl http://localhost:8083/actuator/prometheus | grep media_uploads
curl http://localhost:8084/actuator/prometheus | grep recommendation_cache
```

### `@Timed` Annotation Usage

Key service methods are annotated with `@Timed` for automatic timing:
```java
@Timed(value = "search.query.duration", description = "Duration of paginated search queries")
public SearchResult search(String query, int page, int size) { ... }
```
This automatically creates a `Timer` metric with histogram buckets, trackable in Prometheus as `search_query_duration_seconds_*`.

---

## Task 1.8 — Distributed Tracing

### Dependencies (Per Service)

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

### Configuration (Per Service)

```yaml
management:
  tracing:
    sampling:
      probability: 1.0    # 100% of requests traced (development mode)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

### How Trace Propagation Works

```
User → GET /gateway/feed/user1
         ↓ [traceId: abc123, spanId: span001]
   RestTemplate call → GET /recommend/user1
         ↓ [traceId: abc123, spanId: span002]  (B3 headers injected automatically)
   Redis lookup, scoring algorithm
         ↑ Response
   Gateway response
```

1. Gateway creates a new **trace** (trace ID = `abc123`)
2. `RestTemplateBuilder`-created `RestTemplate` **automatically injects** `X-B3-TraceId` and `X-B3-SpanId` headers into the outbound call
3. Recommendation Service **automatically reads** those headers and continues the same trace
4. Both spans appear in Zipkin under the same trace ID

### Verification

```bash
# 1. Make a gateway request
curl http://localhost:8081/gateway/feed/user1

# 2. Note the traceId in the response headers or gateway logs
# 3. Open Zipkin: http://localhost:9411
# 4. Search by the traceId — you'll see spans from both Gateway and Recommendation
```

---

## Task 1.9 — Logback → Loki Appender

### Dependency (Per Service's `pom.xml`)

```xml
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>1.5.1</version>
</dependency>
```

### `logback-spring.xml` Structure

```xml
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>http://localhost:3100/loki/api/v1/push</url>
    </http>
    <format>
        <label>
            <!-- Service name as a Loki label — enables per-service log queries -->
            <pattern>app=${appName},host=${HOSTNAME},level=%level</pattern>
        </label>
        <message>
            <!-- traceId and spanId embedded in every log line via MDC -->
            <pattern>... [traceId=%X{traceId:-none},spanId=%X{spanId:-none}] ...</pattern>
        </message>
    </format>
</appender>
```

### MDC Integration with Micrometer Tracing

Micrometer Tracing automatically populates MDC keys `traceId` and `spanId` on every request thread. The Logback pattern `%X{traceId:-none}` reads from MDC — no extra code needed.

### Loki Query Examples (Phase 2+)

```logql
# All logs from search service
{app="service-search"}

# ERROR logs from media service
{app="service-media"} |= "ERROR"

# Find all logs for a specific trace
{app="service-gateway"} | traceId="abc123def456"

# Slow query events across all services
{app=~"service-.*"} |= "SLOW_QUERY"
```

---

## Task 1.10 — Kafka Event Publishing

### Topics

| Topic | Partitions | Published By |
|---|---|---|
| `error-events` | 3 | Gateway (upstream 5xx) |
| `anomaly-events` | 3 | All 4 services (thresholds crossed) |
| `deploy-events` | 1 | External tooling (Phase 3+) |

### Kafka Configuration (Per Service)

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false  # Required for clean JSON deserialization
```

### Event Payload Schema

```json
{
  "service": "search",
  "event": "SLOW_QUERY",
  "value": 3200,
  "description": "Search query took 3200ms — exceeds 2s threshold",
  "timestamp": "2026-05-24T14:32:05Z"
}
```

### Threshold → Kafka Trigger Matrix

| Service | Condition | Topic | Event |
|---|---|---|---|
| Gateway | Downstream 5xx | `error-events` | `UPSTREAM_5XX` |
| Search | Query > 2000ms | `anomaly-events` | `SLOW_QUERY` |
| Search | Heap pressure simulation | `anomaly-events` | `HEAP_PRESSURE` |
| Media | Active jobs > 10 | `anomaly-events` | `CONCURRENT_JOB_OVERFLOW` |
| Media | Leaked memory ≥ 100MB | `anomaly-events` | `MEMORY_LEAK_DETECTED` |
| Recommendation | Cache miss rate > 70% | `anomaly-events` | `THUNDERING_HERD` |
| Recommendation | Algorithm overload sim | `anomaly-events` | `ALGORITHM_OVERLOAD` |

---

## Running All 4 Services Locally

### Prerequisites

- Java 21 (`java -version`)
- Maven wrapper (`./mvnw`) — already in repo
- Optionally: Redis on port 6379 (for Recommendation Service cache)
- Optionally: Kafka on port 9092 (for event publishing)
- Optionally: Zipkin on port 9411 (for distributed traces)
- Optionally: Loki on port 3100 (for log shipping)

> All integrations degrade gracefully — services start and serve traffic even without Redis, Kafka, Zipkin, or Loki.

### Build All Services

```bash
./mvnw clean package -pl service-gateway,service-search,service-media,service-recommendation -am -DskipTests
```

### Start Each Service

Open 4 terminals:

```bash
# Terminal 1 — Gateway
java -jar service-gateway/target/service-gateway-1.0.0-SNAPSHOT.jar

# Terminal 2 — Search
java -jar service-search/target/service-search-1.0.0-SNAPSHOT.jar

# Terminal 3 — Media
java -jar service-media/target/service-media-1.0.0-SNAPSHOT.jar

# Terminal 4 — Recommendation
java -jar service-recommendation/target/service-recommendation-1.0.0-SNAPSHOT.jar
```

### Verify Each Service

```bash
# Health checks
curl http://localhost:8081/actuator/health   # Gateway
curl http://localhost:8082/actuator/health   # Search
curl http://localhost:8083/actuator/health   # Media
curl http://localhost:8084/actuator/health   # Recommendation

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus | grep gateway
curl http://localhost:8082/actuator/prometheus | grep search
curl http://localhost:8083/actuator/prometheus | grep media
curl http://localhost:8084/actuator/prometheus | grep recommendation
```

### Quick Smoke Test

```bash
# 1. Get recommendation feed through gateway
curl http://localhost:8081/gateway/feed/user123

# 2. Search through gateway
curl "http://localhost:8081/gateway/search?q=technology"

# 3. Aggregate health
curl http://localhost:8081/gateway/health/all

# 4. Direct search
curl "http://localhost:8082/search?q=science&page=0&size=5"
curl "http://localhost:8082/search/stats"

# 5. Media upload (no file for stub response)
curl -X POST http://localhost:8083/media/upload
curl http://localhost:8083/media/jobs/active
```

### Trigger Anomaly Simulations

```bash
# Search: wildcard flood (heap pressure)
curl -X POST http://localhost:8082/search/simulate/wildcard-flood

# Search: slow query (3.5s — Kafka event)
curl -X POST http://localhost:8082/search/simulate/slow-query

# Media: memory leak (call 3 times for Kafka event)
curl -X POST http://localhost:8083/media/simulate/memory-leak
curl -X POST http://localhost:8083/media/simulate/memory-leak
curl -X POST http://localhost:8083/media/simulate/memory-leak

# Recommendation: cache expiry (thundering herd)
curl -X POST http://localhost:8084/recommend/simulate/cache-expiry

# Gateway: traffic flood (500 concurrent requests)
curl -X POST http://localhost:8081/gateway/simulate/traffic-flood
```

---

## Observability Checklist (Phase 1 Completion)

| Item | How to Verify | Expected |
|---|---|---|
| All 4 services start | `curl .../actuator/health` × 4 | `{"status":"UP"}` |
| Custom metrics exposed | `curl .../actuator/prometheus \| grep <metric>` | Metric lines appear |
| Distributed trace propagated | Check Gateway logs for traceId; hit Zipkin | Same traceId on Gateway + Recommendation spans |
| Loki logs shipping | Check Loki at `http://localhost:3100` or `{app="service-search"}` | Log entries visible |
| Kafka events firing | Check Kafka consumer / logs after simulation | `[KAFKA] Published *` log lines |
| Simulation endpoints work | POST to each simulate endpoint | JSON response with simulation details |

---

## What's Built in Phase 1 vs What Comes Later

| Feature | Phase 1 | Phase 2+ |
|---|---|---|
| Metrics collection (by services) | ✅ Micrometer + Prometheus endpoint | Phase 2: Prometheus scrapes automatically |
| Log shipping | ✅ Loki appender configured | Phase 2: Loki running in Docker |
| Distributed tracing | ✅ Brave/Zipkin reporter configured | Phase 2: Zipkin running in Docker |
| Kafka event publishing | ✅ Events published on threshold | Phase 2: Kafka running; Phase 3: AI Engine consumes |
| Anomaly detection | ❌ | Phase 3: `@Scheduled` jobs in AI Engine |
| AI root cause analysis | ❌ | Phase 3: Gemini integration |
| Dashboard | ❌ | Phase 4: React + WebSocket |

---

## Troubleshooting

### Service Won't Start — Redis Connection Failed
**Symptom:** `service-recommendation` fails with connection refused to Redis.

**Solution:** The `RedisTemplate` calls are wrapped in try-catch. Ensure `spring.data.redis.*` is set. If Redis isn't running, the service works without caching.

### Kafka Not Reachable
**Symptom:** `KafkaProducerService` logs `Failed to send...`

**Solution:** Services continue to function — Kafka publish failures are logged at WARN level and don't throw exceptions.

### `@Timed` Not Creating Metrics
**Symptom:** `search_query_duration_seconds_*` not visible in Prometheus.

**Solution:** Ensure `spring-boot-starter-actuator` is on the classpath and the bean is a Spring-managed bean (not `new`). The `@Timed` annotation requires AOP — check that `spring-boot-starter-aop` is transitively included (it is via `spring-boot-starter-web`).

---

*Documentation generated: Phase 1 — AI Observability Platform*
*Next: [Phase 2 — Data Infrastructure with Docker Compose](phase-2-documentation.md)*
