#!/bin/bash
# Start Movie Service for local development

export TMDB_API_KEY="ae8fa6e23866cb34a49337b233547834"
export REDIS_PASSWORD="redis123"

cd /home/liviu/Desktop/filmpire-microservices

echo "Starting Movie Service..."
echo "TMDB API Key: ${TMDB_API_KEY:0:10}..."
echo "Redis Password: redis123"
echo ""

./gradlew :backend:movie-service:bootRun

