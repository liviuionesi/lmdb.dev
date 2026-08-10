# Filmpire — Enterprise Microservices Platform & Multi-Cloud Architecture

[![Backend CI](https://github.com/pehlivanu/filmpire-microservices/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/pehlivanu/filmpire-microservices/actions/workflows/backend-ci.yml)
[![Frontend CI](https://github.com/pehlivanu/filmpire-microservices/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/pehlivanu/filmpire-microservices/actions/workflows/frontend-ci.yml)
[![Docker Publish](https://github.com/pehlivanu/filmpire-microservices/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/pehlivanu/filmpire-microservices/actions/workflows/docker-publish.yml)
[![Terraform Plan](https://github.com/pehlivanu/filmpire-microservices/actions/workflows/terraform-plan.yml/badge.svg)](https://github.com/pehlivanu/filmpire-microservices/actions/workflows/terraform-plan.yml)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot 4.1.0](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud 2025.1.2](https://img.shields.io/badge/Spring%20Cloud-2025.1.2-blue.svg)](https://spring.io/projects/spring-cloud)
[![React 18 / Vite](https://img.shields.io/badge/React-18%20%7C%20Vite-61dafb.svg)](https://vitejs.dev/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> **A production-grade, event-driven movie streaming & recommendation microservices platform.** Featuring an AI assistant powered by Spring AI & Ollama (LLaMA 3.2), semantic vector search (pgvector), offline speech-to-text voice control (Vosk), a self-healing TMDB v3 API facade, 7 layers of automated testing, full observability (ELK, Zipkin, Prometheus, Grafana), and automated multi-cloud deployment to Azure AKS and AWS k3s.

🌐 **Live Demo (Frontend):** [filmpire-microservices-tan.vercel.app](https://filmpire-microservices-tan.vercel.app/)  
*(The deployed frontend uses dynamic runtime auto-discovery to automatically bind to whichever cloud backend is live.)*

---

## Table of Contents

1. [Executive Overview & Capabilities](#1-executive-overview--capabilities)
2. [Feature Catalog & Technical Implementation](#2-feature-catalog--technical-implementation)
3. [SDLC Story: From Idea to Finished Product](#3-sdlc-story-from-idea-to-finished-product)
4. [System Architecture & Data Flows](#4-system-architecture--data-flows)
5. [Codebase Organization & Repository Topology](#5-codebase-organization--repository-topology)
6. [The 7-Layer Testing Strategy](#6-the-7-layer-testing-strategy)
7. [Multi-Cloud Deployment & Infrastructure Topology](#7-multi-cloud-deployment--infrastructure-topology)
8. [Observability, Logging & Telemetry](#8-observability-logging--telemetry)
9. [CI/CD Pipelines & Code Management](#9-cicd-pipelines--code-management)
10. [Local Quick Start Guide](#10-local-quick-start-guide)
11. [Master Documentation Index](#11-master-documentation-index)

---

## 1. Executive Overview & Capabilities

Filmpire is an end-to-end cloud-native microservices ecosystem that transforms a movie catalog frontend into a full-scale distributed streaming and discovery platform. Rather than acting as a simple proxy to third-party APIs, Filmpire operates an independent, self-populating data platform with polyglot persistence, local AI inference, asynchronous event streaming, and multi-cloud orchestration.

```
React 18 / Vite SPA (Vercel)
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

* **Self-Healing Data Ingestion:** TMDB v3 API facade transparently persists external movie/actor records on first request and automatically repairs local schema drift upon subsequent reads ([ADR-010](docs/architecture/adr/010-tmdb-facade-mapped-persisted-schema.md), [ADR-011](docs/architecture/adr/011-self-healing-read-through-on-schema-drift.md)).
* **Local-First AI ($0 API Cost):** AI chat assistant, semantic vector search, and content-based recommendations powered entirely by Spring AI, Ollama (`llama3.2` and `nomic-embed-text`), and `pgvector` with zero paid API dependencies ([ADR-012](docs/architecture/adr/012-ai-service-postgresql-pgvector.md)).
* **Offline Voice Control:** Speech-to-text recognition operating entirely within the container via an embedded Vosk C++ native model (`vosk-model-small-en-us-0.15`).
* **Multi-Cloud Parity ($0 Cost Model):** Ephemeral, reproducible cloud infrastructure codified in Terraform for both Azure AKS and AWS k3s on EC2 with automated teardown and budget guards ([ADR-004](docs/architecture/adr/004-zero-budget-cloud-strategy.md), [ADR-017](docs/architecture/adr/017-full-cloud-service-parity.md)).
* **Dynamic Backend Resolution:** Single frontend build on Vercel dynamically discovers, health-checks, and binds to active cloud backends via GitHub raw pointer resolution without rebuilds ([ADR-016](docs/architecture/adr/016-dynamic-backend-resolution.md)).

---

## 2. Feature Catalog & Technical Implementation

| Feature Area | Microservice | Storage / Tech | Implementation Details |
|---|---|---|---|
| **Movie Catalog & Browse** | [`movie-service`](backend/movie-service/) | MongoDB 8.0, Redis 7.4 | TMDB facade routes (`/movie/**`, `/genre/**`, `/discover/**`). Maps external payloads into typed documents, caches hot queries in Redis, and persists records locally. |
| **User Authentication & Profiles** | [`user-service`](backend/user-service/) | PostgreSQL 17, JPA/Hibernate | BCrypt password hashing, signed JWT access/refresh token lifecycle, user profiles, favorite movies, and watchlist management. |
| **Actor Biographies & Credits** | [`actor-service`](backend/actor-service/) | PostgreSQL 17, JPA/Hibernate | Actor profile retrieval, filmography mapping, and person discovery routes (`/person/**`, `/search/person`). |
| **AI Assistant & Semantic Search** | [`ai-service`](backend/ai-service/) | PostgreSQL + pgvector, Ollama | Contextual movie chat via LLaMA 3.2, natural language semantic search using vector embeddings (`nomic-embed-text`), and taste-profile recommendations. |
| **Voice Navigation** | [`ai-service`](backend/ai-service/) | Vosk native library | Speech-to-text audio processing (`POST /api/v1/ai/speech-to-text`). Translates spoken commands into category navigation, searches, and UI actions. |
| **Media Asset Management** | [`media-service`](backend/media-service/) | MinIO (S3 API), MongoDB 8.0 | Multipart image upload, binary chunking, metadata indexing, and presigned asset streaming. |
| **Traffic Control & Edge Routing** | [`api-gateway`](backend/api-gateway/) | Spring Cloud Gateway, Redis | Redis token-bucket rate limiting, Resilience4j circuit breakers, JWT validation filter, CORS origin pattern matching, and URL rewrites. |
| **Web Client Application** | [`frontend/filmpire`](frontend/filmpire/) | React 18, Vite, Redux Toolkit, MUI | Responsive UI, dark/light theme toggle, speech recognition controller, movie trailer modal, and dynamic backend resolver. |

---

## 3. SDLC Story: From Idea to Finished Product

Filmpire was engineered following a strict **Agile/Scrum** software development lifecycle, prioritizing architectural integrity, rigorous quality gates, and automated validation at every phase.

```
┌─────────────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  1. Inception   │ ──► │  2. Architecture │ ──► │ 3. Quality Gates │ ──► │ 4. Delivery &    │
│  & Requirements │     │  & Design (ADRs) │     │    & Testing     │     │    Multi-Cloud   │
└─────────────────┘     └──────────────────┘     └──────────────────┘     └──────────────────┘
 • Product Goal          • 17 Recorded ADRs       • Unit & Integration     • Terraform Infra
 • User Stories          • Microservices & Hex    • Contract Testing       • K8s Kustomize
 • Given/When/Then AC    • Polyglot Storage       • Playwright E2E         • Ephemeral Clusters
 • Definition of Done    • OpenAPI Contracts      • SonarQube & Spotless   • Dynamic Discovery
```

### Phase 1: Inception & Backlog Grooming
* **Product Goal Formulation:** Established core requirements for an independent, AI-augmented movie platform ([PRODUCT_GOAL.md](docs/process/PRODUCT_GOAL.md)).
* **Scrum Methodology:** Backlog organized into formal **Epics → User Stories → Technical Tasks** with strict acceptance criteria formulated in `Given / When / Then` syntax ([METHODOLOGY.md](docs/process/METHODOLOGY.md)).
* **Quality Contracts:** Enforced strict [Definition of Ready (DoR)](docs/process/DEFINITION_OF_READY.md) and [Definition of Done (DoD)](docs/process/DEFINITION_OF_DONE.md) standards before any story was promoted to `main`.

### Phase 2: Architecture & Decision Records
Every significant architectural choice, pivot, and rejected alternative was formally recorded as a numbered Architectural Decision Record (ADR):
* [ADR-001: Microservices Architecture](docs/architecture/adr/001-microservices-architecture.md) — 8 distinct bounded contexts vs. monolith.
* [ADR-002: Database Per Service](docs/architecture/adr/002-database-per-service.md) — Polyglot persistence (PostgreSQL, MongoDB, Redis, MinIO).
* [ADR-005: Eureka/Config vs. Native K8s](docs/architecture/adr/005-eureka-config-vs-kubernetes-native.md) — Cloud overlays leverage native K8s DNS and ConfigMaps.
* [ADR-010: Facade-Mapped Persisted Schema](docs/architecture/adr/010-tmdb-facade-mapped-persisted-schema.md) — Self-populating local catalog.
* [ADR-012: PostgreSQL + pgvector for AI](docs/architecture/adr/012-ai-service-postgresql-pgvector.md) — Local vector storage for RAG and embeddings.
* [ADR-016: Dynamic Backend Resolution](docs/architecture/adr/016-dynamic-backend-resolution.md) — Seamless single-SPA cloud binding.
* [ADR-017: Full Cloud Service Parity](docs/architecture/adr/017-full-cloud-service-parity.md) — Zero-discrepancy cloud deployments.
*(See [Master Documentation Index](#11-master-documentation-index) for all 17 ADRs).*

### Phase 3: Implementation & Clean Code Standards
* **Java 25 & Spring Boot 4.1:** Multi-module Gradle build using modern Java features (records, pattern matching, virtual threads).
* **Shared Library Abstraction:** Common `shared-library` module containing unified `ApiResponse<T>` wrappers, standard exception hierarchies, and distributed tracing interceptors.
* **Javadoc & Inline Documentation:** 100% documentation contract covering all classes, methods, and test fixtures explaining architectural rationale.

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
  ├── Filter: Redis Token Bucket Rate Limiting (10 req/s, burst 20)
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
  ├── 2. Perform Cosine Similarity Search in PostgreSQL (pgvector cosine operator `<=>`)
  ├── 3. Retrieve Top-K Matching Movie Overviews & Metadata
  ├── 4. Construct Augmented Prompt with Movie Context
  └── 5. Stream LLM Response from Ollama ('llama3.2') back to Gateway/Client
```

---

## 5. Codebase Organization & Repository Topology

```
filmpire-microservices/
├── backend/                               # 8 Java 25 / Spring Boot 4 Microservices
│   ├── shared-library/                    # Common DTOs, security filters, error handlers, tracing
│   ├── api-gateway/                       # Spring Cloud Gateway WebFlux, Redis rate limiter, CORS
│   ├── discovery-service/                 # Netflix Eureka server (local dev profile)
│   ├── config-service/                    # Spring Cloud Config server (centralized config)
│   ├── movie-service/                     # TMDB facade, catalog persistence, MongoDB, Redis
│   ├── user-service/                      # User auth, JWT issuance, profile, PostgreSQL
│   ├── actor-service/                     # Actor biographies & filmographies, PostgreSQL
│   ├── ai-service/                        # Spring AI, pgvector embeddings, Ollama, Vosk STT
│   └── media-service/                     # Media asset management, MinIO S3 object storage
│
├── frontend/                              # Frontend Web Application
│   └── filmpire/                          # React 18, Vite, Redux Toolkit Query, MUI v5
│
├── infrastructure/                        # Multi-Cloud & Local Infrastructure as Code
│   ├── docker/                            # Docker Compose full-stack topology (15 containers)
│   ├── kubernetes/                        # K8s Manifests (Kustomize)
│   │   ├── base/                          # Base deployments, services, statefulsets
│   │   ├── overlays/                      # Environment overlays: local, azure, aws
│   │   └── monitoring/                    # Prometheus, Grafana, ServiceMonitors
│   ├── terraform/                         # Infrastructure as Code
│   │   ├── azure/                         # Azure AKS, VNet, Subnets, NSGs, Budget Tripwires
│   │   ├── aws/                           # AWS EC2 k3s cluster, VPC, Security Groups
│   │   └── modules/                       # Reusable cloud modules (network, compute, budget)
│   └── scripts/                           # Automation scripts (deploy, destroy, status, tunnel)
│
├── e2e/                                   # Playwright browser acceptance test suite
├── docs/                                  # Comprehensive technical documentation
│   ├── architecture/                      # Full ARCHITECTURE.md spec + 17 ADRs
│   ├── process/                           # Scrum framework, DoR, DoD, NFRs, Product Goals
│   ├── guides/                            # Deployment runbooks, local quick starts
│   └── security/                          # DDoS protection & security architecture
│
├── .github/                               # CI/CD Workflows & Issue Templates
│   ├── workflows/                         # GitHub Actions pipelines (CI, CD, Docker, Terraform)
│   └── ISSUE_TEMPLATE/                    # Agile issue templates (Epic, Story, Task, Bug)
└── build.gradle                           # Root multi-project Gradle build configuration
```

---

## 6. The 7-Layer Testing Strategy

Filmpire implements a comprehensive testing pyramid encompassing 7 distinct testing disciplines, ensuring zero regressions across backend services, distributed data flows, contract boundaries, and frontend user journeys.

```
                  ┌──────────────────────┐
                  │ 7. Load / Stress     │  Gatling (HTTP latency & circuit breaker trip)
                  ├──────────────────────┤
                  │ 6. API Regression    │  Postman / Newman Collections
                  ├──────────────────────┤
                  │ 5. Browser E2E       │  Playwright (Multi-browser user journeys)
                  ├──────────────────────┤
                  │ 4. Frontend UI/State │  Vitest 4, React Testing Library (180+ tests)
                  ├──────────────────────┤
                  │ 3. Contract Tests    │  Spring Cloud Contract (Consumer-Driven Contracts)
                  ├──────────────────────┤
                  │ 2. Integration Tests │  Testcontainers (Real Postgres, MongoDB, Redis)
                  ├──────────────────────┤
                  │ 1. Unit Tests        │  JUnit 5, Mockito, AssertJ (Isolated business logic)
                  └──────────────────────┘
```

### Test Suite Execution Commands

```bash
# Level 1 & 2: Unit and Testcontainers Integration Tests (all backend modules)
./gradlew test

# Run tests for a specific microservice
./gradlew :backend:movie-service:test
./gradlew :backend:ai-service:test

# Level 3: Consumer-Driven Contract Verification
./gradlew :backend:api-gateway:contractTest

# Level 4: Frontend Component & State Tests (Vitest)
cd frontend/filmpire && npm test

# Frontend test coverage report
cd frontend/filmpire && npm run test:coverage

# Level 5: Playwright Browser End-to-End Tests
cd e2e && npx playwright test

# Level 6: Automated Postman/Newman API Smoke Test (requires running stack)
newman run docs/api/Filmpire_API.postman_collection.json -e docs/api/local_environment.json

# Level 7: Gatling Performance & Load Simulation
./gradlew :backend:api-gateway:gatlingRun
```

---

## 7. Multi-Cloud Deployment & Infrastructure Topology

Filmpire supports four fully-codified deployment topologies with 100% feature and service parity:

```
┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                                 DEPLOYMENT TOPOLOGIES                                                  │
├────────────────────────────┬────────────────────────────┬─────────────────────────────┬────────────────────────────────┤
│ 1. Local Dev Stack         │ 2. Local Stack + Tunnel    │ 3. Azure AKS (Terraform)    │ 4. AWS k3s (Terraform)         │
├────────────────────────────┼────────────────────────────┼─────────────────────────────┼────────────────────────────────┤
│ • Full 15-container stack  │ • Local stack / Minikube   │ • Managed AKS Cluster       │ • Lightweight k3s on EC2       │
│ • Docker / Podman Compose  │ • Cloudflare HTTPS Tunnel  │ • Standard_D4ls_v7 node     │ • t3.xlarge instance           │
│ • Minikube local overlay   │ • Powers deployed Vercel FE│ • Native K8s DNS & Config   │ • Native K8s DNS & Config      │
│ • Eureka + Config Server   │ • Auto-published pointer   │ • Budget tripwire guard ($1)│ • Zero-spend teardown          │
│ • Command:                 │ • Command:                 │ • Command:                  │ • Command:                     │
│   ./gradlew deployLocal    │   ./gradlew startTunnel    │   ./gradlew deployAzure     │   ./gradlew deployAws          │
└────────────────────────────┴────────────────────────────┴─────────────────────────────┴────────────────────────────────┘
```

### Topology Breakdown

1. **Local Development (Compose & Minikube):**
   * Runs the complete 15-container stack locally via Docker Compose, Podman Compose, or the local Kubernetes overlay ([`infrastructure/kubernetes/overlays/local`](infrastructure/kubernetes/overlays/local)).
   * Includes internal infrastructure helpers: Eureka (`discovery-service`), Config Server, Kafka, Zipkin, MinIO, and database UIs (Adminer, Mongo-Express, Redis-Commander).
   * Used for daily offline development against `localhost:5173` / `localhost:3000`.

2. **Local Machine / Minikube + Live Cloudflare Tunnel (Powers Vercel FE):**
   * Allows the public **Vercel frontend** (`https://filmpire-microservices-tan.vercel.app`) to communicate directly with your local developer machine or Minikube cluster with $0 cloud spend.
   * `start-tunnel.sh` launches a secure, encrypted Cloudflare quick tunnel (`https://*.trycloudflare.com`) pointing to your local Gateway (`:8080`), automatically captures the generated HTTPS hostname, commits and pushes it to [`infrastructure/tunnel-url.txt`](infrastructure/tunnel-url.txt) on GitHub `develop`.
   * The Vercel frontend automatically discovers the updated tunnel pointer via GitHub raw URL within seconds without requiring frontend rebuilds or manual environment variable updates.

3. **Azure AKS Managed Kubernetes:**
   * Automated provisioning via Terraform ([`infrastructure/terraform/azure`](infrastructure/terraform/azure)) on a single `Standard_D4ls_v7` node with Azure CNI and NSG NodePort `30080`.
   * Deployed via Kustomize overlay ([`infrastructure/kubernetes/overlays/azure`](infrastructure/kubernetes/overlays/azure)). Protected by a strict $1 budget guard tripwire.

4. **AWS k3s on EC2:**
   * Automated provisioning via Terraform ([`infrastructure/terraform/aws`](infrastructure/terraform/aws)) standing up a lightweight k3s cluster on a `t3.xlarge` EC2 instance.
   * Deployed via Kustomize overlay ([`infrastructure/kubernetes/overlays/aws`](infrastructure/kubernetes/overlays/aws)).

### One-Command Deployment Automation

All infrastructure actions are wrapped into unified Gradle tasks and shell automation:

```bash
# 1. Local Compose / Minikube development
./gradlew deployLocal

# 2. Expose local backend to the deployed Vercel frontend via Cloudflare Tunnel
./gradlew startTunnel
# ...or start local stack and tunnel together:
./gradlew deployLocal --args='--tunnel'

# Stop the active Cloudflare tunnel
./gradlew stopTunnel

# 3. Check status of local containers, active tunnel, or cloud cluster
./gradlew statusInfra

# 4. Deploy full production stack to Azure AKS via Terraform
./gradlew deployAzure

# 5. Deploy full production stack to AWS EC2 k3s via Terraform
./gradlew deployAws

# 6. Teardown cloud environments immediately to guarantee $0 spend
./gradlew destroyAzure
./gradlew destroyAws
```

### Dynamic Runtime Frontend-to-Backend Binding
The frontend on Vercel resolves its active backend dynamically at request time ([`apiUrl.js`](frontend/filmpire/src/utils/apiUrl.js)):
1. Checks for a manual override in `localStorage` (`filmpire_api_url`).
2. Checks for a build-time `VITE_API_URL`.
3. Resolves the live Cloudflare HTTPS tunnel pointer from GitHub ([`infrastructure/tunnel-url.txt`](infrastructure/tunnel-url.txt)).
4. Verifies candidate reachability via `/actuator/health`.
5. Automatically routes all RTK Query requests to the reachable backend with zero manual Vercel dashboard steps.

---

## 8. Observability, Logging & Telemetry

Filmpire incorporates a complete observability stack spanning structured logging, distributed tracing, and real-time metric visualization.

```
                                  TELEMETRY PIPELINE
                                  
 [Microservices] ──JSON stdout──► [Filebeat] ──► [Logstash] ──► [Elasticsearch] ──► [Kibana :5601]
       │                                                                               (Log Analysis)
       ├──Micrometer B3 Tracing──► [OpenZipkin :9411]
       │                               (Distributed Trace Spans & Latency Graph)
       └──Prometheus Metrics (/actuator/prometheus) ──► [Prometheus :9090] ──► [Grafana :3001]
                                                                                (Health Dashboards)
```

* **Structured Logging:** Standardized JSON log output via Logback and `LogstashEncoder` including timestamps, log levels, service names, thread identifiers, and active correlation IDs.
* **Distributed Tracing:** Micrometer Tracing with OpenZipkin propagators injecting `traceId` and `spanId` headers across HTTP calls and Kafka events for end-to-end request latency profiling.
* **Metrics & Dashboards:** Spring Boot Actuator endpoints scraped by Prometheus every 15 seconds, rendering real-time JVM memory, CPU utilization, HTTP latency percentiles, and Resilience4j circuit breaker state in pre-built Grafana dashboards.

---

## 9. CI/CD Pipelines & Code Management

### Automated GitHub Actions Workflows

| Pipeline | Trigger | Responsibilities |
|---|---|---|
| [`backend-ci.yml`](.github/workflows/backend-ci.yml) | Push/PR to `main`, `develop` | Compiles Java 25 modules, executes Spotless/Checkstyle, runs JUnit & Testcontainers suites, verifies SonarQube Quality Gate. |
| [`frontend-ci.yml`](.github/workflows/frontend-ci.yml) | Push/PR to `main`, `develop` | Runs ESLint, Prettier, and Vitest component suite with code coverage validation. |
| [`docker-publish.yml`](.github/workflows/docker-publish.yml) | Chained to green Backend CI on `main` | Builds multi-stage container images for all 8 microservices and publishes to GitHub Container Registry (`ghcr.io/pehlivanu/filmpire-*`). |
| [`terraform-plan.yml`](.github/workflows/terraform-plan.yml) | Changes to `infrastructure/terraform/**` | Validates HCL syntax, runs `terraform fmt`, and generates speculative execution plans for Azure and AWS. |

### Code Quality & Maintenance Tooling

* **Automated Code Modernization (OpenRewrite):** Automated recipes for Spring Boot upgrades and clean code patterns:
  ```bash
  ./gradlew rewriteRun
  ```
* **Git Commit Enforcement:** A pre-commit hook (`.githooks/pre-commit`) blocks broken builds, while a commit-msg hook (`.githooks/commit-msg`) enforces semantic commit formats linking active GitHub issues (`feat: Add AI recommendation (#36)`).
* **Architecture Knowledge Graph (Graphify):** Generates and maintains a full AST knowledge graph in `graphify-out/` mapping cross-service dependencies and coupling.

---

## 10. Local Quick Start Guide

### Prerequisites
* Java 25 JDK (Temurin recommended)
* Node.js 20+ and npm
* Docker 24+ or Podman 5+ with Compose support

### Step-by-Step Launch

```bash
# 1. Clone the repository
git clone https://github.com/pehlivanu/filmpire-microservices.git
cd filmpire-microservices

# 2. Configure environment variables
cp infrastructure/docker/.env.example infrastructure/docker/.env
# (Optional: Add your free TMDB_API_KEY from themoviedb.org/settings/api)

# 3. Launch the complete local microservices infrastructure
./gradlew deployLocal

# 4. Pull Ollama AI models for chat and embeddings (one-time setup)
docker exec -it filmpire-ollama ollama pull llama3.2
docker exec -it filmpire-ollama ollama pull nomic-embed-text

# 5. Start the React/Vite development server
cd frontend/filmpire
npm install
npm run dev
```

Visit **`http://localhost:5173`** (or `http://localhost:3000`) in your browser to interact with the application.

---

## 11. Master Documentation Index

### Architecture & Technical Guides
* [System Architecture Specification (ARCHITECTURE.md)](docs/architecture/ARCHITECTURE.md) — Comprehensive 2,700+ line technical architecture
* [Port & Network Mapping Guide](docs/architecture/PORT_MAPPING.md) — Service port allocation, databases, and mesh routing
* [Code Quality & Static Analysis Guidelines](docs/architecture/CODE_QUALITY.md) — SonarQube, Checkstyle, Spotless standards
* [Gradle Multi-Module Build Architecture](docs/architecture/GRADLE_BUILD_SETUP.md) — Multi-project dependency graphs & build lifecycle
* [Docker & Local Infrastructure Guide](docs/architecture/DOCKER_INFRASTRUCTURE_SETUP.md) — Container topology and Compose wiring
* [Integration Testing Strategy & Testcontainers](docs/architecture/INTEGRATION_TESTING.md) — Testcontainers, WireMock, and mock policies
* [Junior Developer Onboarding Guide](docs/architecture/JUNIOR_DEVELOPER_GUIDE.md) — Development workflow, debugging, and setup

### Architectural Decision Records (ADRs)
* [ADR-001: Microservices Architecture Selection](docs/architecture/adr/001-microservices-architecture.md)
* [ADR-002: Database Per Service Architecture](docs/architecture/adr/002-database-per-service.md)
* [ADR-003: TMDB Raw Passthrough Facade](docs/architecture/adr/003-tmdb-raw-passthrough-facade.md)
* [ADR-004: Zero-Budget Multi-Cloud Strategy](docs/architecture/adr/004-zero-budget-cloud-strategy.md)
* [ADR-005: Eureka/Config vs. Native Kubernetes](docs/architecture/adr/005-eureka-config-vs-kubernetes-native.md)
* [ADR-006: Apache Kafka Event Bus](docs/architecture/adr/006-kafka-event-bus.md)
* [ADR-007: Distributed Tracing with OpenZipkin](docs/architecture/adr/007-distributed-tracing-zipkin.md)
* [ADR-008: Consumer-Driven Contract Testing](docs/architecture/adr/008-contract-testing.md)
* [ADR-009: OpenRewrite Spring Boot Modernization](docs/architecture/adr/009-openrewrite-spring-boot-4-migration.md)
* [ADR-010: Facade-Mapped Persisted Catalog Schema](docs/architecture/adr/010-tmdb-facade-mapped-persisted-schema.md)
* [ADR-011: Self-Healing Read-Through on Schema Drift](docs/architecture/adr/011-self-healing-read-through-on-schema-drift.md)
* [ADR-012: AI Service with PostgreSQL & pgvector](docs/architecture/adr/012-ai-service-postgresql-pgvector.md)
* [ADR-013: Frontend Monorepo Integration](docs/architecture/adr/013-frontend-merged-into-monorepo.md)
* [ADR-014: Media Service with S3 and MongoDB](docs/architecture/adr/014-media-service-s3-mongo-storage.md)
* [ADR-015: Operator-Controlled Local Deploy Triggers](docs/architecture/adr/015-local-only-deploy-trigger.md)
* [ADR-016: Dynamic Runtime Backend Resolution](docs/architecture/adr/016-dynamic-backend-resolution.md)
* [ADR-017: Full Service Cloud Parity](docs/architecture/adr/017-full-cloud-service-parity.md)

### Scrum Process & SDLC
* [Product Goal & Strategic Vision](docs/process/PRODUCT_GOAL.md)
* [Agile Methodology & Scrum Process](docs/process/METHODOLOGY.md)
* [Definition of Ready (DoR)](docs/process/DEFINITION_OF_READY.md)
* [Definition of Done (DoD)](docs/process/DEFINITION_OF_DONE.md)
* [Non-Functional Requirements (NFRs)](docs/process/NON_FUNCTIONAL_REQUIREMENTS.md)
* [Scrum Events & Ceremony Protocol](docs/process/SCRUM_EVENTS.md)

### Deployment, Operations & Infrastructure
* [Multi-Cloud Deployment Runbook](docs/guides/DEPLOYMENT_GUIDE.md) — Local, Azure AKS, AWS k3s, and Vercel setup
* [Frontend Integration & Execution Guide](docs/guides/RUN_WITH_FILMPIRE_APP.md) — Frontend runtime configuration
* [Terraform Multi-Cloud Infrastructure](infrastructure/terraform/README.md) — Azure AKS and AWS EC2 Terraform modules
* [Docker Compose Testing Runbook](infrastructure/docker/TESTING_GUIDE.md) — Local Compose verification workflows
* [Kubernetes Prometheus ServiceMonitors](infrastructure/kubernetes/monitoring/service-monitors/README.md) — Monitoring scrape configs

### Security Architecture & Threat Mitigation
* [DDoS Mitigation & Rate Limiting Architecture](docs/security/DDOS_PROTECTION_IMPLEMENTED.md) — Redis rate limiter implementation
* [Security Enhancements & Threat Model](docs/security/DDOS_PROTECTION_IMPROVEMENTS.md) — Defense-in-depth architecture
* [Config Service Security Specification](backend/config-service/SECURITY.md) — Encryption and credential management

### Microservice Subsystems & Module Documentation
* [API Gateway Service](backend/api-gateway/README.md) · [Gateway Route Catalog](backend/api-gateway/ROUTES.md)
* [Movie Catalog Service](backend/movie-service/README.md) · [Test Execution Results](backend/movie-service/TEST_EXECUTION_RESULTS.md) · [Test Summary](backend/movie-service/TEST_SUMMARY.md)
* [User & Authentication Service](backend/user-service/README.md)
* [Actor & Cast Service](backend/actor-service/README.md)
* [AI Assistant & Vector Service](backend/ai-service/README.md)
* [Media Asset Service](backend/media-service/README.md)
* [Discovery Service (Eureka)](backend/discovery-service/README.md)
* [Config Service](backend/config-service/README.md)
* [Shared Java Library](backend/shared-library/README.md)
* [Frontend Application](frontend/filmpire/README.md) · [Next.js Migration Notes](frontend/filmpire/nextjs.md)
* [End-to-End Playwright Suite](e2e/README.md)

### Project Roadmap & Sprint Backlogs
* [Project Roadmap & Narrative](.github/issues/PROJECT_ROADMAP.md)
* [Phase 1: Project Setup & Architecture Backlog](.github/issues/PHASE1_ISSUES.md)
* [Phase 2: Infrastructure Services Backlog](.github/issues/PHASE2_INFRASTRUCTURE_SERVICES.md)
* [Phase 3: Core Business Services Backlog](.github/issues/PHASE3_CORE_SERVICES.md)
* [Phase 4: Advanced AI & Media Services Backlog](.github/issues/PHASE4_ADVANCED_SERVICES.md)
* [Phase 5: Web Frontend Integration Backlog](.github/issues/PHASE5_WEB_FRONTEND.md)
* [Phases 6–8: Mobile, Performance & Multi-Cloud Backlog](.github/issues/PHASES_6-8_MOBILE_TESTING_DEPLOYMENT.md)

### GitHub Governance & Issue Templates
* [Branch Protection Standards](.github/BRANCH_PROTECTION.md)
* [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md)
* [Epic Issue Template](.github/ISSUE_TEMPLATE/epic.md)
* [User Story Issue Template](.github/ISSUE_TEMPLATE/user-story.md)
* [Task Issue Template](.github/ISSUE_TEMPLATE/task.md)
* [Bug Issue Template](.github/ISSUE_TEMPLATE/bug.md)

### Developer Agent & Autonomous Workflow Contracts
* [Autonomous Work Contract (CLAUDE.md)](CLAUDE.md) · [Sub-contract (.claude/CLAUDE.md)](.claude/CLAUDE.md)
* [Codemod Command Guide](.claude/commands/codemod.md)
* [Codemod Skill Specification](.claude/skills/codemod/SKILL.md)
* [Javadoc Quality Skill Specification](.claude/skills/javadoc/SKILL.md)
* [Task Resync Skill Specification](.claude/skills/resync-tasks/SKILL.md)

### Architecture Knowledge Graph Reports (Graphify)
* [Latest Knowledge Graph Report](graphify-out/GRAPH_REPORT.md)
* [Snapshot: 2026-08-10 Architecture Report](graphify-out/2026-08-10/GRAPH_REPORT.md)
* [Snapshot: 2026-08-05 Architecture Report](graphify-out/2026-08-05/GRAPH_REPORT.md)
* [Snapshot: 2026-08-04 Architecture Report](graphify-out/2026-08-04/GRAPH_REPORT.md)

### Historical Evolution & Archive
* [Historical Architecture Archive](docs/archive/README.md)
* [Legacy Agile Workflow Guide](docs/archive/AGILE_WORKFLOW_GUIDE.md)
* [Legacy Project Setup Runbook](docs/archive/PROJECT_SETUP.md)
* [Legacy GitHub Setup Guide](docs/archive/GITHUB_SETUP.md)
* [Legacy GitHub Project Overview](docs/archive/GITHUB_PROJECT_README.md)
* [Legacy SonarQube Configuration](docs/archive/SONAR_CONFIGURATION.md)
* [Historical Cursor AI Prompts](docs/archive/CURSOR_PROMPTS.md)

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
