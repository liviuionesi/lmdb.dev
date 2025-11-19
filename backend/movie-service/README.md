# Movie Service

Spring Boot microservice for movie discovery, search, and details with TMDB API integration.

## Overview

The Movie Service provides comprehensive movie information including:
- Movie discovery with filters (genre, year, rating)
- Full-text search
- Detailed movie information
- Cast and crew information
- Trailers and videos
- Similar movies and recommendations
- Genre listings

**Port:** 8081

## Features

### Core Functionality
- ✅ TMDB API integration (OpenFeign)
- ✅ Hybrid caching strategy (Redis + MongoDB)
- ✅ Service discovery (Eureka)
- ✅ Centralized configuration (Config Server)
- ✅ API documentation (OpenAPI/Swagger)
- ✅ Request rate limiting
- ✅ Circuit breaker pattern
- ✅ Distributed tracing

### Caching Strategy

**3-Tier Hybrid Caching:**
1. **Redis Cache** (L1): 5-minute TTL for fast lookups
2. **MongoDB** (L2): Long-term storage for offline capability
3. **TMDB API** (Source): Fetched only on cache miss

**Flow:**
```
Request → Check Redis → Check MongoDB → Fetch from TMDB → Store in MongoDB + Redis → Response
```

## Prerequisites

### Required Services
1. **MongoDB 8.0** - Movie data storage
2. **Redis 7.4** - Caching layer
3. **Eureka Server** (port 8761) - Service discovery
4. **Config Server** (port 8888) - Configuration management
5. **TMDB API Key** - Register at https://www.themoviedb.org/settings/api

### Environment Variables
```bash
export TMDB_API_KEY="your_tmdb_api_key_here"
export MONGODB_URI="mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
```

## Setup Instructions

### 1. Start Infrastructure Services

```bash
# Start databases and infrastructure
cd infrastructure/docker
docker-compose up -d mongodb redis

# Start Eureka Server
cd backend/discovery-service
../../gradlew bootRun

# Start Config Server
cd backend/config-service
../../gradlew bootRun
```

### 2. Configure TMDB API Key

Get your API key from TMDB:
1. Register at https://www.themoviedb.org/signup
2. Go to Settings → API → Request an API Key
3. Select "Developer" and fill in the form
4. Copy your API Key (v3 auth)

Set the environment variable:
```bash
export TMDB_API_KEY="your_api_key_here"
```

### 3. Build and Run

```bash
# From project root
./gradlew :backend:movie-service:build

# Run the service
./gradlew :backend:movie-service:bootRun

# Or run the JAR directly
java -jar backend/movie-service/build/libs/movie-service-1.0.0-SNAPSHOT.jar
```

## API Endpoints

### Movies
- `GET /api/v1/movies/{id}` - Get movie details
- `GET /api/v1/movies/discover` - Discover movies with filters
- `GET /api/v1/movies/search` - Search movies
- `GET /api/v1/movies/popular` - Popular movies
- `GET /api/v1/movies/top-rated` - Top-rated movies
- `GET /api/v1/movies/trending` - Trending movies
- `GET /api/v1/movies/{id}/videos` - Movie trailers/videos
- `GET /api/v1/movies/{id}/credits` - Cast and crew
- `GET /api/v1/movies/{id}/similar` - Similar movies
- `GET /api/v1/movies/{id}/recommendations` - Recommendations

### Genres
- `GET /api/v1/genres` - Get all genres

## Testing

### Via API Gateway (Recommended)
```bash
# Through gateway (port 8080)
curl http://localhost:8080/api/v1/movies/550
curl http://localhost:8080/api/v1/movies/discover?genreId=28&page=1
curl http://localhost:8080/api/v1/movies/search?query=Inception
curl http://localhost:8080/api/v1/genres
```

### Direct Access
```bash
# Direct to service (port 8081)
curl http://localhost:8081/api/v1/movies/550
curl http://localhost:8081/api/v1/movies/popular
```

### Using Postman
Import the collection: `postman/Movie-Service-API.postman_collection.json`

