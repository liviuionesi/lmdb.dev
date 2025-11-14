# Docker Infrastructure Setup

## Overview

Complete Docker Compose configuration for local development environment with all required databases and services for Filmpire Microservices.

## Services Included

### Databases & Storage

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| **PostgreSQL** | postgres:17-alpine | 5432 | User Service, Actor Service |
| **MongoDB** | mongo:8.0 | 27017 | Movie Service, AI Service, Media Service |
| **Redis** | redis:7.4-alpine | 6379 | API Gateway (rate limiting, session) |
| **MinIO** | minio/minio:RELEASE.2024-11-07T00-52-20Z | 9000, 9001 | Media Service (object storage) |

### Management UIs (Optional)

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| **Adminer** | adminer:latest | 8081 | PostgreSQL Web UI |
| **Mongo Express** | mongo-express:latest | 8082 | MongoDB Web UI |
| **Redis Commander** | rediscommander/redis-commander:latest | 8083 | Redis Web UI |

## Quick Start

### 1. Start Infrastructure

```bash
cd infrastructure/docker
./start-infrastructure.sh
```

Or manually:

```bash
cd infrastructure/docker
docker compose up -d
```

### 2. Check Status

```bash
docker compose ps
```

### 3. View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f postgres
docker compose logs -f mongodb
```

### 4. Stop Infrastructure

```bash
./stop-infrastructure.sh
```

Or manually:

```bash
docker compose down
```

### 5. Stop and Remove Data (CAUTION)

```bash
./stop-infrastructure.sh --volumes
```

Or manually:

```bash
docker compose down -v
```

## Configuration

### Environment Variables

Copy `env.example` to `.env` to customize:

```bash
cd infrastructure/docker
cp env.example .env
# Edit .env with your preferred values
```

### Default Credentials

**PostgreSQL:**
- Host: `localhost:5432`
- Database: `filmpire`
- Username: `admin`
- Password: `admin123`

**MongoDB:**
- Host: `localhost:27017`
- Database: `filmpire`
- Username: `admin`
- Password: `admin123`

**Redis:**
- Host: `localhost:6379`
- Password: `redis123`

**MinIO:**
- API: `localhost:9000`
- Console: `localhost:9001`
- Username: `minioadmin`
- Password: `minioadmin123`

## Service Connection Strings

### Spring Boot Configuration

**PostgreSQL (application.yml):**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/filmpire
    username: admin
    password: admin123
    driver-class-name: org.postgresql.Driver
```

**MongoDB (application.yml):**
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin
```

**Redis (application.yml):**
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: redis123
```

**MinIO (application.yml):**
```yaml
minio:
  url: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin123
  bucket-name: filmpire-media
```

## Management UIs

### Adminer (PostgreSQL)
- **URL:** http://localhost:8081
- **System:** PostgreSQL
- **Server:** postgres
- **Username:** admin
- **Password:** admin123
- **Database:** filmpire

### Mongo Express (MongoDB)
- **URL:** http://localhost:8082
- **No authentication required** (disabled for development)

### Redis Commander
- **URL:** http://localhost:8083
- **No authentication required**

### MinIO Console
- **URL:** http://localhost:9001
- **Username:** minioadmin
- **Password:** minioadmin123

## Volume Persistence

Data is persisted in Docker volumes:

```yaml
volumes:
  postgres_data     # PostgreSQL data
  mongo_data        # MongoDB data
  mongo_config      # MongoDB config
  redis_data        # Redis data
  minio_data        # MinIO object storage
```

To view volumes:
```bash
docker volume ls | grep filmpire
```

To inspect a volume:
```bash
docker volume inspect infrastructure-docker_postgres_data
```

## Health Checks

All services include health checks:

- **PostgreSQL:** `pg_isready` every 10s
- **MongoDB:** `mongosh ping` every 10s
- **Redis:** `redis-cli incr ping` every 10s
- **MinIO:** `curl health endpoint` every 10s

Check health status:
```bash
docker compose ps
```

## Networking

All services are on the `filmpire-network` bridge network, allowing inter-service communication.

Services can reference each other by service name:
- `postgres` → PostgreSQL
- `mongodb` → MongoDB
- `redis` → Redis
- `minio` → MinIO

## Troubleshooting

