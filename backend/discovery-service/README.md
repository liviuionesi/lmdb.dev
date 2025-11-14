# Discovery Service (Eureka Server)

Service discovery server for Filmpire microservices architecture.

**Port:** 8761

**Responsibilities:**
- Service registration and discovery
- Health monitoring
- Load balancing support

## Running Locally

```bash
./gradlew bootRun
```

## Docker

```bash
docker build -t filmpire/discovery-service:latest .
docker run -p 8761:8761 filmpire/discovery-service:latest
```

## Access

- Eureka Dashboard: http://localhost:8761

