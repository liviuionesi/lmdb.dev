# 📊 LMDB Microservices — Project Analytics & Metrics Report

> **Dynamically Generated:** `2026-09-21 18:56:04 UTC`  
> **Git Status:** Branch `develop` | Commit [`f0ca1e8`](https://github.com/liviuionesi/lmdb.dev/commit/f0ca1e8)  
> **Auto-Update Trigger:** Executes automatically on each push and via `./gradlew projectStats`

---

## 🌟 Key Performance Indicators (KPIs)

| Metric | Value | Category | Significance |
|---|---|---|---|
| **Total Git Commits** | **761** | Git Velocity | Evolutionary depth across 2.5+ years of active development |
| **Total Code Churn** | **577,724 LOC** (+341,035 / -236,689) | Git Velocity | Continuous refactoring and enterprise hardening |
| **Total Codebase Lines** | **88,079 LOC** (61,509 code / 16,892 comments) | Codebase Volume | Full-stack polyglot microservice ecosystem |
| **Total Automated Tests** | **1,157 Tests** (855 Backend + 302 Frontend) | Quality & Reliability | 100% Green Unit, Slice, Contract & Integration suites |
| **Total REST Endpoints** | **66 Endpoints** (42 GET, 17 POST, 2 PUT, 5 DELETE) | API Surface | Microservice REST surface exposed via API Gateway |
| **Database Migrations** | **12 Flyway SQL Scripts** | Persistence | Versioned, reproducible relational schemas |
| **Architecture Decisions** | **24 ADRs** Documented | Governance | Comprehensive decision records (ADR-001 through ADR-018) |
| **Cloud Deployment Targets** | **3 Targets** (Azure AKS, AWS EC2 k3s, Local Minikube) | Multi-Cloud | $0-budget tripwire protected infrastructure |
| **Known Vulnerabilities** | **0 CVEs** | Security | Proactive BOM security overrides in `gradle.properties` |

---

## 📈 1. Git Velocity & Lifecycle Churn

- **Development Timeline:** `2024-03-14 (2 years, 6 months ago)` ➔ `2026-09-21 (52 minutes ago)`
- **Total Commits:** `761`
- **Total Lines Added (+):** `341,035`
- **Total Lines Deleted / Refactored (-):** `236,689`
- **Total Churn Volume (Add + Del):** `577,724` lines processed
- **Net Repository Growth:** `+104,346` lines

---

## 💻 2. Codebase Distribution by Technology

| Technology / Language | Files | Code LOC | Comment LOC | Blank LOC | Total LOC | Share of Project |
|---|---|---|---|---|---|---|
| **Java (Spring Boot / gRPC)** | 314 | 25,477 | 14,048 | 4,551 | **44,076** | `[████████░░░░░░░]  50.0%` |
| **Documentation (Markdown)** | 63 | 10,652 | 1 | 2,657 | **13,310** | `[██░░░░░░░░░░░░░]  15.1%` |
| **JavaScript / React (JSX)** | 113 | 8,546 | 1,204 | 1,384 | **11,134** | `[██░░░░░░░░░░░░░]  12.6%` |
| **JSON Data** | 24 | 7,245 | 0 | 1 | **7,246** | `[█░░░░░░░░░░░░░░]   8.2%` |
| **Kubernetes & CI/CD (YAML)** | 82 | 4,639 | 809 | 264 | **5,712** | `[█░░░░░░░░░░░░░░]   6.5%` |
| **Shell Automation (Bash)** | 37 | 2,349 | 614 | 424 | **3,387** | `[█░░░░░░░░░░░░░░]   3.8%` |
| **Build & Config (Gradle/Properties)** | 18 | 1,442 | 50 | 204 | **1,696** | `[░░░░░░░░░░░░░░░]   1.9%` |
| **Terraform & Cloud (HCL)** | 28 | 835 | 157 | 142 | **1,134** | `[░░░░░░░░░░░░░░░]   1.3%` |
| **SQL & DB Migrations** | 6 | 145 | 0 | 20 | **165** | `[░░░░░░░░░░░░░░░]   0.2%` |
| **XML & HTML** | 4 | 136 | 6 | 20 | **162** | `[░░░░░░░░░░░░░░░]   0.2%` |
| **Protocol Buffers (Proto3)** | 1 | 31 | 3 | 8 | **42** | `[░░░░░░░░░░░░░░░]   0.0%` |
| **CSS & Styling** | 1 | 12 | 0 | 3 | **15** | `[░░░░░░░░░░░░░░░]   0.0%` |

---

## 🧩 3. Microservice & Module LOC Breakdown

| Microservice / Module | Files | Code LOC | Comment LOC | Total LOC | Share of Project |
|---|---|---|---|---|---|
| **`ai-service (Port 8084 / gRPC 9090)`** | 96 | 9,201 | 4,105 | **14,705** | `[███░░░░░░░░░░░░]  16.7%` |
| **`infrastructure (Terraform, K8s, Scripts)`** | 139 | 10,315 | 1,259 | **12,460** | `[██░░░░░░░░░░░░░]  14.1%` |
| **`frontend (React 19 / MUI 9 / Vite 8)`** | 116 | 8,767 | 1,155 | **11,336** | `[██░░░░░░░░░░░░░]  12.9%` |
| **`docs (Architecture, Guides, ADRs)`** | 43 | 9,581 | 1 | **10,993** | `[██░░░░░░░░░░░░░]  12.5%` |
| **`movie-service (Port 8081)`** | 77 | 6,692 | 2,902 | **10,725** | `[██░░░░░░░░░░░░░]  12.2%` |
| **`api-gateway (Port 8080)`** | 44 | 4,560 | 1,853 | **7,380** | `[█░░░░░░░░░░░░░░]   8.4%` |
| **`actor-service (Port 8083)`** | 38 | 3,102 | 1,773 | **5,414** | `[█░░░░░░░░░░░░░░]   6.1%` |
| **`shared-library (Common DTOs & Mappers)`** | 34 | 2,375 | 1,561 | **4,538** | `[█░░░░░░░░░░░░░░]   5.2%` |
| **`user-service (Port 8082)`** | 35 | 2,101 | 1,038 | **3,534** | `[█░░░░░░░░░░░░░░]   4.0%` |
| **`config-service (Spring Config 8888)`** | 26 | 1,396 | 623 | **2,366** | `[░░░░░░░░░░░░░░░]   2.7%` |
| **`media-service (Port 8085)`** | 17 | 884 | 291 | **1,322** | `[░░░░░░░░░░░░░░░]   1.5%` |
| **`discovery-service (Eureka 8761)`** | 10 | 486 | 258 | **867** | `[░░░░░░░░░░░░░░░]   1.0%` |
| **`e2e (Postman & Newman Regression)`** | 7 | 223 | 49 | **320** | `[░░░░░░░░░░░░░░░]   0.4%` |

---

## 🏗️ 4. Architecture & Object Topology

### Backend Architecture (Spring Boot & Java 25)
- **Total Java Type Declarations:** `222`
  - Classes (`public class`): `114`
  - Records (`public record` DTOs/Value Objects): `78`
  - Interfaces (`public interface` Contracts/Clients): `20`
  - Enums (`public enum`): `10`
- **REST Controllers:** `18` (`@RestController`)
- **Business Services & Handlers:** `35` (`@Service`)
- **Spring Data Repositories:** `10` (Postgres JPA + MongoDB)
- **Persistence Entities:** `14` (`@Entity` + `@Document`)
- **Flyway Database Migrations:** `12` versioned SQL migration scripts
- **Spring Cloud Contract Tests:** `8` stubs/verifier tests
- **gRPC & Protobuf Schemas:** `1` (`.proto`)

### Frontend Architecture (React 19, MUI 9, Redux Toolkit)
- **React Components:** `55` (`.jsx`)
- **Redux State Slices:** `4` (`createSlice`)
- **Custom React Hooks:** `7`

---

## 🧪 5. Testing & Quality Assurance Analytics

| Test Category | Test Count | Test Files | Tooling & Test Slices |
|---|---|---|---|
| **Backend Test Suite** | **855** | 121 | JUnit 5, Mockito, Testcontainers (Postgres/Mongo/Kafka), WireMock, Contract Verifier, Gatling |
| **Frontend Test Suite** | **302** | 41 | Vitest, React Testing Library, jsdom |
| **Combined Test Coverage** | **1,157** | 162 | **100% Passing Test Matrix** |

- **Test-to-Production Code Ratio:** `18.8` automated tests per 1,000 lines of production code.
- **Security & Dependency Centralization:** 100% of versions managed via `gradle.properties` with proactive CVE security overrides.

---

## 🏛️ 6. Architectural Decision Records (ADRs)

The repository includes **24 formal Architectural Decision Records** in `docs/architecture/adr/`:

| ADR ID & Title | Status | Scope |
|---|---|---|
| [`001-microservices-architecture`](../architecture/adr/001-microservices-architecture.md) | **Accepted** | Architecture Decision |
| [`002-database-per-service`](../architecture/adr/002-database-per-service.md) | **Accepted** | Architecture Decision |
| [`003-tmdb-raw-passthrough-facade`](../architecture/adr/003-tmdb-raw-passthrough-facade.md) | **Accepted** | Architecture Decision |
| [`004-zero-budget-cloud-strategy`](../architecture/adr/004-zero-budget-cloud-strategy.md) | **Accepted** | Architecture Decision |
| [`005-eureka-config-vs-kubernetes-native`](../architecture/adr/005-eureka-config-vs-kubernetes-native.md) | **Accepted** | Architecture Decision |
| [`006-kafka-event-bus`](../architecture/adr/006-kafka-event-bus.md) | **Accepted** | Architecture Decision |
| [`007-distributed-tracing-zipkin`](../architecture/adr/007-distributed-tracing-zipkin.md) | **Accepted** | Architecture Decision |
| [`008-contract-testing`](../architecture/adr/008-contract-testing.md) | **Accepted** | Architecture Decision |
| [`009-openrewrite-spring-boot-4-migration`](../architecture/adr/009-openrewrite-spring-boot-4-migration.md) | **Accepted** | Architecture Decision |
| [`010-tmdb-facade-mapped-persisted-schema`](../architecture/adr/010-tmdb-facade-mapped-persisted-schema.md) | **Accepted** | Architecture Decision |
| [`011-self-healing-read-through-on-schema-drift`](../architecture/adr/011-self-healing-read-through-on-schema-drift.md) | **Accepted** | Architecture Decision |
| [`012-ai-service-postgresql-pgvector`](../architecture/adr/012-ai-service-postgresql-pgvector.md) | **Accepted** | Architecture Decision |
| [`013-frontend-merged-into-monorepo`](../architecture/adr/013-frontend-merged-into-monorepo.md) | **Accepted** | Architecture Decision |
| [`014-media-service-s3-mongo-storage`](../architecture/adr/014-media-service-s3-mongo-storage.md) | **Accepted** | Architecture Decision |
| [`015-local-only-deploy-trigger`](../architecture/adr/015-local-only-deploy-trigger.md) | **Accepted** | Architecture Decision |
| [`016-dynamic-backend-resolution`](../architecture/adr/016-dynamic-backend-resolution.md) | **Accepted** | Architecture Decision |
| [`017-full-cloud-service-parity`](../architecture/adr/017-full-cloud-service-parity.md) | **Accepted** | Architecture Decision |
| [`018-cloud-lifecycle-stop-not-destroy`](../architecture/adr/018-cloud-lifecycle-stop-not-destroy.md) | **Accepted** | Architecture Decision |
| [`019-azure-zero-touch-auto-wake-sleep`](../architecture/adr/019-azure-zero-touch-auto-wake-sleep.md) | **Accepted** | Architecture Decision |
| [`020-nl-query-cross-service-aggregation`](../architecture/adr/020-nl-query-cross-service-aggregation.md) | **Accepted** | Architecture Decision |
| [`021-vosk-bilingual-model-selection`](../architecture/adr/021-vosk-bilingual-model-selection.md) | **Accepted** | Architecture Decision |
| [`022-config-server-access-control`](../architecture/adr/022-config-server-access-control.md) | **Accepted** | Architecture Decision |
| [`023-multi-step-natural-language-search`](../architecture/adr/023-multi-step-natural-language-search.md) | **Accepted** | Architecture Decision |
| [`024-explicit-docker-compose-volume-naming`](../architecture/adr/024-explicit-docker-compose-volume-naming.md) | **Accepted** | Architecture Decision |

---

## ☁️ 7. Infrastructure & Deployment Matrix

| Environment | Target Type | Orchestration | Compute Sizing | Idle Compute Spend |
|---|---|---|---|---|
| **Local Development** | Docker & Podman Compose / Minikube | Compose / Kustomize | Local Machine RAM/CPU | $0.00 |
| **Public HTTPS Gateway** | Cloudflare Quick Tunnel (`cloudflared`) | Docker / K8s Deployment | Ephemeral tunnel | $0.00 |
| **Azure AKS** | Managed Kubernetes (`lmdb-aks`) | Terraform + K8s Overlays | `Standard_D4ls_v7` (4 vCPU / 8 GB) | $0.00/hr when stopped (`az aks stop`) |
| **AWS Cloud** | Single-Node k3s (`lmdb-k3s`) | Terraform + k3s over SSH | `m7i-flex.large` (2 vCPU / 8 GB) | $0.00/hr when stopped (`ec2 stop`) |
| **Frontend Production** | Vercel Edge Network | Next-gen Static / SPA | Global Edge CDN | $0.00 (Hobby tier) |

---

## 🔄 How to Regenerate This Report
To refresh all metrics in this document dynamically after making code changes:
```bash
./gradlew projectStats
```
or directly via script:
```bash
./infrastructure/scripts/generate-project-stats.py
```
