# Port Mapping Reference

Complete port mapping for Filmpire Microservices infrastructure and backend services.

## 🗺️ Port Allocation Strategy

**Port Ranges:**
- `5432-6379`: Database services (PostgreSQL, MongoDB, Redis)
- `8080-8089`: Backend microservices
- `8761-8888`: Infrastructure services (Eureka, Config Server)
- `9000-9009`: Object storage (MinIO)
- `9080-9089`: Management UIs (Adminer, Mongo Express, Redis Commander)

---

## 📊 Complete Port Map

### Infrastructure Services (Docker Compose)

| Service | Port | Protocol | Purpose | Access |
|---------|------|----------|---------|--------|
| **PostgreSQL** | `5432` | TCP | Database for User & Actor services | `localhost:5432` |
| **MongoDB** | `27017` | TCP | Database for Movie, AI & Media services | `localhost:27017` |
| **Redis** | `6379` | TCP | Cache & rate limiting for API Gateway | `localhost:6379` |
| **MinIO API** | `9000` | HTTP | Object storage API | `http://localhost:9000` |
| **MinIO Console** | `9001` | HTTP | Object storage management UI | `http://localhost:9001` |
| **Adminer** | `9081` | HTTP | PostgreSQL web UI | `http://localhost:9081` |
| **Mongo Express** | `9082` | HTTP | MongoDB web UI | `http://localhost:9082` |
| **Redis Commander** | `9083` | HTTP | Redis web UI | `http://localhost:9083` |

### Backend Microservices

| Service | Port | Protocol | Purpose | Access |
|---------|------|----------|---------|--------|
| **API Gateway** | `8080` | HTTP | Main entry point, routes requests | `http://localhost:8080` |
| **Movie Service** | `8081` | HTTP | Movie data & metadata | `http://localhost:8081` |
| **User Service** | `8082` | HTTP | User management & authentication | `http://localhost:8082` |
| **Actor Service** | `8083` | HTTP | Actor/cast information | `http://localhost:8083` |
| **AI Service** | `8084` | HTTP | AI features (recommendations, chat) | `http://localhost:8084` |
| **Media Service** | `8085` | HTTP | Media file storage & delivery | `http://localhost:8085` |
| **Discovery Service** | `8761` | HTTP | Eureka service registry | `http://localhost:8761` |
| **Config Service** | `8888` | HTTP | Centralized configuration server | `http://localhost:8888` |

---

## 🔍 Port Conflict Resolution

### ✅ Resolved Conflicts

**Previous Conflicts (FIXED):**
- ❌ Adminer `8081` ↔ Movie Service `8081` → **Fixed**: Adminer moved to `9081`
- ❌ Mongo Express `8082` ↔ User Service `8082` → **Fixed**: Mongo Express moved to `9082`
- ❌ Redis Commander `8083` ↔ Actor Service `8083` → **Fixed**: Redis Commander moved to `9083`

### ✅ No Conflicts

All ports are now unique and non-conflicting:
- Database ports: `5432`, `27017`, `6379` (standard ports)
- Backend services: `8080-8085` (sequential)
- Infrastructure: `8761`, `8888` (standard Spring Cloud ports)
- Management UIs: `9081-9083` (separate range)
- Object storage: `9000-9001` (MinIO standard)

---

## 🔧 Configuration

### Docker Compose Ports

Ports are configurable via environment variables in `infrastructure/docker/.env`:

```bash
# Database Ports
POSTGRES_PORT=5432
MONGO_PORT=27017
REDIS_PORT=6379

# MinIO Ports
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001

# Management UI Ports (changed to avoid conflicts)
ADMINER_PORT=9081
MONGO_EXPRESS_PORT=9082
REDIS_COMMANDER_PORT=9083
```

### Backend Service Ports

Configured in `backend/<service>/src/main/resources/application.yml`:

```yaml
server:
  port: 8081  # Service-specific port
```

---

## 📝 Quick Reference

### Start Infrastructure
```bash
cd infrastructure/docker
podman-compose up -d
```

### Access Management UIs
```bash
# PostgreSQL Adminer
open http://localhost:9081

# MongoDB Mongo Express
open http://localhost:9082

# Redis Commander
open http://localhost:9083

# MinIO Console
open http://localhost:9001
```

### Start Backend Services
```bash
# Discovery Service (Eureka)
./gradlew :backend:discovery-service:bootRun
# Access: http://localhost:8761

# Config Service
./gradlew :backend:config-service:bootRun
# Access: http://localhost:8888

# API Gateway
./gradlew :backend:api-gateway:bootRun
# Access: http://localhost:8080

# Movie Service
./gradlew :backend:movie-service:bootRun
# Access: http://localhost:8081

# User Service
./gradlew :backend:user-service:bootRun
# Access: http://localhost:8082

# Actor Service
./gradlew :backend:actor-service:bootRun
# Access: http://localhost:8083

# AI Service
./gradlew :backend:ai-service:bootRun
# Access: http://localhost:8084

# Media Service
./gradlew :backend:media-service:bootRun
# Access: http://localhost:8085
```

---

## 🚨 Troubleshooting

### Port Already in Use

If you encounter "port already in use" errors:

1. **Check what's using the port:**
   ```bash
   # Linux
   sudo lsof -i :8081
   sudo netstat -tulpn | grep 8081
   
   # Or using ss
   sudo ss -tulpn | grep 8081
   ```

2. **Kill the process:**
   ```bash
   # Find PID from above command, then:
   kill -9 <PID>
   ```

3. **Or change the port:**
   - For Docker services: Update `infrastructure/docker/.env`
   - For backend services: Update `backend/<service>/src/main/resources/application.yml`

### Verify All Ports Are Free

```bash
# Check all Filmpire ports at once
for port in 5432 27017 6379 8080 8081 8082 8083 8084 8085 8761 8888 9000 9001 9081 9082 9083; do
  if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null ; then
    echo "Port $port is in use"
  else
    echo "Port $port is free"
  fi
done
```

---

## 📚 Related Documentation

- [Docker Infrastructure Setup](./DOCKER_INFRASTRUCTURE_SETUP.md)
- [Gradle Build Setup](./GRADLE_BUILD_SETUP.md)
- [GitHub Setup](./GITHUB_SETUP.md)

---

**Last Updated:** 2025-11-14  
**Status:** ✅ All conflicts resolved

