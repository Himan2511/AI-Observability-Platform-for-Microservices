#!/bin/bash
# ============================================================
# Kafka Topic Initialisation Script
# AI Observability Platform — Phase 2
#
# Creates the 3 required Kafka topics for the platform:
#   - anomaly-events  (3 partitions) — service anomaly triggers
#   - error-events    (3 partitions) — HTTP 5xx errors
#   - deploy-events   (1 partition)  — deployment notifications
#
# Run by the init-kafka service in docker-compose.yml
# ============================================================

set -e

KAFKA_BOOTSTRAP="kafka:29092"
WAIT_TIMEOUT=60

echo "========================================"
echo " Kafka Topic Initialisation"
echo " Bootstrap: $KAFKA_BOOTSTRAP"
echo "========================================"

# ── Wait for Kafka to be ready ─────────────────────────────────────────────
echo ""
echo "[INFO] Waiting for Kafka to be ready..."

ELAPSED=0
until kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --list > /dev/null 2>&1; do
    if [ $ELAPSED -ge $WAIT_TIMEOUT ]; then
        echo "[ERROR] Timed out waiting for Kafka after ${WAIT_TIMEOUT}s"
        exit 1
    fi
    echo "[INFO] Kafka not ready yet... retrying in 3s (${ELAPSED}s elapsed)"
    sleep 3
    ELAPSED=$((ELAPSED + 3))
done

echo "[OK] Kafka is ready!"
echo ""

# ── Create Topics ──────────────────────────────────────────────────────────

# anomaly-events: Published by all 4 services when their anomaly thresholds
# are crossed. Consumed by ai-engine in Phase 3 to trigger AI analysis.
echo "[INFO] Creating topic: anomaly-events"
kafka-topics.sh \
    --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic anomaly-events \
    --partitions 3 \
    --replication-factor 1 \
    --config retention.ms=604800000 \
    --config cleanup.policy=delete
echo "[OK] anomaly-events created (3 partitions, 7-day retention)"

# error-events: Published by Gateway when downstream services return 5xx.
# Also published by services when they detect internal errors.
echo ""
echo "[INFO] Creating topic: error-events"
kafka-topics.sh \
    --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic error-events \
    --partitions 3 \
    --replication-factor 1 \
    --config retention.ms=604800000 \
    --config cleanup.policy=delete
echo "[OK] error-events created (3 partitions, 7-day retention)"

# deploy-events: Published by CI/CD pipeline or external tooling to notify
# the ai-engine of deployments (marks deployment timestamps on charts).
echo ""
echo "[INFO] Creating topic: deploy-events"
kafka-topics.sh \
    --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic deploy-events \
    --partitions 1 \
    --replication-factor 1 \
    --config retention.ms=2592000000 \
    --config cleanup.policy=delete
echo "[OK] deploy-events created (1 partition, 30-day retention)"

# ── Verification ───────────────────────────────────────────────────────────
echo ""
echo "[INFO] Current Kafka topics:"
kafka-topics.sh \
    --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --list

echo ""
echo "[INFO] Topic details:"
kafka-topics.sh \
    --bootstrap-server "$KAFKA_BOOTSTRAP" \
    --describe \
    --topics-with-overrides

echo ""
echo "========================================"
echo " Kafka initialisation complete!"
echo " Topics: anomaly-events, error-events, deploy-events"
echo "========================================"
