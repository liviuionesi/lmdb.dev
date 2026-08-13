#!/bin/bash

# LMDB Microservices - Start Infrastructure Script
# This script starts all required infrastructure services using Docker Compose

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$SCRIPT_DIR/../docker"

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  LMDB Microservices - Infrastructure${NC}"
ENABLE_TUNNEL=false
for arg in "$@"; do
    if [ "$arg" == "--tunnel" ] || [ "$arg" == "--live" ]; then
        ENABLE_TUNNEL=true
    fi
done

# Detect container runtime (Docker or Podman)
CONTAINER_RUNTIME=""
COMPOSE_CMD=""

if command -v docker &> /dev/null && docker compose version &> /dev/null; then
    CONTAINER_RUNTIME="docker"
    COMPOSE_CMD="docker compose"
    echo -e "${GREEN}✓ Docker detected${NC}"
elif command -v podman &> /dev/null && podman compose version &> /dev/null 2>&1; then
    CONTAINER_RUNTIME="podman"
    COMPOSE_CMD="podman compose"
    echo -e "${GREEN}✓ Podman detected${NC}"
elif command -v podman-compose &> /dev/null; then
    CONTAINER_RUNTIME="podman"
    COMPOSE_CMD="podman-compose"
    echo -e "${GREEN}✓ Podman Compose detected${NC}"
else
    echo -e "${RED}❌ Neither Docker Compose nor Podman Compose is available!${NC}"
    echo "Please install Docker: https://docs.docker.com/get-docker/"
    echo "Or install Podman: https://podman.io/getting-started/installation"
    exit 1
fi

echo -e "${GREEN}✓ Container runtime ready${NC}"
echo ""

# Loads TMDB_API_KEY etc. and exports every other .env var so the summary
# printed at the end reflects any port/credential overrides.
source "$SCRIPT_DIR/env.sh"
echo ""

# Change to docker directory
cd "$DOCKER_DIR"

# Both files together bring up the app stack plus the ELK overlay
# (Elasticsearch/Logstash/Kibana/Filebeat) in one `up`.
COMPOSE_FILES="-f docker-compose.yml -f docker-compose.elk.yml"

# Pull images first (skip for build services like discovery-service)
# --profile dev-tools brings up Adminer/Mongo Express/Redis Commander too —
# this script is the local dev entry point; docker-compose.prod.yml is the
# profile-free, tools-excluded path (#29).
echo -e "${BLUE}📥 Pulling container images...${NC}"
$COMPOSE_CMD $COMPOSE_FILES --profile dev-tools pull || echo -e "${YELLOW}⚠ Some images may need to be built${NC}"

echo ""
echo -e "${BLUE}🚀 Starting infrastructure services (incl. ELK)...${NC}"
$COMPOSE_CMD $COMPOSE_FILES --profile dev-tools up -d --build

echo ""
echo -e "${BLUE}⏳ Waiting for services to be healthy...${NC}"
sleep 5

# Check service health
echo ""
echo -e "${BLUE}📊 Service Status:${NC}"
$COMPOSE_CMD $COMPOSE_FILES ps

# ---------------------------------------------------------------------------
# Frontend (frontend/lmdb) has no compose service — it's a plain
# background npm process, logged to /tmp and stopped by
# stop-infrastructure.sh via pkill.
# ---------------------------------------------------------------------------
FRONTEND_DIR="$SCRIPT_DIR/../../frontend/lmdb"
FRONTEND_LOG="/tmp/lmdb-frontend.log"

echo ""
if [ ! -d "$FRONTEND_DIR" ]; then
    echo -e "${YELLOW}⚠  Frontend directory not found at $FRONTEND_DIR — skipping.${NC}"
elif ! command -v npm &> /dev/null; then
    echo -e "${YELLOW}⚠  npm not found on PATH — skipping frontend startup. Install Node.js 24.x (NVM) and run:${NC}"
    echo "     cd frontend/lmdb && npm install && npm start"
elif pgrep -f "vite" > /dev/null || pgrep -f "react-scripts start" > /dev/null; then
    echo -e "${GREEN}✓ Frontend dev server already running.${NC}"
