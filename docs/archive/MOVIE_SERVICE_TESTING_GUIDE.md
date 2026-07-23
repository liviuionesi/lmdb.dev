# Movie Service Testing Guide

Complete guide for testing the Movie Service with full request tracing through the infrastructure.

## 🚀 Quick Start

### Prerequisites
1. **TMDB API Key** - Get from https://www.themoviedb.org/settings/api
2. **Docker/Podman** - For MongoDB and Redis
3. **Java 25** - JDK installed
4. **Gradle** - via wrapper

### Environment Setup
```bash
# Set TMDB API Key (REQUIRED!)
export TMDB_API_KEY="your_api_key_here"

# Optional overrides
export MONGODB_URI="mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
```

## 📦 Step-by-Step Startup

### Step 1: Start Infrastructure (Databases)
```bash
cd infrastructure/docker
docker-compose up -d mongodb redis

# Verify running
docker ps | grep -E "mongo|redis"

# Expected output:
# filmpire-mongo   Up (healthy)   0.0.0.0:27017->27017/tcp
# filmpire-redis   Up (healthy)   0.0.0.0:6379->6379/tcp
```

### Step 2: Start Discovery Service (Eureka)
```bash
# Terminal 1
cd /home/liviu/Desktop/filmpire-microservices
./gradlew :backend:discovery-service:bootRun

# Wait for:
# "Started DiscoveryServiceApplication"
# "Eureka dashboard available at: http://localhost:8761"
```

**Verify:** Open http://localhost:8761 - Should see Eureka Dashboard

### Step 3: Start Config Service
```bash
# Terminal 2
cd /home/liviu/Desktop/filmpire-microservices
./gradlew :backend:config-service:bootRun

# Wait for:
# "Started ConfigServiceApplication"
# "Tomcat started on port(s): 8888"
```

**Verify:**
```bash
curl http://localhost:8888/actuator/health
# Expected: {"status":"UP"}
```

### Step 4: Start Movie Service
```bash
# Terminal 3
cd /home/liviu/Desktop/filmpire-microservices
./gradlew :backend:movie-service:bootRun

# Wait for:
# "Started MovieServiceApplication"
# "Tomcat started on port(s): 8081"
# "DiscoveryClient_MOVIE-SERVICE... registering service..."
```

**Verify Registration:**
```bash
curl http://localhost:8761/eureka/apps/MOVIE-SERVICE
# Should return XML with service details
```

### Step 5: Start API Gateway
```bash
# Terminal 4
cd /home/liviu/Desktop/filmpire-microservices
./gradlew :backend:api-gateway:bootRun

# Wait for:
# "Started ApiGatewayApplication"
# "Netty started on port 8080"
```

**Verify:**
```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

## 🧪 Testing Request Flow

### Infrastructure Verification

#### 1. Check All Services are Registered
```bash
# Open Eureka Dashboard
open http://localhost:8761

# Should see:
# - API-GATEWAY
# - CONFIG-SERVICE  
# - MOVIE-SERVICE
```

#### 2. Check MongoDB Connection
```bash
mongosh mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin

# In mongosh:
show dbs
use filmpire
show collections

# Exit
exit
```

#### 3. Check Redis Connection
```bash
redis-cli ping
# Expected: PONG
```

### Request Tracing Examples

#### Example 1: Get Movie Details (Fight Club - ID: 550)

**Direct to Movie Service:**
```bash
curl -v http://localhost:8081/api/v1/movies/550 | jq
```

**Through API Gateway (RECOMMENDED):**
```bash
curl -v http://localhost:8080/api/v1/movies/550 | jq
```

**What Happens:**
1. **Gateway** (8080) receives request
2. **Gateway** queries **Eureka** (8761) for MOVIE-SERVICE instances
3. **Gateway** routes to **Movie Service** (8081)
4. **Movie Service** checks **Redis** cache (localhost:6379)
5. If cache miss, checks **MongoDB** (localhost:27017)
6. If not in MongoDB, fetches from **TMDB API**
7. Stores in MongoDB + Redis
8. Returns response through Gateway

**Expected Response:**
```json
{
  "success": true,
  "message": "Movie retrieved successfully",
  "data": {
    "tmdbId": 550,
    "title": "Fight Club",
    "overview": "A ticking-time-bomb insomniac...",
    "voteAverage": 8.4,
    ...
  },
  "statusCode": 200,
  "timestamp": "2025-11-17T..."
}
```

**Verify Caching:**
```bash
# Check Redis cache
redis-cli
GET "movies::550"

