# Port Mapping Reference

Complete port mapping for LMDB Microservices infrastructure, backend services, telemetry, and frontend applications.

## 🗺️ Port Allocation Strategy

**Port Ranges:**
- `3000-5173`: Frontend applications & visualization dashboards (React/Vite, Grafana)
- `5432-6379`: Database & messaging services (PostgreSQL+pgvector, MongoDB, Redis, Kafka)
- `8080-8089`: Backend microservices (REST)
- `8761-8888`: Infrastructure services (Eureka, Config Server)
- `9000-9009`: Object storage (MinIO)
- `9080-9089`: Management UIs & internal gRPC (Adminer, Mongo Express, Redis Commander, AI gRPC)
- `9090-9411`: Observability & telemetry (Prometheus, Elasticsearch, Zipkin)
- `11434`: Local AI Inference (Ollama)
- `30080`: Kubernetes NodePort ingress for API Gateway

---

## 📊 Complete Port Map

### Infrastructure & Database Services (Docker Compose)

| Service | Port | Protocol | Purpose | Access |
|---------|------|----------|---------|--------|
| **PostgreSQL (pgvector)** | `5432` | TCP | User accounts, actor filmographies, AI vector embeddings | `localhost:5432` |
| **MongoDB** | `27017` | TCP | Movie catalog persistence & media metadata | `localhost:27017` |
| **Redis** | `6379` | TCP | Distributed caching & API Gateway rate limiting | `localhost:6379` |
| **Apache Kafka** | `9092` | TCP | Asynchronous analytics event bus (local profile) | `localhost:9092` |
| **Ollama** | `11434` | HTTP | Local LLM inference (LLaMA 3.2, nomic-embed-text) | `http://localhost:11434` |
| **MinIO API** | `9000` | HTTP | S3-compatible object storage API | `http://localhost:9000` |
| **MinIO Console** | `9001` | HTTP | Object storage web console | `http://localhost:9001` |
| **Adminer** | `9081` | HTTP | PostgreSQL database management UI | `http://localhost:9081` |
| **Mongo Express** | `9082` | HTTP | MongoDB database management UI | `http://localhost:9082` |
| **Redis Commander** | `9083` | HTTP | Redis key-value management UI | `http://localhost:9083` |

### Backend Microservices

| Service | Port | Protocol | Purpose | Access |
|---------|------|----------|---------|--------|
| **API Gateway** | `8080` | HTTP | Edge routing, JWT auth filter, rate limiting, circuit breaker | `http://localhost:8080` |
| **Movie Service** | `8081` | HTTP | TMDB facade, self-healing catalog, MongoDB & Redis caching | `http://localhost:8081` |
| **User Service** | `8082` | HTTP | User management, JWT token issuance & verification | `http://localhost:8082` |
| **Actor Service** | `8083` | HTTP | Actor biographies, cast filmographies, credits mapping | `http://localhost:8083` |
| **AI Service (REST)** | `8084` | HTTP | AI chat, semantic search, Vosk voice STT endpoint | `http://localhost:8084` |
| **AI Service (gRPC)** | `9084` | gRPC | High-throughput vector & recommendation gRPC endpoint | `localhost:9084` |
| **Media Service** | `8085` | HTTP | Media upload, chunking, MinIO S3 integration | `http://localhost:8085` |
| **Discovery Service** | `8761` | HTTP | Netflix Eureka service discovery (local profile) | `http://localhost:8761` |
| **Config Service** | `8888` | HTTP | Spring Cloud Config centralized configuration server | `http://localhost:8888` |

### Observability & Telemetry

| Service | Port | Protocol | Purpose | Access |
|---------|------|----------|---------|--------|
| **Prometheus** | `9090` | HTTP | Time-series metric scraper (`/actuator/prometheus`) | `http://localhost:9090` |
| **Grafana** | `3001` | HTTP | Service health & JVM metric visualization | `http://localhost:3001` |
| **OpenZipkin** | `9411` | HTTP | Distributed request tracing & latency profiling | `http://localhost:9411` |
| **Elasticsearch** | `9200` | HTTP | Centralized JSON log indexer | `http://localhost:9200` |
| **Kibana** | `5601` | HTTP | Log analysis & query dashboard | `http://localhost:5601` |

### Frontend Applications & Cloud Ingress

| Service | Port | Protocol | Purpose | Access |
|---------|------|----------|---------|--------|
| **React / Vite Frontend** | `5173` / `3000` | HTTP | User interface web application | `http://localhost:5173` |
| **Kubernetes NodePort** | `30080` | HTTP | Direct external ingress on AKS / AWS EC2 nodes | `http://<NODE_IP>:30080` |

---

## 🔧 Configuration

### Docker Compose Ports

Configured via environment variables in `infrastructure/docker/.env`:

```bash
# Database & Messaging Ports
POSTGRES_PORT=5432
MONGO_PORT=27017
REDIS_PORT=6379
KAFKA_PORT=9092

# Object Storage & AI
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
OLLAMA_PORT=11434

# Management UI Ports
ADMINER_PORT=9081
MONGO_EXPRESS_PORT=9082
REDIS_COMMANDER_PORT=9083

# Observability Ports
ZIPKIN_PORT=9411
ELASTICSEARCH_PORT=9200
KIBANA_PORT=5601
GRAFANA_PORT=3001
PROMETHEUS_PORT=9090
```

---

## 📝 Quick Reference

### Start Infrastructure
```bash
./gradlew deployLocal
# or: ./infrastructure/scripts/start-infrastructure.sh
```

### Access Management & Telemetry UIs
```bash
# PostgreSQL Adminer
open http://localhost:9081

# MongoDB Mongo Express
open http://localhost:9082

# Redis Commander
open http://localhost:9083

# MinIO Console
open http://localhost:9001

# Grafana Dashboards
open http://localhost:3001

# OpenZipkin Tracing
open http://localhost:9411

# Kibana Logs
open http://localhost:5601
```

---

## 📚 Related Documentation

- [System Architecture Specification](./ARCHITECTURE.md)
- [Docker Infrastructure Setup](./DOCKER_INFRASTRUCTURE_SETUP.md)
- [Gradle Build Setup](./GRADLE_BUILD_SETUP.md)
- [Multi-Cloud Deployment Guide](../guides/DEPLOYMENT_GUIDE.md)

