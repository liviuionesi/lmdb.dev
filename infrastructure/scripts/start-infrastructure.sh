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

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed!${NC}"
    echo "Please install Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# Check if Docker Compose is available
if ! docker compose version &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not available!${NC}"
    echo "Please install Docker Compose: https://docs.docker.com/compose/install/"
    exit 1
fi

echo -e "${GREEN}✓ Docker installed${NC}"
echo -e "${GREEN}✓ Docker Compose available${NC}"
echo ""

# Check if .env file exists
if [ ! -f "$DOCKER_DIR/.env" ]; then
    echo -e "${YELLOW}⚠  No .env file found. Using default values.${NC}"
    echo -e "${YELLOW}   Copy env.example to .env to customize configuration.${NC}"
    echo ""
fi

# Change to docker directory
cd "$DOCKER_DIR"

# Pull images first
echo -e "${BLUE}📥 Pulling Docker images...${NC}"
docker compose pull

echo ""
echo -e "${BLUE}🚀 Starting infrastructure services...${NC}"
docker compose up -d

echo ""
echo -e "${BLUE}⏳ Waiting for services to be healthy...${NC}"
sleep 5

# Check service health
echo ""
echo -e "${BLUE}📊 Service Status:${NC}"
docker compose ps

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
echo "  Adminer (PostgreSQL):   http://localhost:8081"
echo "  Mongo Express (MongoDB): http://localhost:8082"
echo "  Redis Commander:        http://localhost:8083"
echo "  MinIO Console:          http://localhost:9001"
echo ""
echo -e "${GREEN}Credentials (default):${NC}"
echo "  PostgreSQL:  admin / admin123"
echo "  MongoDB:     admin / admin123"
echo "  Redis:       redis123"
echo "  MinIO:       minioadmin / minioadmin123"
echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${YELLOW}Useful Commands:${NC}"
echo "  View logs:       docker compose logs -f [service]"
echo "  Stop services:   ./stop-infrastructure.sh"
echo "  Restart:         docker compose restart [service]"
echo "  Status:          docker compose ps"
echo -e "${BLUE}================================================${NC}"
echo ""