The collection includes:
- Health checks
- All movie endpoints
- Rate limiting tests
- Request tracing examples

## Monitoring

### Health Check
```bash
curl http://localhost:8081/actuator/health
```

### Metrics
```bash
curl http://localhost:8081/actuator/metrics
```

### API Documentation
- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI JSON: http://localhost:8081/v3/api-docs

### Service Discovery
- Eureka Dashboard: http://localhost:8761
- Check if `MOVIE-SERVICE` is registered

## Request Tracing

The service supports distributed tracing for debugging request flow:

**Flow:**
```
Client → API Gateway → Eureka → Movie Service → TMDB API
         (8080)       (8761)     (8081)
```

**Trace logs:**
- All requests log with trace IDs
- Check logs for request flow
- Monitor MongoDB and Redis hits/misses

## Caching Verification

### Check Redis
```bash
# Connect to Redis
redis-cli

# List cached keys
KEYS *

# Get cached movie
GET movies::550

# Check TTL
TTL movies::550
```

### Check MongoDB
```bash
# Connect to MongoDB
mongosh mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin

# List movies
db.movies.find().limit(5)

# Find specific movie
db.movies.findOne({tmdbId: 550})

# Count movies
db.movies.countDocuments()
```

## Performance

### Expected Response Times
- **Redis Cache Hit:** < 50ms
- **MongoDB Cache Hit:** < 100ms
- **TMDB API Call:** < 500ms

### Rate Limiting
- **Per IP:** 10 requests/second (burst: 20)
- Configured at API Gateway level

## Troubleshooting

### Service Not Registering with Eureka
```bash
# Check Eureka connection
curl http://localhost:8761/eureka/apps/MOVIE-SERVICE

# Verify Eureka URL in application.yml
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

### TMDB API Key Not Working
```bash
# Test API key directly
curl "https://api.themoviedb.org/3/movie/550?api_key=YOUR_KEY"

# Check environment variable
echo $TMDB_API_KEY

# Check logs for API errors
tail -f logs/movie-service.log | grep TMDB
```

### MongoDB Connection Issues
```bash
# Test MongoDB connection
mongosh mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin

# Check if MongoDB is running
docker ps | grep mongo

# Check service logs
tail -f logs/movie-service.log | grep MongoDB
```

### Redis Connection Issues
```bash
# Test Redis connection
redis-cli ping

# Check if Redis is running
docker ps | grep redis

# Check service logs
tail -f logs/movie-service.log | grep Redis
```

## Development

### Run Tests
```bash
# Unit tests
./gradlew :backend:movie-service:test

# Integration tests
./gradlew :backend:movie-service:integrationTest

# With coverage
./gradlew :backend:movie-service:test jacocoTestReport
```

### Code Coverage
Open: `backend/movie-service/build/reports/jacoco/test/html/index.html`

## Configuration

### Application Profiles
- **default**: Uses Config Server
- **dev**: Local development configuration
- **prod**: Production configuration

### Config Server
Configuration is managed by Spring Cloud Config Server.

Location: `backend/config-service/src/main/resources/config/movie-service.yml`

## Architecture

### Technology Stack
- **Framework:** Spring Boot 3.5.8-SNAPSHOT
- **Language:** Java 25
- **Build Tool:** Gradle 9.2.0
- **Database:** MongoDB 8.0
- **Cache:** Redis 7.4-alpine
- **API Client:** OpenFeign
- **Service Discovery:** Netflix Eureka
- **Documentation:** SpringDoc OpenAPI
- **Mapping:** MapStruct 1.6.3

### Dependencies
See `build.gradle` for complete dependency list.

## Related Documentation

- [Architecture Documentation](../../docs/architecture/ARCHITECTURE.md)
- [API Gateway Documentation](../api-gateway/README.md)
- [Config Service Documentation](../config-service/README.md)
- [Docker Infrastructure](../../infrastructure/docker/README.md)

## License

Copyright © 2025 Filmpire Team
