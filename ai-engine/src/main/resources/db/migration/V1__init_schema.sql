-- ============================================================
-- PostgreSQL + TimescaleDB + PgVector Schema
-- AI Observability Platform — Phase 2
--
-- Run automatically on first container start via Docker Compose
-- volume mount to /docker-entrypoint-initdb.d/
--
-- Tables:
--   1. service_registry       — monitored service definitions
--   2. incidents              — detected anomalies
--   3. ai_analysis_results    — Gemini root cause analysis per incident
--   4. alert_rules            — configurable thresholds
--   5. anomaly_history        — TimescaleDB hypertable (time-series)
--   6. incident_embeddings    — PgVector RAG embeddings
-- ============================================================

-- ── Extensions ───────────────────────────────────────────────────────────

-- TimescaleDB: time-series optimisation for anomaly_history
CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;

-- PgVector: embedding storage for RAG (Phase 3)
CREATE EXTENSION IF NOT EXISTS vector;

-- ── 1. Service Registry ──────────────────────────────────────────────────
-- Registry of all monitored microservices.
-- Seeded with the 4 Phase 1 services.

CREATE TABLE IF NOT EXISTS service_registry (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL UNIQUE,
    base_url    VARCHAR(255)    NOT NULL,
    port        INT             NOT NULL,
    description TEXT,
    active      BOOLEAN         DEFAULT TRUE,
    created_at  TIMESTAMPTZ     DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     DEFAULT NOW()
);

-- ── 2. Incidents ─────────────────────────────────────────────────────────
-- Each detected anomaly creates one row. Transitions:
--   OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED

