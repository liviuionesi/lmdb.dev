# Filmpire Microservices Platform

A portfolio project: a Spring Boot backend that clones the [TMDB v3 API](https://developer.themoviedb.org/reference/intro/getting-started) closely enough that the existing **Filmpire React app** (`frontend/filmpire`) runs against it as a **drop-in replacement** for `https://api.themoviedb.org/3` — only its base URL changes. Requests are served read-through/save-through: **Redis → the service's own persisted, typed catalog (MongoDB/PostgreSQL) → real TMDB (fallback, rate-limited)**, and what's fetched from TMDB gets mapped, saved, and reserialized in TMDB's exact shape — not replayed as raw cached bytes (see [ADR-010](docs/architecture/adr/010-tmdb-facade-mapped-persisted-schema.md)). Around that core product sits a real microservices stack (service discovery, config server, gateway, rate limiting, circuit breakers), full local observability (Prometheus/Grafana + ELK), and $0-budget Terraform-provisioned cloud deployment (Azure AKS + AWS k3s) — see [`docs/architecture/ARCHITECTURE.md`](docs/architecture/ARCHITECTURE.md) for the complete, current architecture and the [ADRs](docs/architecture/adr/) for why each major decision was made.

## 🏗️ Architecture

- **8 backend microservices** (Spring Boot 4.1.0, Java 25, Gradle Groovy DSL) — `api-gateway`, `discovery-service` (Eureka), `config-service`, `movie-service`, `user-service`, `actor-service` are implemented and tested; `ai-service` and `media-service` are scaffolded (`HelloController` stubs) with their target design written up in ARCHITECTURE.md §3.7/§3.8, not yet built (tracked as open issues #36/#37)
- **1 frontend application** (`frontend/filmpire` — the existing Filmpire React app, CRA + Redux Toolkit Query + MUI + Alan AI voice, merged into this repo as a monorepo with full history preserved — see [ADR-013](docs/architecture/adr/013-frontend-merged-into-monorepo.md))
- **Hybrid database strategy** — PostgreSQL for user-owned, non-re-derivable data (accounts, favorites); MongoDB for the TMDB-derived movie/actor catalog, which can self-heal from a schema-drifted document by re-fetching (see [ADR-002](docs/architecture/adr/002-database-per-service.md), [ADR-011](docs/architecture/adr/011-self-healing-read-through-on-schema-drift.md))
- **Spring Cloud infrastructure** — Eureka discovery, Config Server, Spring Cloud Gateway (JWT auth, Redis-backed rate limiting, Resilience4j circuit breakers, CORS)
- **Observability** — every service instrumented with Actuator + Micrometer/Prometheus + JSON structured logging; full local stack (kube-prometheus-stack + Grafana + Alertmanager, ELK) verified live on minikube
- **$0-budget cloud deployment** — Terraform provisions Azure AKS (primary) and AWS k3s-on-EC2 (secondary), both constrained to free-tier limits with a zero-spend budget tripwire; Kubernetes via Kustomize (`base` + `local`/`azure`/`aws` overlays); see ARCHITECTURE.md §11 and [`infrastructure/terraform/README.md`](infrastructure/terraform/README.md)
- **CI** — `backend-ci.yml` (build+test on every push/PR touching `backend/**`), `e2e-smoke.yml` (nightly live-stack Postman/newman run), `terraform-plan.yml` (plan-only, GitHub OIDC, on push to `main`). Image publish + gated cloud deploy is still open (issue #28) — nothing auto-deploys today
- **Spring AI** — a dependency on the classpath (`spring-ai` 1.0.0-SNAPSHOT), not yet wired into a running service; `ai-service` is where it lands (§3.7)

### Development Standards (Spring Boot 4.1.x + Java 25)

**Core Principles:**
- ✅ **Constructor Injection ONLY** - NO field injection
- ✅ **Java Records for DTOs** - NO mutable classes
- ✅ **RestClient or @HttpExchange** - NO RestTemplate
- ✅ **JUnit 5 (Jupiter) exclusively** - JUnit 4 FORBIDDEN
- ✅ **Testcontainers with @ServiceConnection** - NO H2
- ✅ **ReentrantLock** - NO synchronized blocks (Virtual Threads)
- ✅ **Version Management via gradle.properties** - Single source of truth

## 📋 Prerequisites

- **Java 25** (via SDKMAN)
- **Gradle 9.2.0** (via Gradle Wrapper)
- **Node.js 24.x** (via NVM) — for `frontend/filmpire`
- **Docker or Podman** (this repo's own dev environment uses Podman + `podman-compose`/`docker compose` — either works)
- **A TMDB API key** ([themoviedb.org](https://www.themoviedb.org/settings/api), free) — required for the movie/actor services to populate their catalog and for the gateway's auth/account proxy; set as `TMDB_API_KEY` in `infrastructure/docker/.env`
- PostgreSQL 17, MongoDB 8.0, Redis 7.4 — run via the compose stack below, no separate install needed

## 🚀 Quick Start

### 1. Clone and configure

```bash
git clone https://github.com/pehlivanu/filmpire-microservices.git
cd filmpire-microservices
cp infrastructure/docker/env.example infrastructure/docker/.env
# edit infrastructure/docker/.env and set TMDB_API_KEY
```

### 2. Start the backend stack

```bash
cd infrastructure/scripts
./start-infrastructure.sh
```

This brings up the full stack via Docker/Podman Compose in one shot: Postgres, MongoDB, Redis, MinIO, Elasticsearch/Kibana, Eureka, Config Service, and all implemented application services (`api-gateway` on `:8080`, `movie-service` on `:8081`, `user-service` on `:8082`, `actor-service` on `:8083`). Smoke-test it:

```bash
curl http://localhost:8080/genre/movie/list
```

For iterating on a single service instead (hot-reload via `bootRun`), source `infrastructure/docker/.env` first so the required env vars (`TMDB_API_KEY`, `REDIS_PASSWORD`, DB creds) are present, then `cd backend/<service> && ../../gradlew bootRun`.

### 3. Start the frontend

```bash
cd frontend/filmpire
echo "REACT_APP_API_URL=http://localhost:8080" >> .env.local
npm install
npm start
```

See [`docs/guides/RUN_WITH_FILMPIRE_APP.md`](docs/guides/RUN_WITH_FILMPIRE_APP.md) for the full runbook — manual E2E checklist and verifying drop-in parity against real TMDB.

## 📁 Project Structure

```
filmpire-microservices/
├── backend/
│   ├── api-gateway/       # Spring Cloud Gateway (Port 8080)
│   ├── discovery-service/ # Eureka Server (Port 8761)
│   ├── config-service/    # Config Server (Port 8888)
│   ├── movie-service/     # Movie/genre TMDB facade + native API (Port 8081)
│   ├── user-service/      # Auth, favorites, watchlist (Port 8082)
│   ├── actor-service/     # Actor/person TMDB facade + native API (Port 8083)
│   ├── ai-service/        # Scaffolded, not yet implemented (Port 8084)
│   ├── media-service/     # Scaffolded, not yet implemented (Port 8085)
│   └── shared-library/    # Shared DTOs, exceptions, utilities
├── frontend/
│   └── filmpire/           # Existing CRA app (merged in as a monorepo,
│                            # full history preserved — see ADR-013)
├── infrastructure/
│   ├── docker/             # docker-compose.yml (full local stack)
│   ├── kubernetes/         # Kustomize base + local/azure/aws overlays
│   ├── terraform/          # Azure AKS + AWS k3s free-tier provisioning
│   └── scripts/            # start/stop-infrastructure.sh
└── docs/
    ├── architecture/       # ARCHITECTURE.md + adr/
    ├── api/                # Postman collection
    └── guides/              # Runbooks
```

## 🛠️ Technology Stack

### Backend (versions in `gradle.properties`)
- **Java** 25 (via SDKMAN)
- **Gradle** 9.2.0 (Groovy DSL via wrapper)
- **Spring Boot** 4.1.0 (Framework 7, Jackson 3, Jakarta EE 11)
- **Spring Cloud** 2025.1.2
- **Spring AI** 1.0.0-SNAPSHOT (on the classpath, not yet enabled — see §3.7)
- **PostgreSQL** 17-alpine
- **MongoDB** 8.0
- **Redis** 7.4-alpine
- **Bucket4j** — TMDB outbound rate limiting

### Testing Stack
- **JUnit** 5.11.3 (Jupiter ONLY - JUnit 4 FORBIDDEN)
- **Mockito** 5.19.0
- **Testcontainers** 2.0.5 (with `@ServiceConnection`)
- **AssertJ** (fluent assertions)
- **WireMock** — stubs TMDB in integration tests
- **JaCoCo** 0.8.14 — coverage reporting; 85%+ is this project's stated target (ARCHITECTURE.md §13), not a build-enforced gate

### Frontend (`frontend/filmpire`)
- **React (CRA)** 17.x (`react-scripts` 5)
- **Redux Toolkit Query** 1.6.x
- **Material UI** 5.x
- **axios** 1.6.x
- **Alan AI SDK** 1.8.x (voice control)

## 📚 Documentation

- [Architecture Document](./docs/architecture/ARCHITECTURE.md) - Complete, current system architecture
- [Architecture Decision Records](./docs/architecture/adr/) - Why each major decision was made
- [Gradle Build Setup](./docs/architecture/GRADLE_BUILD_SETUP.md) - Build configuration & version management
- [Spring Boot Development Rules](./.cursor/rules/spring-boot-development.mdc) - Development standards
- [Postman Collection](./docs/api/) - All endpoints, incl. an auth pre-request script (per-service OpenAPI/Swagger UI is also available at runtime, `/swagger-ui.html`)
- [Port Mapping](./docs/architecture/PORT_MAPPING.md) - Service ports reference
- [Running the Frontend Against This Backend](./docs/guides/RUN_WITH_FILMPIRE_APP.md) - Runbook for `frontend/filmpire`

## 🧪 Testing

```bash
# Run all backend tests
./gradlew test

# Run one service's tests
cd backend/movie-service
./gradlew test

# Run with coverage report
./gradlew test jacocoTestReport

# Run frontend tests
cd frontend/filmpire
npm test
```

**Testing conventions:**
- ✅ JUnit 5 (Jupiter) exclusively - NO JUnit 4
- ✅ Testcontainers with `@ServiceConnection` for integration tests - NO H2
- ✅ WireMock stubs TMDB in facade/read-through tests
- ✅ `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` in every service's `build.gradle`
- ✅ 85%+ coverage is the target (see Testing Stack above)

## 🚢 Deployment

$0-budget, Terraform-provisioned, ephemeral-by-design (`apply` → demo → `destroy`, nothing runs unattended in the cloud) — see ARCHITECTURE.md §11 and [`infrastructure/terraform/README.md`](infrastructure/terraform/README.md) for the full walkthrough and the real gotchas found running it live.

- **Azure AKS** (primary) — free-tier control plane, Terraform in `infrastructure/terraform/azure/`; live apply/destroy round-trip verified 2026-07-29
- **AWS k3s on EC2** (secondary) — `infrastructure/terraform/aws/`; code written, live verification pending AWS account signup
- **Kubernetes manifests** — Kustomize `base/` + `overlays/{local,azure,aws}`, `infrastructure/kubernetes/`
- **Container images** — `ghcr.io` (free for public repos); publish + gated deploy workflow is not built yet (issue #28) — the cloud overlays currently reference image tags that don't exist until that ships
- **Local** is the primary dev/demo environment (minikube/k3d via Podman) — cloud is a demo target only, verified separately, never the daily dev loop

## 📝 License

This project is created for best practices exemplification and technological insight demonstration purposes.

## 👤 Author

Liviu Ionesi

---

**Status**: 🚧 In active development — see [open issues](https://github.com/pehlivanu/filmpire-microservices/issues) for what's next  
**Version**: 1.0.0-SNAPSHOT