### Port Already in Use

If ports are already in use, modify `.env`:

```bash
POSTGRES_PORT=5433
MONGO_PORT=27018
REDIS_PORT=6380
```

### Container Won't Start

Check logs:
```bash
docker compose logs [service-name]
```

### Data Corruption

Stop and remove volumes (WARNING: deletes all data):
```bash
docker compose down -v
docker compose up -d
```

### Permission Issues

Ensure Docker has proper permissions:
```bash
sudo usermod -aG docker $USER
newgrp docker
```

### Can't Connect from Services

Ensure services are on the same network:
```bash
docker network inspect filmpire-network
```

## Production Considerations

⚠️ **This setup is for DEVELOPMENT ONLY**

For production:

1. **Use secrets management** (not plain text passwords)
2. **Enable SSL/TLS** for all connections
3. **Configure proper authentication**
4. **Use managed services** (AWS RDS, MongoDB Atlas, etc.)
5. **Set resource limits**
6. **Configure backups**
7. **Enable monitoring and alerting**
8. **Review security best practices**

## Multiple Database Support

PostgreSQL automatically creates multiple databases:
- `filmpire_users` - User Service
- `filmpire_actors` - Actor Service

MongoDB uses collections within single `filmpire` database:
- `movies` - Movie Service
- `ai_conversations` - AI Service
- `media_files` - Media Service

## Scripts

### start-infrastructure.sh

Features:
- Checks Docker installation
- Pulls latest images
- Starts all services
- Waits for health checks
- Displays access information

### stop-infrastructure.sh

Features:
- Graceful shutdown
- Optional volume removal with `--volumes` flag
- Confirmation prompt for data deletion

## Testing the Setup

### 1. Start Services
```bash
./start-infrastructure.sh
```

### 2. Test PostgreSQL
```bash
psql -h localhost -U admin -d filmpire
# Password: admin123
```

### 3. Test MongoDB
```bash
mongosh mongodb://admin:admin123@localhost:27017/filmpire?authSource=admin
```

### 4. Test Redis
```bash
redis-cli -h localhost -p 6379 -a redis123 ping
```

### 5. Test MinIO
```bash
curl http://localhost:9000/minio/health/live
```

## Task Completion

### ✅ Implementation Checklist

- [x] Create docker-compose.yml
- [x] Configure PostgreSQL 17
- [x] Configure MongoDB 8.0
- [x] Configure Redis 7.4
- [x] Configure MinIO
- [x] Setup networking (filmpire-network)
- [x] Configure volumes (5 persistent volumes)
- [x] Add health checks (all services)
- [x] Create startup script (start-infrastructure.sh)
- [x] Create shutdown script (stop-infrastructure.sh)
- [x] Create env.example with all variables

### ✅ Acceptance Criteria

- [x] `docker compose up -d` starts all services
- [x] All databases configured and accessible
- [x] Health checks configured for all services
- [x] Services can connect to databases (via network)
- [x] Proper networking configured (filmpire-network)
- [x] Volumes persist data (5 volumes configured)
- [x] Management UIs included (Adminer, Mongo Express, Redis Commander)

### Files Created

```
infrastructure/
├── docker/
│   ├── docker-compose.yml          ✅ Main configuration (7 services)
│   └── env.example                 ✅ Environment variables template
└── scripts/
    ├── start-infrastructure.sh     ✅ Startup script (executable)
    └── stop-infrastructure.sh      ✅ Shutdown script (executable)
```

## Version Information

| Component | Version | Released |
|-----------|---------|----------|
| PostgreSQL | 17-alpine | Sept 2024 |
| MongoDB | 8.0 | July 2024 |
| Redis | 7.4-alpine | July 2024 |
| MinIO | RELEASE.2024-11-07 | Nov 2024 |

All versions are latest stable releases as of November 2024.

## References

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [PostgreSQL Docker Hub](https://hub.docker.com/_/postgres)
- [MongoDB Docker Hub](https://hub.docker.com/_/mongo)
- [Redis Docker Hub](https://hub.docker.com/_/redis)
- [MinIO Documentation](https://min.io/docs/minio/container/index.html)

---

**Status:** ✅ Complete  
**Task:** #5 - Setup Docker Compose Infrastructure  
**Date:** 2025-11-14