CREATE TABLE IF NOT EXISTS incidents (
    id              BIGSERIAL       PRIMARY KEY,
    service_name    VARCHAR(100)    NOT NULL,
    anomaly_type    VARCHAR(100)    NOT NULL,
    -- anomaly_type values: CPU_SPIKE, MEMORY_SURGE, LATENCY_SPIKE,
    --   ERROR_RATE, THREAD_EXHAUSTION, SLOW_QUERY, HEAP_PRESSURE,
    --   CONCURRENT_JOB_OVERFLOW, MEMORY_LEAK, THUNDERING_HERD,
    --   ALGORITHM_OVERLOAD, INDEX_REBUILD, UPSTREAM_5XX, TRAFFIC_FLOOD
    severity        VARCHAR(20)     NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    status          VARCHAR(20)     DEFAULT 'OPEN'
                                    CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED')),
    metric_value    DOUBLE PRECISION,
    threshold_value DOUBLE PRECISION,
    description     TEXT,
    detected_at     TIMESTAMPTZ     DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    FOREIGN KEY (service_name) REFERENCES service_registry(name)
        ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_incidents_service_name   ON incidents (service_name);
CREATE INDEX IF NOT EXISTS idx_incidents_status          ON incidents (status);
CREATE INDEX IF NOT EXISTS idx_incidents_severity        ON incidents (severity);
CREATE INDEX IF NOT EXISTS idx_incidents_detected_at     ON incidents (detected_at DESC);

-- ── 3. AI Analysis Results ───────────────────────────────────────────────
-- Gemini-generated root cause analysis linked to each incident.
-- One-to-one with incidents (one AI call per incident).

CREATE TABLE IF NOT EXISTS ai_analysis_results (
    id                      BIGSERIAL   PRIMARY KEY,
    incident_id             BIGINT      NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    root_cause              TEXT,
    contributing_factors    TEXT,           -- JSON array stored as TEXT
    recommended_action      TEXT,
    preventive_measures     TEXT,           -- JSON array stored as TEXT
    estimated_recovery_time VARCHAR(100),
    similar_incidents_found INT             DEFAULT 0,
    model_used              VARCHAR(100),
    tokens_used             INT,
    prompt_tokens           INT,
    completion_tokens       INT,
    created_at              TIMESTAMPTZ     DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_results_incident_id ON ai_analysis_results (incident_id);

-- ── 4. Alert Rules ───────────────────────────────────────────────────────
-- Configurable thresholds. service_name NULL = applies to ALL services.
-- Managed via REST API in Phase 3 (Admin role only).

CREATE TABLE IF NOT EXISTS alert_rules (
    id              BIGSERIAL       PRIMARY KEY,
    service_name    VARCHAR(100),   -- NULL = global rule
    metric_name     VARCHAR(100)    NOT NULL,
    threshold       DOUBLE PRECISION NOT NULL,
    operator        VARCHAR(10)     NOT NULL CHECK (operator IN ('GT', 'LT', 'EQ', 'GTE', 'LTE')),
    severity        VARCHAR(20)     NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    description     TEXT,
    enabled         BOOLEAN         DEFAULT TRUE,
    created_at      TIMESTAMPTZ     DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_service_name  ON alert_rules (service_name);
CREATE INDEX IF NOT EXISTS idx_alert_rules_enabled        ON alert_rules (enabled);

-- ── 5. Anomaly History (TimescaleDB Hypertable) ───────────────────────────
-- Time-series table for storing metric readings at anomaly detection time.
-- TimescaleDB compresses older data automatically.
-- Queried by Phase 3 AI engine for trend analysis.

CREATE TABLE IF NOT EXISTS anomaly_history (
    time                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    service_name        VARCHAR(100)    NOT NULL,
    metric_name         VARCHAR(100)    NOT NULL,
    metric_value        DOUBLE PRECISION NOT NULL,
    baseline_value      DOUBLE PRECISION,
    deviation_percent   DOUBLE PRECISION,
    incident_id         BIGINT          REFERENCES incidents(id) ON DELETE SET NULL
);

-- Convert to TimescaleDB hypertable (time-partitioned, 1-day chunks)
SELECT create_hypertable(
    'anomaly_history',
    'time',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => TRUE
);

CREATE INDEX IF NOT EXISTS idx_anomaly_history_service ON anomaly_history (service_name, time DESC);
CREATE INDEX IF NOT EXISTS idx_anomaly_history_metric  ON anomaly_history (metric_name, time DESC);

-- ── 6. Incident Embeddings (PgVector RAG) ───────────────────────────────
-- Gemini text embeddings for each resolved incident.
-- Used in Phase 3 for RAG: retrieve similar past incidents
-- before calling Gemini for root cause analysis.
--
-- Dimension: 768 (Gemini text-embedding-004)

CREATE TABLE IF NOT EXISTS incident_embeddings (
    id              BIGSERIAL   PRIMARY KEY,
    incident_id     BIGINT      NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    summary_text    TEXT        NOT NULL,   -- human-readable incident summary
    embedding       vector(768),            -- Gemini embedding vector
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- IVFFlat index for approximate nearest-neighbour search
-- (cosine similarity — used by Phase 3 RAG query)
CREATE INDEX IF NOT EXISTS idx_incident_embeddings_vector
    ON incident_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);

-- ════════════════════════════════════════════════════════════
-- SEED DATA
-- ════════════════════════════════════════════════════════════

-- ── Seed: Service Registry ────────────────────────────────────────────────

INSERT INTO service_registry (name, base_url, port, description, active) VALUES
(
    'service-gateway',
    'http://localhost:8081',
    8081,
    'API Gateway — single entry point, routes requests to all downstream services, enforces rate limiting',
    TRUE
),
(
    'service-search',
    'http://localhost:8082',
    8082,
    'Search Service — full-text search over 100k in-memory documents, CPU/memory intensive',
    TRUE
),
(
    'service-media',
    'http://localhost:8083',
    8083,
    'Media/File Processing Service — accepts uploads, simulates resize/thumbnail/compression pipeline',
    TRUE
),
(
    'service-recommendation',
    'http://localhost:8084',
    8084,
    'Recommendation/Feed Service — personalised content feed using scoring algorithm with Redis caching',
    TRUE
)
ON CONFLICT (name) DO NOTHING;

-- ── Seed: Alert Rules ─────────────────────────────────────────────────────
-- Default thresholds per the project plan.
-- service_name NULL = rule applies to ALL services.

INSERT INTO alert_rules (service_name, metric_name, threshold, operator, severity, description, enabled) VALUES

-- Gateway: CPU spike
(
    'service-gateway',
    'process_cpu_usage_percent',
    80.0,
    'GT',
    'WARNING',
    'Gateway CPU usage exceeded 80% — possible traffic flood or slow upstream cascade',
    TRUE
),

-- Search: Heap memory critical
(
    'service-search',
    'jvm_heap_usage_percent',
    85.0,
    'GT',
    'CRITICAL',
    'Search service heap exceeds 85% — wildcard flood or GC thrashing likely',
    TRUE
),

-- Media: Concurrent processing jobs
(
    'service-media',
    'media_processing_active',
    15.0,
    'GT',
    'WARNING',
    'Media service has more than 15 concurrent processing jobs — thread exhaustion risk',
    TRUE
),

-- Recommendation: Cache miss rate
(
    'service-recommendation',
    'recommendation_cache_miss_rate_percent',
    70.0,
    'GT',
    'WARNING',
    'Recommendation cache miss rate exceeds 70% — thundering herd pattern detected',
    TRUE
),

-- Global: HTTP error rate
(
    NULL,
    'http_error_rate_percent',
    5.0,
    'GT',
    'CRITICAL',
    'HTTP 5xx error rate exceeds 5% across any service',
    TRUE
),

-- Global: Request latency p95
(
    NULL,
    'http_request_latency_p95_ms',
    2000.0,
    'GT',
    'WARNING',
    'p95 request latency exceeds 2000ms — upstream slowness or overload',
    TRUE
)

ON CONFLICT DO NOTHING;

-- ════════════════════════════════════════════════════════════
-- VERIFICATION QUERIES (run manually to confirm setup)
-- ════════════════════════════════════════════════════════════
--
-- SELECT * FROM service_registry;
-- SELECT * FROM alert_rules ORDER BY service_name NULLS LAST;
-- SELECT * FROM timescaledb_information.hypertables WHERE hypertable_name = 'anomaly_history';
-- \dx   -- shows installed extensions
