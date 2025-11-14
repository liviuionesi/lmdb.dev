# API Gateway

Single entry point for all client requests in the Filmpire microservices platform.

**Port:** 8080

## Responsibilities

- Request routing to microservices
- Authentication and authorization (JWT)
- Rate limiting (100 requests/minute per IP)
- CORS configuration
- Circuit breaker pattern
- Request/response transformation
- Load balancing via Eureka

## Technology Stack

- Spring Cloud Gateway
- Spring Security (JWT)
- Redis (rate limiting)
- Eureka Client

## Running Locally

```bash
./gradlew :backend:api-gateway:bootRun
```

## Docker

```bash
docker build -t filmpire/api-gateway:latest .
docker run -p 8080:8080 filmpire/api-gateway:latest
```

## Configuration

See `src/main/resources/application.yml` for configuration details.

Key environment variables:
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`: Eureka server URL
- `SPRING_DATA_REDIS_HOST`: Redis host for rate limiting
- `JWT_SECRET`: Secret key for JWT validation

## Routes

| Route | Target Service | Description |
|-------|---------------|-------------|
| `/api/v1/movies/**` | Movie Service | Movie operations |
| `/api/v1/users/**` | User Service | User management |
| `/api/v1/auth/**` | User Service | Authentication |
| `/api/v1/actors/**` | Actor Service | Actor information |
| `/api/v1/ai/**` | AI Service | AI recommendations |
| `/api/v1/media/**` | Media Service | Media uploads |

## Access

- Gateway: http://localhost:8080
- Actuator: http://localhost:8080/actuator/health

