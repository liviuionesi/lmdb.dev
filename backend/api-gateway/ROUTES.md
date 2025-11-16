# API Gateway Routes Documentation

Complete reference for all routes configured in the Filmpire API Gateway.

**Version:** 1.0.0  
**Last Updated:** November 16, 2025

---

## Route Configuration Summary

| Service | Route Pattern | Target URI | Rate Limit | Circuit Breaker | Fallback |
|---------|--------------|------------|-----------|-----------------|----------|
| Movie Service | `/api/v1/movies/**` | `lb://movie-service` | 10/sec (burst 20) | ✅ | `/fallback/movies` |
| User Service | `/api/v1/users/**` | `lb://user-service` | 10/sec (burst 20) | ✅ | `/fallback/users` |
| Auth Service | `/api/v1/auth/**` | `lb://user-service` | None | ✅ | `/fallback/auth` |
| Actor Service | `/api/v1/actors/**` | `lb://actor-service` | 10/sec (burst 20) | ✅ | `/fallback/actors` |
| AI Service | `/api/v1/ai/**` | `lb://ai-service` | 5/sec (burst 10) | ✅ | `/fallback/ai` |
| AI Service | `/api/v1/recommendations/**` | `lb://ai-service` | 5/sec (burst 10) | ✅ | `/fallback/ai` |
| Media Service | `/api/v1/media/**` | `lb://media-service` | 20/sec (burst 40) | ✅ | `/fallback/media` |

---

## Detailed Route Definitions

### 1. Movie Service Routes

**Route ID:** `movie-service`  
**URI Pattern:** `/api/v1/movies/**`  
**Target Service:** `movie-service` (via Eureka)  
**Authentication:** Public for GET, Required for POST/PUT/DELETE  
**Rate Limit:** 10 requests/second, burst capacity 20

**Endpoints:**
- `GET /api/v1/movies` - List all movies (paginated)
- `GET /api/v1/movies/{id}` - Get movie by ID
- `GET /api/v1/movies/search` - Search movies
- `POST /api/v1/movies` - Create new movie (auth required)
- `PUT /api/v1/movies/{id}` - Update movie (auth required)
- `DELETE /api/v1/movies/{id}` - Delete movie (auth required)

**Filters:**
- Circuit Breaker (movieServiceCircuitBreaker)
- Rate Limiter (10/sec, burst 20)

**Fallback:** Returns HTTP 503 with error message when circuit is open

---

### 2. User Service Routes

**Route ID:** `user-service`  
**URI Pattern:** `/api/v1/users/**`  
**Target Service:** `user-service` (via Eureka)  
**Authentication:** Required  
**Rate Limit:** 10 requests/second, burst capacity 20

**Endpoints:**
- `GET /api/v1/users/profile` - Get current user profile
- `PUT /api/v1/users/profile` - Update user profile
- `GET /api/v1/users/{id}` - Get user by ID
- `DELETE /api/v1/users/{id}` - Delete user account

**Filters:**
- Circuit Breaker (userServiceCircuitBreaker)
- Rate Limiter (10/sec, burst 20)

**Fallback:** Returns HTTP 503 with error message when circuit is open

---

### 3. Authentication Routes

**Route ID:** `auth-service`  
**URI Pattern:** `/api/v1/auth/**`  
**Target Service:** `user-service` (via Eureka)  
**Authentication:** Public  
**Rate Limit:** None (authentication endpoints should not be rate-limited)

