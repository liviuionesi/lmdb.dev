# Filmpire API Gateway

Spring Cloud Gateway serving as the single entry point for all client requests in the Filmpire microservices platform.

**Port:** 8080  
**Version:** 1.0.0

---

## 📋 Overview

The API Gateway provides intelligent routing, security, rate limiting, and circuit breaking for all microservices in the Filmpire platform.

---

## 🎯 Responsibilities

- **Request Routing** - Intelligent routing to microservices based on path patterns
- **Authentication & Authorization** - JWT-based security with role-based access control
- **Rate Limiting** - Redis-backed rate limiting to prevent API abuse
- **Circuit Breaker** - Resilience4j circuit breaker pattern for fault tolerance
- **CORS Configuration** - Cross-origin resource sharing for frontend applications
- **Request/Response Logging** - Comprehensive logging for monitoring and debugging
- **Load Balancing** - Eureka-based service discovery and load balancing
- **Error Handling** - Standardized error responses across all services

---

## 🛠 Technology Stack

- **Spring Cloud Gateway** - Reactive gateway framework
- **Spring Security** - Security framework for authentication/authorization
- **JWT (JJWT)** - JSON Web Token handling
- **Redis** - Rate limiting and caching
- **Resilience4j** - Circuit breaker and fault tolerance
- **Eureka Client** - Service discovery
- **Spring Boot Actuator** - Health checks and metrics

---

## 🚀 Running Locally

### Prerequisites

- Java 25
- Redis server running on `localhost:6379`
- Eureka Discovery Service running on `localhost:8761`

### Run with Gradle

```bash
./gradlew :backend:api-gateway:bootRun
```

### Run with Docker

```bash
# Build Docker image
docker build -t filmpire/api-gateway:latest backend/api-gateway/

# Run container
docker run -p 8080:8080 \
  -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-service:8761/eureka/ \
  -e REDIS_HOST=redis \
  -e JWT_SECRET=your-secret-key \
  filmpire/api-gateway:latest
```

