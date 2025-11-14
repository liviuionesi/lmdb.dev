# Config Service

Centralized configuration management for all Filmpire microservices using Spring Cloud Config.

**Port:** 8888

## Responsibilities

- Centralized configuration management
- Environment-specific configurations (dev, prod)
- Dynamic configuration refresh
- Configuration versioning via Git
- Encryption support for sensitive data

## Technology Stack

- Spring Cloud Config Server
- Eureka Client
- Git backend

## Running Locally

```bash
./gradlew :backend:config-service:bootRun
```

## Docker

```bash
docker build -t filmpire/config-service:latest .
docker run -p 8888:8888 filmpire/config-service:latest
```

## Configuration Repository Structure

```
config-repo/
├── application.yml           # Shared config
├── application-dev.yml       # Development environment
├── application-prod.yml      # Production environment
├── movie-service.yml         # Movie service specific
├── user-service.yml          # User service specific
├── actor-service.yml         # Actor service specific
├── ai-service.yml            # AI service specific
└── media-service.yml         # Media service specific
```

## Environment Variables

- `SPRING_CLOUD_CONFIG_SERVER_GIT_URI`: Git repository URL
- `SPRING_CLOUD_CONFIG_SERVER_GIT_DEFAULT_LABEL`: Branch name (default: main)
- `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`: Eureka server URL

## Encryption

To encrypt sensitive values:

```bash
curl http://localhost:8888/encrypt -d "my-secret-value"
```

Use encrypted values in config files with `{cipher}` prefix.

## Access

- Config Server: http://localhost:8888
- Configuration: http://localhost:8888/{application}/{profile}
- Health: http://localhost:8888/actuator/health

