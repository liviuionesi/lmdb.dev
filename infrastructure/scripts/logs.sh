#!/bin/bash

# LMDB Microservices - Tail Infrastructure Logs
# Follows logs for all services, or one service if a name is given.

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

# Detect container runtime (Docker or Podman)
COMPOSE_CMD=""
if command -v docker &> /dev/null && docker compose version &> /dev/null; then
    COMPOSE_CMD="docker compose"
elif command -v podman &> /dev/null && podman compose version &> /dev/null 2>&1; then
    COMPOSE_CMD="podman compose"
elif command -v podman-compose &> /dev/null; then
    COMPOSE_CMD="podman-compose"
else
    echo -e "${RED}❌ Neither Docker Compose nor Podman Compose is available!${NC}"
    exit 1
fi

cd "$DOCKER_DIR"

# Must match start-infrastructure.sh's compose file set, or the ELK-overlay
# containers (elasticsearch/logstash/kibana/filebeat) won't be found.
COMPOSE_FILES="-f docker-compose.yml -f docker-compose.elk.yml"

SERVICE="$1"
if [ "$SERVICE" == "frontend" ]; then
    echo -e "${BLUE}📜 Following frontend log... (Ctrl+C to stop)${NC}"
    tail -f /tmp/lmdb-frontend.log
elif [ -n "$SERVICE" ]; then
    echo -e "${BLUE}📜 Following logs for ${GREEN}${SERVICE}${BLUE}... (Ctrl+C to stop)${NC}"
    $COMPOSE_CMD $COMPOSE_FILES --profile dev-tools logs -f --tail=200 "$SERVICE"
else
    echo -e "${BLUE}📜 Following logs for all services... (Ctrl+C to stop)${NC}"
    echo -e "${YELLOW}   Tip: ./logs.sh <service-name> to follow just one, or ./logs.sh frontend${NC}"
    $COMPOSE_CMD $COMPOSE_FILES --profile dev-tools logs -f --tail=100
fi
