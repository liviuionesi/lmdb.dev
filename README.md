# LMDB — Live Movies Database (Microservices Platform)

[![Backend CI](https://github.com/liviuionesi/lmdb.dev/actions/workflows/backend-ci.yml/badge.svg?branch=main)](https://github.com/liviuionesi/lmdb.dev/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/liviuionesi/lmdb.dev/actions/workflows/frontend-ci.yml/badge.svg?branch=main)](https://github.com/liviuionesi/lmdb.dev/actions/workflows/frontend-ci.yml)
[![Docker Publish](https://github.com/liviuionesi/lmdb.dev/actions/workflows/docker-publish.yml/badge.svg?branch=main)](https://github.com/liviuionesi/lmdb.dev/actions/workflows/docker-publish.yml)
[![Terraform Plan](https://github.com/liviuionesi/lmdb.dev/actions/workflows/terraform-plan.yml/badge.svg?branch=main)](https://github.com/liviuionesi/lmdb.dev/actions/workflows/terraform-plan.yml)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud 2025.1.2](https://img.shields.io/badge/Spring%20Cloud-2025.1.2-blue.svg)](https://spring.io/projects/spring-cloud)
[![React 19 / Vite](https://img.shields.io/badge/React-19%20%7C%20Vite-61dafb.svg)](https://vitejs.dev/)
[![Project Metrics](https://img.shields.io/badge/Project%20Metrics-Dynamic%20Report-purple.svg)](docs/reports/PROJECT_METRICS.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**LMDB** clones the TMDB v3 API in Spring, backed by its own persisted, self-healing catalog instead of a thin proxy — the existing LMDB React app talks to it as a drop-in replacement for `api.themoviedb.org`. It adds a local, $0-cost AI layer on top: a chat assistant and semantic search (Spring AI + Ollama + pgvector) and offline voice control (Vosk) — no paid API calls anywhere. Built by **[Liviu Ionesi](https://liviuionesi.com)** ([LinkedIn](https://www.linkedin.com/in/liviuionesi/)) as a portfolio project, deployable to Azure AKS or AWS k3s on a $0 cloud budget.

🌐 **Live Demo:** [lmdb.dev](https://lmdb.dev)
👤 **Portfolio:** [liviuionesi.com](https://liviuionesi.com) · [LinkedIn](https://www.linkedin.com/in/liviuionesi/)
📊 **Project Metrics:** [docs/reports/PROJECT_METRICS.md](docs/reports/PROJECT_METRICS.md)

---

## Table of Contents

1. [Executive Overview](#1-executive-overview)
2. [Feature Catalog](#2-feature-catalog)
3. [SDLC: How This Was Built](#3-sdlc-how-this-was-built)
4. [System Architecture & Data Flows](#4-system-architecture--data-flows)
5. [Codebase Layout](#5-codebase-layout)
6. [Testing Strategy](#6-testing-strategy)
7. [Deployment Topologies](#7-deployment-topologies)
8. [Observability](#8-observability)
9. [CI/CD](#9-cicd)
10. [Local Quick Start](#10-local-quick-start)
11. [Documentation Index](#11-documentation-index)
12. [Data Attribution & TMDB Compliance](#12-data-attribution--tmdb-compliance)

---

## 1. Executive Overview

LMDB is an end-to-end cloud-native microservices ecosystem: not a proxy in front of TMDB, but an independent data platform that populates itself from TMDB on first request and serves everything after that from its own storage.

```
React 19 / Vite SPA (Vercel)
         │
         ▼  (HTTPS / Dynamic Auto-Discovery)
API Gateway (Spring Cloud Gateway :8080) ──► JWT Auth · Redis Rate Limiter · Circuit Breakers
         │
 ┌───────┼────────────────┬────────────────┬───────────────┬────────────────┐
 ▼       ▼                ▼                ▼               ▼                ▼
Movie   User            Actor              AI            Media          Discovery
Service Service         Service          Service        Service          Service
(:8081) (:8082)         (:8083)       (:8084/:9084)     (:8085)          (:8761)
   │       │                │                │               │              │
MongoDB PostgreSQL      PostgreSQL      PostgreSQL       MongoDB         Eureka
  8.0      17               17           +pgvector        +MinIO         (Local)
   │                                         │
Redis                                   Ollama (LLaMA 3.2)
 (Cache)                                + Vosk (Offline STT)
```

### Key Engineering Highlights

* **Self-Healing Data Ingestion:** the TMDB facade persists external movie/actor records on first request and repairs local schema drift on later reads ([ADR-010](docs/architecture/adr/010-tmdb-facade-mapped-persisted-schema.md), [ADR-011](docs/architecture/adr/011-self-healing-read-through-on-schema-drift.md)).
* **Local-First AI ($0 API cost):** chat assistant, semantic search, recommendations, and natural-language movie search run entirely on Spring AI, Ollama (`llama3.2`, `nomic-embed-text`), and `pgvector` — no paid model API ([ADR-012](docs/architecture/adr/012-ai-service-postgresql-pgvector.md)).
* **Natural-Language Search:** the frontend search bar consumes `ai-service` directly through the gateway — free text is parsed into a structured filter and resolved across actor-service and movie-service in one round trip, no query-shape branching in the browser ([ADR-020](docs/architecture/adr/020-nl-query-cross-service-aggregation.md)).
* **Offline Voice Control:** speech-to-text runs inside the container via an embedded Vosk model, no cloud call.
* **Multi-Cloud Parity ($0 cost model):** ephemeral Terraform infrastructure for Azure AKS and AWS k3s, with scheduled auto-stop and budget guards ([ADR-004](docs/architecture/adr/004-zero-budget-cloud-strategy.md), [ADR-017](docs/architecture/adr/017-full-cloud-service-parity.md), [ADR-018](docs/architecture/adr/018-cloud-lifecycle-stop-not-destroy.md)).
* **Dynamic Backend Resolution:** one Vercel frontend build finds, health-checks, and binds to whichever backend is live — no rebuild required ([ADR-016](docs/architecture/adr/016-dynamic-backend-resolution.md)).

---

## 2. Feature Catalog

| Feature Area                       | Microservice                              | Storage / Tech                     | Implementation Details                                                                                                                                             |
| ---------------------------------- | ----------------------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Movie Catalog & Browse**         | [`movie-service`](backend/movie-service/) | MongoDB 8.0, Redis 7.4             | TMDB facade routes (`/movie/**`, `/genre/**`, `/discover/**`). Maps external payloads into typed documents, caches hot queries in Redis, persists records locally. |
| **User Authentication & Profiles** | [`user-service`](backend/user-service/)   | PostgreSQL 17, JPA/Hibernate       | BCrypt password hashing, signed JWT access/refresh tokens, profiles, favorites, watchlist.                                                                         |
| **Actor Biographies & Credits**    | [`actor-service`](backend/actor-service/) | PostgreSQL 17, JPA/Hibernate       | Actor profile retrieval, filmography mapping, person discovery routes (`/person/**`, `/search/person`).                                                            |
| **AI Assistant & Semantic Search** | [`ai-service`](backend/ai-service/)       | PostgreSQL + pgvector, Ollama      | Contextual movie chat via LLaMA 3.2, semantic search over vector embeddings (`nomic-embed-text`), taste-profile recommendations.                                   |
| **Natural-Language Movie Search**  | [`ai-service`](backend/ai-service/)       | Ollama                             | Frontend search bar calls `POST /api/v1/ai/search/execute` directly through the gateway — parses free text into a structured filter and aggregates it across actor-service and movie-service in one round trip ([ADR-020](docs/architecture/adr/020-nl-query-cross-service-aggregation.md)). |
| **Voice Navigation**               | [`ai-service`](backend/ai-service/)       | Vosk (offline)                     | Speech-to-text (`POST /api/v1/ai/speech-to-text`) driving category navigation, search, and UI actions.                                                             |
| **Media Asset Management**         | [`media-service`](backend/media-service/) | MinIO (S3 API), MongoDB 8.0        | Multipart upload, binary chunking, metadata indexing, presigned asset streaming.                                                                                   |
| **Traffic Control & Edge Routing** | [`api-gateway`](backend/api-gateway/)     | Spring Cloud Gateway, Redis        | Redis token-bucket rate limiting, Resilience4j circuit breakers, JWT validation, CORS, URL rewrites.                                                               |
| **Web Client Application**         | [`frontend/lmdb`](frontend/lmdb/)         | React 19, Vite, Redux Toolkit, MUI | Responsive UI, dark/light theme, speech recognition, trailer modal, dynamic backend resolver.                                                                      |

---

## 3. SDLC: How This Was Built

LMDB followed a strict Agile/Scrum lifecycle: a formal Product Goal, User Stories with Given/When/Then acceptance criteria, Definition of Ready/Done gates, and a numbered Architecture Decision Record for every significant call. See [`docs/process/`](docs/process/) for the standing definitions (not restated here) and [`docs/architecture/adr/`](docs/architecture/adr/) for all 20 ADRs — the two examples below are the ones that shaped the product most:

* [ADR-010: Facade-Mapped Persisted Schema](docs/architecture/adr/010-tmdb-facade-mapped-persisted-schema.md) — turns the TMDB facade from a proxy into a self-populating catalog.
* [ADR-016: Dynamic Backend Resolution](docs/architecture/adr/016-dynamic-backend-resolution.md) — lets one frontend build follow whichever cloud backend is currently up.

Full index: [§11 Documentation Index](#11-documentation-index).

---

## 4. System Architecture & Data Flows

### Real-Time Request Lifecycle

```
[Browser Client]
       │
       │ 1. GET /movie/popular (or /api/v1/movies/...)
       ▼
[api-gateway :8080]
  ├── Filter: CorrelationId & Micrometer Trace Injection
  ├── Filter: Redis Token Bucket Rate Limiting (varies by route, 5–20 req/s — see docs/security/DDOS_PROTECTION_IMPLEMENTED.md)
  ├── Filter: JWT Security Authentication & Role Check
  └── Route: Forward to http://movie-service:8081
       │
       ▼
[movie-service :8081]
  ├── 1. Check Redis Cache for key 'movies:popular:page:1'
  │      └── If HIT: Return cached JSON payload
  └── 2. If MISS: Query local MongoDB 'movies' collection
         ├── If Found & Schema Fresh: Return MongoDB document & populate Redis
         └── If Missing or Drift Detected:
                ├── Invoke TMDB API upstream
                ├── Map external schema to Movie entity
                ├── Save/Upsert to MongoDB
                └── Populate Redis & return response
```

### AI Chat & Semantic Vector Search Flow

```
[Browser Client] ──► POST /api/v1/ai/chat {"query": "Find dark sci-fi thrillers with AI"}
       │
       ▼
[api-gateway] ──► Forward to http://ai-service:8084
       │
       ▼
[ai-service :8084]
  ├── 1. Generate Query Embedding via Ollama ('nomic-embed-text')
  ├── 2. Cosine Similarity Search in PostgreSQL (pgvector `<=>` operator)
  ├── 3. Retrieve Top-K Matching Movie Overviews & Metadata
  ├── 4. Construct Augmented Prompt with Movie Context
  └── 5. Stream LLM Response from Ollama ('llama3.2') back to Gateway/Client
```

---

## 5. Codebase Layout

```
lmdb.dev/
├── backend/          # 8 Java 25 / Spring Boot 4 microservices (shared-library, api-gateway,
│                      # discovery-service, config-service, movie/user/actor/ai/media-service)
├── frontend/lmdb/     # React 19, Vite, Redux Toolkit Query, MUI
├── infrastructure/    # Docker Compose, Kubernetes (Kustomize), Terraform (Azure/AWS), scripts
├── e2e/               # Playwright browser acceptance suite
├── docs/              # architecture/ (spec + 19 ADRs), process/ (Scrum), guides/, security/
└── .github/           # CI/CD workflows, issue templates
```

Full annotated tree, one directory level deeper: [ARCHITECTURE.md Appendix A](docs/architecture/ARCHITECTURE.md#appendix-a-project-structure) — kept there, not duplicated here, so there's one tree to update instead of two.

---

## 6. Testing Strategy

Seven distinct test types run across this codebase, backend unit/integration tests plus five cross-cutting layers. The authoritative table — exact tool, scope, and file location for each — lives in [ARCHITECTURE.md §10.1](docs/architecture/ARCHITECTURE.md#101-testing-pyramid); the commands below are the ones you'll actually run day to day.

```bash
# Unit + Testcontainers integration tests (all backend modules)
./gradlew test

# Single service
./gradlew :backend:movie-service:test

# Consumer-driven contract verification (movie, user, actor, ai-service — not api-gateway)
./gradlew :backend:movie-service:contractTest

# Frontend component & state tests (Vitest)
cd frontend/lmdb && npm test
cd frontend/lmdb && npm run test:coverage

# Browser E2E (Playwright)
cd e2e && npx playwright test

# API smoke test against a running stack (Postman/Newman)
newman run docs/api/LMDB-API.postman_collection.json

# Performance/load simulation (Gatling — lives in movie-service, not api-gateway)
./gradlew :backend:movie-service:gatlingRun
```

---

## 7. Deployment Topologies

Four Terraform/Compose-codified targets, one Gradle task each:

| Topology                 | What it is                                                                             | Command                 |
| ------------------------ | -------------------------------------------------------------------------------------- | ----------------------- |
| **Local Dev Stack**      | Full container stack via Docker/Podman Compose or the local K8s overlay                | `./gradlew deployLocal` |
| **Local Stack + Tunnel** | Local/Minikube backend exposed to the deployed Vercel frontend via a Cloudflare tunnel | `./gradlew startTunnel` |
| **Azure AKS**            | Managed AKS cluster, Terraform-provisioned                                             | `./gradlew deployAzure` |
| **AWS k3s**              | Lightweight k3s on a single EC2 instance, Terraform-provisioned                        | `./gradlew deployAws`   |

Notes:
- The tunnel scenario auto-publishes its HTTPS hostname to [`infrastructure/tunnel-url.txt`](infrastructure/tunnel-url.txt) on `develop`; the deployed frontend picks it up within seconds via GitHub raw, no rebuild.
- Azure runs on a single `Standard_D4ls_v7` node behind a $1 budget tripwire; AWS runs k3s on a single `m7i-flex.large` instance (resized up from `t3.small` — Ollama alone needs up to 4Gi, and `t3.xlarge` was rejected outright as ineligible for the free tier — see [ARCHITECTURE.md §11.2](docs/architecture/ARCHITECTURE.md)).
- Full runbook, including troubleshooting and the CI-driven deploy path: [docs/guides/DEPLOYMENT_GUIDE.md](docs/guides/DEPLOYMENT_GUIDE.md).

```bash
./gradlew deployLocal          # 1. Local Compose / Minikube
./gradlew startTunnel          # 2. Expose local backend to the deployed frontend
./gradlew stopTunnel
./gradlew statusInfra          # 3. Check status of local / tunnel / cloud
./gradlew deployAzure          # 4. Azure AKS
./gradlew deployAws            # 5. AWS k3s
./gradlew destroyAzure         # 6. Teardown — guarantees $0 spend
./gradlew destroyAws
```

### Dynamic Frontend-to-Backend Binding
The Vercel frontend resolves its live backend at request time — manual override, then localhost, then cloud, then the published tunnel, each health-checked before use. Exact resolution order and code reference: [FRONTEND_ARCHITECTURE.md §3](frontend/lmdb/FRONTEND_ARCHITECTURE.md#3-dynamic-backend-url-auto-discovery-engine).

---

## 8. Observability

```
                                  TELEMETRY PIPELINE

 [Microservices] ──JSON stdout──► [Filebeat] ──► [Logstash] ──► [Elasticsearch] ──► [Kibana :5601]
       │                                                                               (Log Analysis)
       ├──Micrometer B3 Tracing──► [OpenZipkin :9411]
       │                               (Distributed Trace Spans & Latency Graph)
       └──Prometheus Metrics (/actuator/prometheus) ──► [Prometheus :9090] ──► [Grafana :3001]
                                                                                (Health Dashboards)
```

* **Structured Logging:** JSON via Logback + `LogstashEncoder` — timestamps, log levels, service names, correlation IDs.
* **Distributed Tracing:** Micrometer Tracing with OpenZipkin propagators across HTTP calls and Kafka events.
* **Metrics & Dashboards:** Actuator endpoints scraped by Prometheus every 15s; pre-built Grafana dashboards for JVM, HTTP latency, and circuit breaker state.

---

## 9. CI/CD

| Pipeline                                                     | Trigger                                  | Responsibilities                                                                                      |
| ------------------------------------------------------------ | ---------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| [`backend-ci.yml`](.github/workflows/backend-ci.yml)         | Push/PR to `main`, `develop`             | Compiles Java 25 modules, Spotless/Checkstyle, JUnit & Testcontainers suites, SonarQube Quality Gate. |
| [`frontend-ci.yml`](.github/workflows/frontend-ci.yml)       | Push/PR to `main`, `develop`             | ESLint, Prettier, Vitest suite with coverage.                                                         |
| [`docker-publish.yml`](.github/workflows/docker-publish.yml) | Chained to green Backend CI on `main`    | Builds container images for all 8 microservices, publishes to `ghcr.io/liviuionesi/lmdb-*`.           |
| [`terraform-plan.yml`](.github/workflows/terraform-plan.yml) | Changes to `infrastructure/terraform/**` | Validates HCL, `terraform fmt`, speculative plans for Azure and AWS.                                  |

* **Automated modernization (OpenRewrite):** `./gradlew rewriteRun`
* **Git hooks:** pre-commit blocks broken builds; commit-msg enforces `feat: ... (#NN)`-style messages linking a real issue.
* **Knowledge graph (Graphify):** AST graph of cross-service dependencies in `graphify-out/`.

---

## 10. Local Quick Start

**Prerequisites:** Java 25 JDK, Node.js 20+, Docker 24+ or Podman 5+ with Compose.

```bash
# 1. Clone
git clone https://github.com/liviuionesi/lmdb.dev.git
cd lmdb.dev

# 2. Configure environment
cp infrastructure/docker/.env.example infrastructure/docker/.env
# (Optional: add your own free TMDB_API_KEY from themoviedb.org/settings/api)

# 3. Launch the local microservices stack
./gradlew deployLocal

# 4. Pull Ollama models (one-time)
docker exec -it lmdb-ollama ollama pull llama3.2
docker exec -it lmdb-ollama ollama pull nomic-embed-text

# 5. Start the frontend dev server
cd frontend/lmdb
npm install
npm run dev
```

Visit **`http://localhost:5173`** (or `http://localhost:3000`).

---

## 11. Documentation Index

### Architecture & Technical Guides
* [System Architecture Specification](docs/architecture/ARCHITECTURE.md) — the full spec; source of truth for tech stack, ADR list, testing pyramid, and directory layout
* [Port Allocation Matrix](docs/architecture/PORT_MAPPING.md)
* [Event-Driven Architecture & Kafka Bus](docs/architecture/EVENT_DRIVEN_ARCHITECTURE.md)
* [Code Quality & SonarQube Standards](docs/architecture/CODE_QUALITY.md)
* [Integration Testing Strategy](docs/architecture/INTEGRATION_TESTING.md)
* [Junior Developer Deep Dive Guide](docs/architecture/JUNIOR_DEVELOPER_GUIDE.md)
* [Docker Compose Infrastructure Setup](docs/architecture/DOCKER_INFRASTRUCTURE_SETUP.md)
* [Gradle Multi-Module Build Architecture](docs/architecture/GRADLE_BUILD_SETUP.md)
* [Frontend Architecture](frontend/lmdb/FRONTEND_ARCHITECTURE.md) — component hierarchy, Redux/RTK Query, dynamic backend resolution

### Scrum Process & SDLC
* [Product Goal](docs/process/PRODUCT_GOAL.md) · [Methodology](docs/process/METHODOLOGY.md) · [Definition of Ready](docs/process/DEFINITION_OF_READY.md) · [Definition of Done](docs/process/DEFINITION_OF_DONE.md) · [Non-Functional Requirements](docs/process/NON_FUNCTIONAL_REQUIREMENTS.md) · [Scrum Events](docs/process/SCRUM_EVENTS.md)

### Deployment, Operations & Infrastructure
* [Multi-Cloud Deployment Runbook](docs/guides/DEPLOYMENT_GUIDE.md)
* [GitOps & CI/CD Cloud Automation](docs/guides/GITOPS_AND_CI_CD.md)
* [Running the Frontend Against This Backend](docs/guides/RUN_WITH_LMDB_APP.md)
* [Terraform Multi-Cloud Infrastructure](infrastructure/terraform/README.md)
* [Docker Compose Testing Runbook](infrastructure/docker/TESTING_GUIDE.md)
* [Kubernetes Prometheus ServiceMonitors](infrastructure/kubernetes/monitoring/service-monitors/README.md)

### Security Architecture
* [DDoS Mitigation & Rate Limiting](docs/security/DDOS_PROTECTION_IMPLEMENTED.md) — what's actually implemented; per-route rate limit table
* [Config Service Security Specification](backend/config-service/SECURITY.md)

### Microservice Subsystems
* [API Gateway](backend/api-gateway/README.md) · [Route Catalog](backend/api-gateway/ROUTES.md)
* [Movie Catalog Service](backend/movie-service/README.md)
* [User & Authentication Service](backend/user-service/README.md)
* [Actor & Cast Service](backend/actor-service/README.md)
* [AI Assistant & Vector Service](backend/ai-service/README.md)
* [Media Asset Service](backend/media-service/README.md)
* [Discovery Service (Eureka)](backend/discovery-service/README.md)
* [Config Service](backend/config-service/README.md)
* [Shared Java Library](backend/shared-library/README.md)
* [Frontend Application](frontend/lmdb/README.md)
* [End-to-End Playwright Suite](e2e/README.md)

### Project Roadmap & Backlog
GitHub Issues on `liviuionesi/lmdb.dev` are the authoritative backlog. [Project Roadmap & Narrative](.github/issues/PROJECT_ROADMAP.md) gives the phase-level story; [Phase 4 spec](.github/issues/PHASE4_ADVANCED_SERVICES.md) is still referenced by the roadmap. Phases 1–3's original seed specs are archived in [`docs/archive/`](docs/archive/) once superseded by the live issues they generated.

### GitHub Governance & Templates
* [Branch Protection Standards](.github/BRANCH_PROTECTION.md) · [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md)
* [Epic](.github/ISSUE_TEMPLATE/epic.md) · [User Story](.github/ISSUE_TEMPLATE/user-story.md) · [Task](.github/ISSUE_TEMPLATE/task.md) · [Bug](.github/ISSUE_TEMPLATE/bug.md)

### Autonomous Agent Contracts
* [Autonomous Work Contract](CLAUDE.md) · [Sub-contract](.claude/CLAUDE.md)
* [Codemod](.claude/skills/codemod/SKILL.md) · [Javadoc Quality](.claude/skills/javadoc/SKILL.md) · [Task Resync](.claude/skills/resync-tasks/SKILL.md)

### Architecture Knowledge Graph (Graphify)
* [Latest Report](graphify-out/GRAPH_REPORT.md) — dated snapshots also exist under `graphify-out/<date>/`

---

## 12. Data Attribution & TMDB Compliance

Movie metadata, plot synopses, cast filmographies, poster artwork, and trailer references are provided by **[The Movie Database (TMDB)](https://www.themoviedb.org/)** via their public v3 API.

<p align="left">
  <a href="https://www.themoviedb.org/">
    <img src="https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg" alt="The Movie Database (TMDB) Logo" width="120" />
  </a>
</p>

> **Mandatory TMDB Legal Notice:**
> *"This product uses the TMDB API but is not endorsed or certified by TMDB."*

LMDB does not act as a pass-through proxy. It implements a self-populating catalog architecture ([ADR-010](docs/architecture/adr/010-tmdb-facade-mapped-persisted-schema.md), [ADR-011](docs/architecture/adr/011-self-healing-read-through-on-schema-drift.md)): external payloads are mapped to typed local documents on first request, hot queries are cached in Redis, and synopses/genres feed the local `pgvector` database powering semantic search — all at $0 external API cost.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