else
    # .env.local is Vite's gitignored local-override file — point it at this
    # stack's gateway so the app doesn't fall back to the real TMDB API.
    if [ ! -f "$FRONTEND_DIR/.env.local" ]; then
        echo "VITE_API_URL=http://localhost:${API_GATEWAY_PORT:-8080}" > "$FRONTEND_DIR/.env.local"
        echo "REACT_APP_API_URL=http://localhost:${API_GATEWAY_PORT:-8080}" >> "$FRONTEND_DIR/.env.local"
        echo -e "${GREEN}✓ Created frontend/lmdb/.env.local (VITE_API_URL=http://localhost:${API_GATEWAY_PORT:-8080})${NC}"
    fi

    if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
        echo -e "${BLUE}📦 Installing frontend dependencies (first run only)...${NC}"
        (cd "$FRONTEND_DIR" && npm install) || echo -e "${YELLOW}⚠ npm install failed — start the frontend manually.${NC}"
    fi

    echo -e "${BLUE}💻 Starting frontend (React) dev server on port 3000...${NC}"
    (cd "$FRONTEND_DIR" && BROWSER=none nohup npm start > "$FRONTEND_LOG" 2>&1 &)
    echo -e "${GREEN}✓ Frontend launch triggered — log: $FRONTEND_LOG${NC}"
fi

if [ "$ENABLE_TUNNEL" = true ]; then
    echo ""
    "$SCRIPT_DIR/start-tunnel.sh"
fi

echo ""
echo -e "${GREEN}✅ Infrastructure started successfully!${NC}"
echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  Service Access Information${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo -e "${GREEN}Databases:${NC}"
echo "  PostgreSQL:  localhost:5432"
echo "  MongoDB:     localhost:27017"
echo "  Redis:       localhost:6379"
echo "  MinIO API:   localhost:9000"
echo ""
echo -e "${GREEN}Management UIs:${NC}"
echo "  Adminer (PostgreSQL):   http://localhost:${ADMINER_PORT:-9081}"
echo "  Mongo Express (MongoDB): http://localhost:${MONGO_EXPRESS_PORT:-9082}"
echo "  Redis Commander:        http://localhost:${REDIS_COMMANDER_PORT:-9083}"
echo "  MinIO Console:          http://localhost:${MINIO_CONSOLE_PORT:-9001}"
echo ""
echo -e "${GREEN}Infrastructure Services:${NC}"
echo "  Discovery Service (Eureka): http://localhost:${DISCOVERY_SERVICE_PORT:-8761}"
echo "  API Gateway:                http://localhost:${API_GATEWAY_PORT:-8080}"
echo ""
echo -e "${GREEN}Observability:${NC}"
echo "  Kibana:                 http://localhost:${KIBANA_PORT:-5601}"
echo "  Elasticsearch:           http://localhost:${ELASTICSEARCH_PORT:-9200}"
echo "  Gateway metrics:         http://localhost:${API_GATEWAY_PORT:-8080}/actuator/prometheus"
echo "  (Prometheus/Grafana are provisioned via the minikube/k8s monitoring"
echo "   stack, not this docker-compose stack — see infrastructure/kubernetes/monitoring)"
echo ""
echo -e "${GREEN}Frontend:${NC}"
echo "  LMDB app:            http://localhost:3000"
echo "  Frontend log:            tail -f $FRONTEND_LOG"
echo ""
echo -e "${GREEN}Credentials (from environment / .env):${NC}"
echo "  PostgreSQL:  ${POSTGRES_USER:-admin} / ${POSTGRES_PASSWORD:-[set in .env]}"
echo "  MongoDB:     ${MONGO_ROOT_USER:-admin} / ${MONGO_ROOT_PASSWORD:-[set in .env]}"
echo "  Redis:       ${REDIS_PASSWORD:-[set in .env]}"
echo "  MinIO:       ${MINIO_ROOT_USER:-minioadmin} / ${MINIO_ROOT_PASSWORD:-[set in .env]}"
echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${YELLOW}Useful Commands:${NC}"
echo "  View logs:       $COMPOSE_CMD $COMPOSE_FILES logs -f [service]"
echo "  Stop everything: ./stop-infrastructure.sh"
echo "  Restart:         $COMPOSE_CMD $COMPOSE_FILES restart [service]"
echo "  Status:          $COMPOSE_CMD $COMPOSE_FILES ps"
echo -e "${BLUE}================================================${NC}"
echo ""