# Check MongoDB
mongosh mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin
db.movies.findOne({tmdbId: 550})
```

#### Example 2: Discover Action Movies

```bash
curl "http://localhost:8080/api/v1/movies/discover?genreId=28&page=1&size=10" | jq
```

**Expected:**
- Paginated list of action movies (genreId=28)
- Response includes: content, pageNumber, pageSize, totalElements, totalPages

#### Example 3: Search Movies

```bash
curl "http://localhost:8080/api/v1/movies/search?query=Inception&page=1" | jq
```

#### Example 4: Get All Genres

```bash
curl http://localhost:8080/api/v1/genres | jq
```

**Expected:**
```json
{
  "success": true,
  "data": [
    {"id": 28, "name": "Action"},
    {"id": 12, "name": "Adventure"},
    {"id": 16, "name": "Animation"},
    ...
  ]
}
```

### Monitoring Request Flow

#### Gateway Logs (Terminal 4)
Look for:
```
RoutePredicateHandlerMapping : Mapped [/api/v1/movies/**]
LoadBalancerClientFilter : LoadBalancer URI lb://movie-service
```

#### Movie Service Logs (Terminal 3)
Look for:
```
MovieService : Fetching movie with TMDB ID: 550
MovieService : Movie not in MongoDB, fetching from TMDB: 550
MovieRepository : Saved movie to MongoDB: Fight Club
```

#### Redis Cache Monitoring
```bash
# Terminal 5 - Monitor Redis
redis-cli monitor

# You'll see:
# "GET" "movies::550"
# "SETEX" "movies::550" "300" "..."
```

#### MongoDB Operations
```bash
# Terminal 6 - MongoDB profiling
mongosh mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin

# Enable profiling
db.setProfilingLevel(2)

# Check operations
db.system.profile.find().limit(5).pretty()
```

## 🔍 Advanced Testing

### Test Caching Behavior

**First Request (Cache Miss):**
```bash
time curl -s http://localhost:8080/api/v1/movies/27205 | jq .data.title
# Expected: ~500ms (TMDB API call)
# Output: "Inception"
```

**Second Request (Cache Hit from Redis):**
```bash
time curl -s http://localhost:8080/api/v1/movies/27205 | jq .data.title
# Expected: ~50ms (Redis cache)
# Output: "Inception"
```

**Clear Redis Cache:**
```bash
redis-cli FLUSHALL
```

**Third Request (MongoDB Cache Hit):**
```bash
time curl -s http://localhost:8080/api/v1/movies/27205 | jq .data.title
# Expected: ~100ms (MongoDB lookup)
# Output: "Inception"
```

### Test Rate Limiting

Send 25 rapid requests to test Gateway rate limiting (10 req/sec, burst 20):

```bash
# Bash loop
for i in {1..25}; do
  echo "Request $i:"
  curl -w "\nStatus: %{http_code}\n" -s http://localhost:8080/api/v1/movies/popular | jq -c '{success, message}'
  sleep 0.05
done
```

**Expected:**
- First 20 requests: 200 OK
- Requests 21-25: 429 Too Many Requests

### Test All Endpoints

```bash
# 1. Popular movies
curl http://localhost:8080/api/v1/movies/popular?page=1 | jq .data.content[0].title

# 2. Top-rated movies
curl http://localhost:8080/api/v1/movies/top-rated?page=1 | jq .data.content[0].title

# 3. Trending movies
curl http://localhost:8080/api/v1/movies/trending?timeWindow=week | jq .data.content[0].title

# 4. Movie videos (trailers)
curl http://localhost:8080/api/v1/movies/550/videos | jq .data[0].name

# 5. Movie credits (cast)
curl http://localhost:8080/api/v1/movies/550/credits | jq .data.cast[0].name

# 6. Similar movies
curl http://localhost:8080/api/v1/movies/550/similar | jq .data.content[0].title

# 7. Recommendations
curl http://localhost:8080/api/v1/movies/550/recommendations | jq .data.content[0].title
```

## 📊 Using Postman

### Import Collection
1. Open Postman
2. File → Import
3. Select: `postman/Movie-Service-API.postman_collection.json`

### Run Tests
The collection includes 40+ requests organized in folders:
1. **Health & Discovery** - Verify all services
2. **Movie Discovery** - Test filtering
3. **Movie Search & Details** - Search functionality
4. **Movie Lists** - Popular, top-rated, trending
5. **Movie Extras** - Videos, credits, similar
6. **Genres** - Genre listings
7. **Rate Limiting** - Test limits
8. **Documentation** - OpenAPI/Swagger

### Request Tracing in Postman
Each request includes a trace ID header:
```
X-Request-ID: {{$randomUUID}}
```

Check logs for this ID to trace the request through all services.

## 🐛 Troubleshooting

### Movie Service Not Starting

**Check TMDB API Key:**
```bash
echo $TMDB_API_KEY
# Should output your API key

# Test directly:
curl "https://api.themoviedb.org/3/movie/550?api_key=$TMDB_API_KEY"
```

**Check MongoDB:**
```bash
docker ps | grep mongo
mongosh mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin
```

**Check Redis:**
```bash
docker ps | grep redis
redis-cli ping
```

### Service Not Registering with Eureka

**Check Eureka is running:**
```bash
curl http://localhost:8761/actuator/health
```

**Check Movie Service logs for:**
```
DiscoveryClient_MOVIE-SERVICE registering service...
```

**Manually check registration:**
```bash
curl http://localhost:8761/eureka/apps/MOVIE-SERVICE
```

### Gateway Not Routing

**Check Gateway routes:**
```bash
curl http://localhost:8080/actuator/gateway/routes | jq
```

**Should include:**
```json
{
  "route_id": "movie-service",
  "uri": "lb://movie-service",
  "predicate": "Paths: [/api/v1/movies/**, /api/v1/genres/**]"
}
```

### TMDB API Errors

**Error: Invalid API Key**
- Get new key from https://www.themoviedb.org/settings/api
- Make sure to copy the "API Key (v3 auth)"
- Not the "API Read Access Token"

**Error: Rate Limit Exceeded**
- TMDB has rate limits (40 requests per 10 seconds)
- Caching (Redis + MongoDB) helps avoid this
- Wait 10 seconds and try again

## ✅ Success Criteria

Your setup is working correctly if:

1. ✅ Eureka Dashboard shows: API-GATEWAY, CONFIG-SERVICE, MOVIE-SERVICE
2. ✅ `curl http://localhost:8080/api/v1/movies/550` returns Fight Club
3. ✅ `curl http://localhost:8080/api/v1/genres` returns list of genres
4. ✅ Redis contains cached movies: `redis-cli KEYS movies::*`
5. ✅ MongoDB contains movies: `db.movies.countDocuments()`
6. ✅ Second request for same movie is faster (cache hit)
7. ✅ Rate limiting works (25 rapid requests → 429 errors)

## 📖 API Documentation

Once running, access interactive API documentation:

- **Swagger UI:** http://localhost:8081/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8081/v3/api-docs

## 🎯 Next Steps

1. **Test with Postman collection** - Import and run all requests
2. **Monitor logs** - Watch request flow through infrastructure
3. **Test caching** - Observe Redis + MongoDB behavior
4. **Test rate limiting** - Send rapid requests
5. **Check Eureka** - Verify service registration
6. **Explore API docs** - Use Swagger UI

## 📝 Summary

**Infrastructure Flow:**
```
Client
  ↓
API Gateway (localhost:8080)
  ↓ (Service Discovery)
Eureka (localhost:8761)
  ↓ (Load Balance)
Movie Service (localhost:8081)
  ↓ (Cache Check)
Redis (localhost:6379) → MongoDB (localhost:27017) → TMDB API
  ↓
Response (with caching)
```

Happy Testing! 🎬

