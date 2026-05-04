#!/usr/bin/env bash
# ============================================================
# setup.sh — Initialisation Exchange Rate Proxy
# Usage : bash scripts/setup.sh
# ============================================================
set -euo pipefail

ES_URL="http://localhost:9200"
KAFKA_BS="localhost:9092"
APP_URL="http://localhost:8090"

echo "╔══════════════════════════════════════════╗"
echo "║   Exchange Rate Proxy — Setup Script     ║"
echo "╚══════════════════════════════════════════╝"

# 1. Elasticsearch template
echo "⏳ Attente Elasticsearch..."
until curl -s "$ES_URL/_cluster/health" | grep -q '"status"'; do sleep 2; done
echo "✅ Elasticsearch prêt"

echo "📋 Application template Elasticsearch..."
curl -s -X PUT "$ES_URL/_index_template/exchange-rates-template" \
  -H "Content-Type: application/json" \
  -d @elasticsearch/index-template.json
echo ""

# 2. Topic Kafka
echo "📦 Création topic Kafka exchange-rates..."
docker exec proxy-kafka /opt/kafka/bin/kafka-topics.sh \
  --create --bootstrap-server localhost:9092 \
  --topic exchange-rates --partitions 3 --replication-factor 1 \
  --if-not-exists 2>/dev/null || echo "  (Kafka CLI non disponible, le topic sera créé par Spring)"

# 3. Test API
echo "⏳ Attente application Spring Boot..."
until curl -s "$APP_URL/actuator/health" | grep -q '"status":"UP"' 2>/dev/null; do sleep 3; done
echo "✅ Application démarrée"

# 4. Premier fetch
echo "💱 Premier fetch USD..."
curl -s -u api-user:proxy-secret-2026 "$APP_URL/api/exchange-rates/refresh/USD" | \
  python3 -c "import sys,json; d=json.load(sys.stdin); print(f'  ✅ {len(d[\"data\"][\"rates\"])} devises indexées')" 2>/dev/null || \
  echo "  Fetch en cours..."

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║  ✅ Setup terminé !                      ║"
echo "║  Dashboard : frontend/index.html         ║"
echo "║  API       : http://localhost:8090       ║"
echo "║  Kibana    : http://localhost:5601       ║"
echo "╚══════════════════════════════════════════╝"