**Endpoints:**
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/register` - User registration
- `POST /api/v1/auth/refresh` - Refresh JWT token
- `POST /api/v1/auth/logout` - User logout

**Filters:**
- Circuit Breaker (authServiceCircuitBreaker)
- No rate limiting

**Fallback:** Returns HTTP 503 with error message when circuit is open

---

### 4. Actor Service Routes

**Route ID:** `actor-service`  
**URI Pattern:** `/api/v1/actors/**`  
**Target Service:** `actor-service` (via Eureka)  
**Authentication:** Public for GET, Required for POST/PUT/DELETE  
**Rate Limit:** 10 requests/second, burst capacity 20

**Endpoints:**
- `GET /api/v1/actors` - List all actors (paginated)
- `GET /api/v1/actors/{id}` - Get actor by ID
- `GET /api/v1/actors/search` - Search actors
- `POST /api/v1/actors` - Create new actor (auth required)
- `PUT /api/v1/actors/{id}` - Update actor (auth required)
- `DELETE /api/v1/actors/{id}` - Delete actor (auth required)

**Filters:**
- Circuit Breaker (actorServiceCircuitBreaker)
- Rate Limiter (10/sec, burst 20)

**Fallback:** Returns HTTP 503 with error message when circuit is open

---

### 5. AI Service Routes

**Route ID:** `ai-service`  
**URI Pattern:** `/api/v1/ai/**`, `/api/v1/recommendations/**`  
**Target Service:** `ai-service` (via Eureka)  
**Authentication:** Required  
**Rate Limit:** 5 requests/second, burst capacity 10 (lower due to compute intensity)

**Endpoints:**
- `POST /api/v1/ai/recommendations` - Get AI-powered movie recommendations
- `POST /api/v1/ai/chat` - Chat with AI assistant
- `POST /api/v1/ai/transcribe` - Transcribe voice input
- `GET /api/v1/recommendations/{userId}` - Get user recommendations

**Filters:**
- Circuit Breaker (aiServiceCircuitBreaker)
- Rate Limiter (5/sec, burst 10)

**Fallback:** Returns HTTP 503 with error message when circuit is open

---

### 6. Media Service Routes

**Route ID:** `media-service`  
**URI Pattern:** `/api/v1/media/**`  
**Target Service:** `media-service` (via Eureka)  
**Authentication:** Required  
**Rate Limit:** 20 requests/second, burst capacity 40 (higher for file uploads)

**Endpoints:**
- `POST /api/v1/media/upload` - Upload media file
- `GET /api/v1/media/{id}` - Get media file
- `DELETE /api/v1/media/{id}` - Delete media file
- `GET /api/v1/media/thumbnail/{id}` - Get media thumbnail

**Filters:**
- Circuit Breaker (mediaServiceCircuitBreaker)
- Rate Limiter (20/sec, burst 40)

**Fallback:** Returns HTTP 503 with error message when circuit is open

---

## Global Filters

### 1. Dedupe Response Header Filter

Removes duplicate CORS headers to prevent browser issues.

### 2. Retry Filter

- **Retries:** 3 attempts
- **Retry On:** BAD_GATEWAY, SERVICE_UNAVAILABLE
- **Retry Methods:** GET only (idempotent)
- **Backoff:** Exponential (50ms → 100ms → 200ms)

---

## Circuit Breaker Configuration

### Default Configuration

- **Sliding Window Size:** 10 calls
- **Minimum Number of Calls:** 5
- **Permitted Calls in Half-Open:** 3
- **Wait Duration in Open State:** 10 seconds
- **Failure Rate Threshold:** 50%
- **Slow Call Duration Threshold:** 2 seconds
- **Slow Call Rate Threshold:** 50%

### Circuit Breaker Instances

All services use the default configuration:
- movieServiceCircuitBreaker
- userServiceCircuitBreaker
- authServiceCircuitBreaker
- actorServiceCircuitBreaker
- aiServiceCircuitBreaker
- mediaServiceCircuitBreaker

---

## Rate Limiting Details

### Rate Limiter Implementation

- **Backend:** Redis (Lettuce client)
- **Algorithm:** Token bucket
- **Key Resolution:** IP address + route

### Global Rate Limiting

**All Routes:**
- **100 requests/second per IP** (hard cap)
- Implemented via `GlobalRateLimitFilter`
- Redis-backed
- Returns 429 if exceeded

### Per-Service Rate Limiting

| Service | Replenish Rate | Burst Capacity | Reason |
|---------|----------------|----------------|---------|
| Movie Service | 10/sec | 20 | Standard API calls |
| User Service | 10/sec | 20 | Standard API calls |
| **Auth Service** | **5/sec** 🔒 | **10** | **Prevent brute force attacks** |
| Actor Service | 10/sec | 20 | Standard API calls |
| AI Service | 5/sec | 10 | Compute-intensive operations |
| Media Service | 20/sec | 40 | File upload/download |

**Key Feature:** All rate limiting is IP-based (using X-Forwarded-For header)

### Rate Limit Response

When rate limit is exceeded:
- **HTTP Status:** 429 Too Many Requests
- **Response Headers:**
  - `X-RateLimit-Remaining`: Tokens remaining
  - `X-RateLimit-Burst-Capacity`: Burst capacity
  - `X-RateLimit-Replenish-Rate`: Replenish rate

---

## CORS Configuration

### Allowed Origins

- `http://localhost:3000` (React default)
- `http://localhost:3001` (Alternative React port)
- `http://localhost:5173` (Vite default)
- `http://localhost:5174` (Alternative Vite port)

### Allowed Methods

`GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`

### Allowed Headers

All headers (`*`)

### Exposed Headers

- `Authorization`
- `Access-Control-Allow-Origin`

### Credentials

Allowed (`allow-credentials: true`)

### Max Age

3600 seconds (1 hour)

---

## Authentication & Authorization

### Public Endpoints (No Auth Required)

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/movies/**`
- `GET /api/v1/actors/**`
- `GET /actuator/**`
- `GET /fallback/**`

### Protected Endpoints (Auth Required)

All other endpoints require a valid JWT token in the `Authorization` header:

```
Authorization: Bearer <jwt-token>
```

### User Information Headers

The gateway adds the following headers to requests for downstream services:

- `X-User-Id`: User's unique identifier
- `X-Username`: User's username
- `X-User-Roles`: Comma-separated list of roles

---

## Error Responses

### Standard Error Format

```json
{
  "status": 503,
  "errorCode": "SERVICE_UNAVAILABLE",
  "message": "Movie Service is temporarily unavailable. Please try again later.",
  "path": "/api/v1/movies/123",
  "timestamp": "2025-11-16T12:00:00"
}
```

### Common HTTP Status Codes

| Code | Meaning | When It Occurs |
|------|---------|---------------|
| 401 | Unauthorized | Invalid or missing JWT token |
| 403 | Forbidden | Valid token but insufficient permissions |
| 404 | Not Found | Resource not found |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected error in gateway |
| 503 | Service Unavailable | Circuit breaker open or service down |

---

## Monitoring Routes

### Actuator Endpoints

- `GET /actuator/health` - Health check
- `GET /actuator/info` - Application information
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/prometheus` - Prometheus metrics
- `GET /actuator/gateway/routes` - List all gateway routes
- `GET /actuator/gateway/filters` - List all gateway filters

---

## Testing Routes

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Test Public Endpoint

```bash
curl http://localhost:8080/api/v1/movies
```

### Test Protected Endpoint

```bash
curl -H "Authorization: Bearer <jwt-token>" \
     http://localhost:8080/api/v1/users/profile
```

### Test Rate Limiting

```bash
# Send 25 requests quickly to trigger rate limit
for i in {1..25}; do
  curl http://localhost:8080/api/v1/movies
done
```

---

**Version:** 1.0.0  
**Last Updated:** November 16, 2025

