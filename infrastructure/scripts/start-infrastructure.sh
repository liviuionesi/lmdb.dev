#!/bin/bash

# Filmpire Microservices - Start Infrastructure Script
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
echo -e "${BLUE}  Filmpire Microservices - Infrastructure${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

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

# Check if .env file exists
if [ ! -f "$DOCKER_DIR/.env" ]; then
    echo -e "${YELLOW}⚠  No .env file found. Using default values.${NC}"
    echo -e "${YELLOW}   Copy env.example to .env to customize configuration.${NC}"
    echo ""
fi

# Change to docker directory
cd "$DOCKER_DIR"

# Pull images first (skip for build services like discovery-service)
echo -e "${BLUE}📥 Pulling container images...${NC}"
$COMPOSE_CMD pull || echo -e "${YELLOW}⚠ Some images may need to be built${NC}"

echo ""
echo -e "${BLUE}🚀 Starting infrastructure services...${NC}"
$COMPOSE_CMD up -d --build

echo ""
echo -e "${BLUE}⏳ Waiting for services to be healthy...${NC}"
sleep 5

# Check service health
echo ""
echo -e "${BLUE}📊 Service Status:${NC}"
$COMPOSE_CMD ps

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
echo ""
echo -e "${GREEN}Credentials (default):${NC}"
echo "  PostgreSQL:  admin / admin123"
echo "  MongoDB:     admin / admin123"
echo "  Redis:       redis123"
echo "  MinIO:       minioadmin / minioadmin123"
echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${YELLOW}Useful Commands:${NC}"
echo "  View logs:       $COMPOSE_CMD logs -f [service]"
echo "  Stop services:   ./stop-infrastructure.sh"
echo "  Restart:         $COMPOSE_CMD restart [service]"
echo "  Status:          $COMPOSE_CMD ps"
echo -e "${BLUE}================================================${NC}"
echo ""

