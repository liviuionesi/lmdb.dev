#!/bin/bash
# Start all Filmpire services for local testing

export TMDB_API_KEY="ae8fa6e23866cb34a49337b233547834"
export REDIS_PASSWORD="redis123"

cd /home/liviu/Desktop/filmpire-microservices

echo "🚀 Starting Filmpire Microservices..."
echo ""

# Start Discovery Service
echo "📍 Starting Discovery Service (Eureka) on port 8761..."
./gradlew :backend:discovery-service:bootRun > /tmp/discovery-service.log 2>&1 &
DISCOVERY_PID=$!

sleep 30
echo "✓ Discovery Service started"

# Start Config Service
echo "⚙️  Starting Config Service on port 8888..."
./gradlew :backend:config-service:bootRun > /tmp/config-service.log 2>&1 &
CONFIG_PID=$!

sleep 25
echo "✓ Config Service started"

# Start API Gateway
echo "🌐 Starting API Gateway on port 8080..."
./gradlew :backend:api-gateway:bootRun > /tmp/api-gateway.log 2>&1 &
GATEWAY_PID=$!

sleep 30
echo "✓ API Gateway started"

# Start Movie Service
echo "🎬 Starting Movie Service on port 8081..."
./gradlew :backend:movie-service:bootRun > /tmp/movie-service.log 2>&1 &
MOVIE_PID=$!

sleep 40
echo "✓ Movie Service started"

echo ""
echo "=================================="
echo "✅ All Services Started!"
echo "=================================="
echo ""
echo "Service URLs:"
echo "  Eureka Dashboard:  http://localhost:8761"
echo "  API Gateway:       http://localhost:8080"
echo "  Movie Service:     http://localhost:8081"
echo "  Swagger UI:        http://localhost:8081/swagger-ui.html"
echo ""
echo "Quick Test:"
echo "  curl http://localhost:8080/api/v1/movies/550 | jq .data.title"
echo ""
echo "Logs:"
echo "  tail -f /tmp/discovery-service.log"
echo "  tail -f /tmp/config-service.log"
echo "  tail -f /tmp/api-gateway.log"
echo "  tail -f /tmp/movie-service.log"
echo ""
echo "To stop all services:"
echo "  pkill -f 'bootRun'"
echo ""

