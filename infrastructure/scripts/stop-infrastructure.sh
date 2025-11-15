#!/bin/bash

# Filmpire Microservices - Stop Infrastructure Script
# This script stops all infrastructure services

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

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  Stopping Filmpire Infrastructure${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Change to docker directory
cd "$DOCKER_DIR"

# Parse command line arguments
REMOVE_VOLUMES=false
if [ "$1" == "--volumes" ] || [ "$1" == "-v" ]; then
    REMOVE_VOLUMES=true
    echo -e "${YELLOW}⚠  Warning: This will remove all data volumes!${NC}"
    echo -e "${YELLOW}   All database data will be permanently deleted.${NC}"
    echo ""
    read -p "Are you sure? (yes/no): " -r
    echo
    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        echo -e "${BLUE}Cancelled.${NC}"
        exit 0
    fi
fi

# Stop services
echo -e "${BLUE}🛑 Stopping services...${NC}"
$COMPOSE_CMD down

if [ "$REMOVE_VOLUMES" = true ]; then
    echo ""
    echo -e "${BLUE}🗑  Removing volumes...${NC}"
    $COMPOSE_CMD down -v
    echo -e "${GREEN}✅ Services stopped and volumes removed${NC}"
else
    echo -e "${GREEN}✅ Services stopped (data preserved)${NC}"
fi

echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${YELLOW}Note: To remove all data, run:${NC}"
echo "  ./stop-infrastructure.sh --volumes"
echo -e "${BLUE}================================================${NC}"
echo ""