---

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Gateway port | `8080` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Eureka server URL | `http://localhost:8761/eureka/` |
| `REDIS_HOST` | Redis server host | `localhost` |
| `REDIS_PORT` | Redis server port | `6379` |
| `REDIS_PASSWORD` | Redis password | `` |
| `JWT_SECRET` | JWT signing secret | *(must be changed in production)* |
| `JWT_EXPIRATION` | JWT expiration time (ms) | `86400000` (24 hours) |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` |

### Rate Limiting Configuration

**Global Rate Limiting** (across all routes):
- **100 requests/second per IP** - Hard cap to prevent DDoS attacks

**Per-Service Rate Limiting** (IP-based):
- **Auth Service**: 5 requests/second, burst capacity 10 *(strict limits to prevent brute force)*
- **Movie Service**: 10 requests/second, burst capacity 20
- **User Service**: 10 requests/second, burst capacity 20
- **Actor Service**: 10 requests/second, burst capacity 20
- **AI Service**: 5 requests/second, burst capacity 10 *(lower due to compute intensity)*
- **Media Service**: 20 requests/second, burst capacity 40 *(higher for file uploads)*

**Key Features:**
- IP-based key resolution (uses X-Forwarded-For header)
- Redis-backed for distributed deployments
- Rate limit headers in responses (`X-RateLimit-*`)
- 429 Too Many Requests on limit exceeded

### Circuit Breaker Configuration

- **Sliding Window Size**: 10 calls
- **Failure Rate Threshold**: 50%
- **Wait Duration in Open State**: 10 seconds
- **Slow Call Duration Threshold**: 2 seconds
- **Minimum Number of Calls**: 5

### Request Size Limits

- **Max Request Body**: 10MB (prevents large payload attacks)
- **Max HTTP Headers**: 16KB
- **Connection Timeout**: 5 seconds
- **Response Timeout**: 10 seconds

### Connection Limits

- **Max Connections**: 1000 (global pool)
- **Max Idle Time**: 30 seconds
- **Max Connection Life**: 60 seconds

### IP Filtering

- **Blacklist**: Block malicious IPs
- **Whitelist**: Restrict access to trusted IPs only
- **Admin API**: `/admin/security/*` endpoints for management

---

## 🛣 Routes

### Service Routes

| Route Pattern | Target Service | Description | Rate Limit | Circuit Breaker |
|--------------|----------------|-------------|-----------|----------------|
| `/api/v1/movies/**` | movie-service | Movie operations | 10/sec | ✅ |
| `/api/v1/users/**` | user-service | User management | 10/sec | ✅ |
| `/api/v1/auth/**` | user-service | Authentication | **5/sec** 🔒 | ✅ |
| `/api/v1/actors/**` | actor-service | Actor information | 10/sec | ✅ |
| `/api/v1/ai/**` | ai-service | AI recommendations | 5/sec | ✅ |
| `/api/v1/recommendations/**` | ai-service | Movie recommendations | 5/sec | ✅ |
| `/api/v1/media/**` | media-service | Media uploads | 20/sec | ✅ |
| `/admin/security/**` | gateway | IP filter management | Auth required | ✅ |

**Note:** Global rate limit of 100 req/sec per IP applies to all routes.

### Public Endpoints (No Authentication Required)

- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/register` - User registration
- `POST /api/v1/auth/refresh` - Refresh JWT token
- `GET /api/v1/movies/**` - Browse movies *(read-only)*
- `GET /api/v1/actors/**` - Browse actors *(read-only)*
- `GET /actuator/health` - Health check
- `GET /actuator/info` - Service information

### Protected Endpoints (Authentication Required)

All other endpoints require a valid JWT token in the `Authorization` header:

```
Authorization: Bearer <jwt-token>
```

---

## 🔒 Security

### JWT Authentication

The gateway validates JWT tokens and extracts user information:

1. **Token Validation**: Validates signature and expiration
2. **User Extraction**: Extracts username, user ID, and roles
3. **Header Propagation**: Adds user info headers for downstream services:
   - `X-User-Id`: User's unique ID
   - `X-Username`: User's username
   - `X-User-Roles`: Comma-separated list of roles

### CORS Configuration

Allowed origins:
- `http://localhost:3000` (React)
- `http://localhost:3001` (Alternative React port)
- `http://localhost:5173` (Vite)
- `http://localhost:5174` (Alternative Vite port)

Allowed methods: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`

---

## 🛡 Fault Tolerance

### Circuit Breaker

Each service has a dedicated circuit breaker with fallback endpoints:

- **Fallback Route**: `/fallback/{service}`
- **Response**: HTTP 503 with error message

Example fallback response:

```json
{
  "success": false,
  "message": "Movie Service is temporarily unavailable. Please try again later.",
  "status": 503,
  "timestamp": "2025-11-16T12:00:00"
}
```

### Retry Mechanism

- **Retries**: 3 attempts
- **Retry On**: `BAD_GATEWAY`, `SERVICE_UNAVAILABLE`
- **Retry Methods**: `GET` only (idempotent)
- **Backoff Strategy**: Exponential (50ms, 100ms, 200ms)

---

## 📊 Monitoring & Observability

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health status of gateway and dependencies |
| `/actuator/info` | Application information |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/gateway/routes` | Configured gateway routes |

### Logging

- **Request Logging**: All incoming requests with method, path, query params
- **Response Logging**: All outgoing responses with status code and duration
- **Slow Request Warning**: Requests taking > 1 second are logged as warnings
- **Error Logging**: All exceptions with stack traces

Log format:

```
==> Incoming Request: GET /api/v1/movies
<== Outgoing Response: GET /api/v1/movies - Status: 200 - Duration: 150ms
```

---

## 🧪 Testing

### Run Tests

```bash
# Run all tests
./gradlew :backend:api-gateway:test

# Run with coverage report
./gradlew :backend:api-gateway:jacocoTestReport
```

### Test Coverage

- **Unit Tests**: 22 tests covering all components
- **JWT Util Tests**: Token validation, extraction, expiration
- **Authentication Filter Tests**: Token processing, public endpoints
- **Fallback Controller Tests**: Circuit breaker fallback responses
- **Application Context Test**: Verifies Spring context loads correctly

---

## 📁 Project Structure

```
api-gateway/
├── src/
│   ├── main/
│   │   ├── java/com/filmpire/gateway/
│   │   │   ├── ApiGatewayApplication.java      # Main application class
│   │   │   ├── config/
│   │   │   │   └── SecurityConfig.java         # Security configuration
│   │   │   ├── controller/
│   │   │   │   └── FallbackController.java     # Circuit breaker fallbacks
│   │   │   ├── exception/
│   │   │   │   └── GlobalErrorWebExceptionHandler.java # Global error handler
│   │   │   ├── filter/
│   │   │   │   ├── JwtAuthenticationFilter.java # JWT authentication
│   │   │   │   └── LoggingFilter.java           # Request/response logging
│   │   │   └── util/
│   │   │       └── JwtUtil.java                 # JWT utilities
│   │   └── resources/
│   │       └── application.yml                  # Application configuration
│   └── test/
│       ├── java/com/filmpire/gateway/
│       │   ├── ApiGatewayApplicationTests.java
│       │   ├── controller/
│       │   │   └── FallbackControllerTest.java
│       │   ├── filter/
│       │   │   └── JwtAuthenticationFilterTest.java
│       │   └── util/
│       │       └── JwtUtilTest.java
│       └── resources/
│           └── application-test.yml             # Test configuration
├── build.gradle                                 # Build configuration
├── Dockerfile                                   # Docker image definition
├── .dockerignore                                # Docker ignore patterns
└── README.md                                    # This file
```

---

## 🐛 Troubleshooting

### Common Issues

**Issue**: Gateway returns 503 Service Unavailable  
**Solution**: Check if target microservices are registered with Eureka

**Issue**: JWT authentication fails  
**Solution**: Verify JWT_SECRET matches the one used by authentication service

**Issue**: Rate limiting not working  
**Solution**: Ensure Redis is running and accessible

**Issue**: CORS errors in frontend  
**Solution**: Add frontend origin to allowed-origins in application.yml

---

## 📝 Changelog

### Version 1.0.0 (2025-11-16)

- ✅ Initial implementation
- ✅ JWT authentication and authorization
- ✅ Rate limiting with Redis
- ✅ Circuit breaker with Resilience4j
- ✅ CORS configuration
- ✅ Request/response logging
- ✅ Global error handling
- ✅ Fallback endpoints for circuit breaker
- ✅ Comprehensive unit tests (22 tests, all passing)
- ✅ Docker support
- ✅ Actuator endpoints for monitoring

---

## 🔗 Related Services

- **Discovery Service** (Eureka) - http://localhost:8761
- **Movie Service** - http://localhost:8081
- **User Service** - http://localhost:8082
- **Actor Service** - http://localhost:8083
- **AI Service** - http://localhost:8084
- **Media Service** - http://localhost:8085

---

## 📄 License

Part of the Filmpire Microservices Platform - Portfolio Project

---

**Version:** 1.0.0  
**Last Updated:** November 16, 2025  
**Maintainer:** Filmpire Development Team

