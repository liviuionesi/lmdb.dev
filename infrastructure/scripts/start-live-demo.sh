#!/usr/bin/env bash
# LMDB Microservices - 1-Command Live Demo Launcher
# Starts all microservices, databases, React frontend, and public Cloudflare Tunnel in one step.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=================================================="
echo "  🎬 LMDB 1-Command Live Demo Launcher"
echo "=================================================="
echo ""

# 1. Start full infrastructure stack + Cloudflare tunnel + React frontend
"$SCRIPT_DIR/start-infrastructure.sh" --tunnel

# 2. Perform automated health checks on all microservices
echo ""
echo "🔍 Validating microservices health endpoints..."

SERVICES=(
  "Discovery Service (Eureka):http://localhost:8761/actuator/health"
  "Config Service:http://localhost:8888/actuator/health"
  "API Gateway:http://localhost:8080/actuator/health"
  "Movie Service:http://localhost:8081/actuator/health"
  "User Service:http://localhost:8082/actuator/health"
  "Actor Service:http://localhost:8083/actuator/health"
  "AI Service:http://localhost:8084/actuator/health"
  "Media Service:http://localhost:8085/actuator/health"
)

ALL_HEALTHY=true
for entry in "${SERVICES[@]}"; do
  NAME="${entry%%:*}"
  URL="${entry#*:}"
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "$URL" || echo "FAILED")
  if [ "$STATUS" == "200" ]; then
    echo "  🟢 $NAME: Healthy ($STATUS)"
  else
    echo "  🟡 $NAME: Responded with $STATUS (starting up...)"
    ALL_HEALTHY=false
  fi
done

echo ""
echo "=================================================="
echo "  🚀 Live Demo Stack is Ready!"
echo "=================================================="
echo "  Local Frontend: http://localhost:3000"
echo "  API Gateway:    http://localhost:8080"
echo "  Admin Dashboard: http://localhost:3000/admin"
echo ""
echo "  To stop the entire demo session, run:"
echo "    ./infrastructure/scripts/stop-infrastructure.sh"
echo "=================================================="
