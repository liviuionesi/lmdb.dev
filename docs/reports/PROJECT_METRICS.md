# 📊 LMDB Microservices — Project Analytics & Metrics Report

> **Dynamically Generated:** `2026-08-14 21:15:32 UTC`  
> **Git Status:** Branch `develop` | Commit [`bb93f30`](https://github.com/liviuionesi/lmdb.dev/commit/bb93f30)  
> **Auto-Update Trigger:** Executes automatically on each push and via `./gradlew projectStats`

---

## 🌟 Key Performance Indicators (KPIs)

| Metric | Value | Category | Significance |
|---|---|---|---|
| **Total Git Commits** | **445** | Git Velocity | Evolutionary depth across 2.5+ years of active development |
| **Total Code Churn** | **466,055 LOC** (+271,907 / -194,148) | Git Velocity | Continuous refactoring and enterprise hardening |
| **Total Codebase Lines** | **68,474 LOC** (49,090 code / 10,714 comments) | Codebase Volume | Full-stack polyglot microservice ecosystem |
| **Total Automated Tests** | **679 Tests** (478 Backend + 201 Frontend) | Quality & Reliability | 100% Green Unit, Slice, Contract & Integration suites |
| **Total REST Endpoints** | **60 Endpoints** (39 GET, 14 POST, 2 PUT, 5 DELETE) | API Surface | Microservice REST surface exposed via API Gateway |
| **Database Migrations** | **12 Flyway SQL Scripts** | Persistence | Versioned, reproducible relational schemas |
| **Architecture Decisions** | **19 ADRs** Documented | Governance | Comprehensive decision records (ADR-001 through ADR-018) |
| **Cloud Deployment Targets** | **3 Targets** (Azure AKS, AWS EC2 k3s, Local Minikube) | Multi-Cloud | $0-budget tripwire protected infrastructure |
| **Known Vulnerabilities** | **0 CVEs** | Security | Proactive BOM security overrides in `gradle.properties` |

---

## 📈 1. Git Velocity & Lifecycle Churn

- **Development Timeline:** `2024-03-14 (2 years, 5 months ago)` ➔ `2026-08-14 (18 minutes ago)`
- **Total Commits:** `445`
- **Total Lines Added (+):** `271,907`
- **Total Lines Deleted / Refactored (-):** `194,148`
- **Total Churn Volume (Add + Del):** `466,055` lines processed
- **Net Repository Growth:** `+77,759` lines

---

## 💻 2. Codebase Distribution by Technology

| Technology / Language | Files | Code LOC | Comment LOC | Blank LOC | Total LOC | Share of Project |
|---|---|---|---|---|---|---|
| **Java (Spring Boot / gRPC)** | 229 | 15,683 | 8,808 | 2,891 | **27,382** | `[██████░░░░░░░░░]  40.0%` |
| **Documentation (Markdown)** | 69 | 14,650 | 1 | 3,768 | **18,419** | `[████░░░░░░░░░░░]  26.9%` |
| **JavaScript / React (JSX)** | 96 | 6,533 | 611 | 1,014 | **8,158** | `[██░░░░░░░░░░░░░]  11.9%` |
| **Kubernetes & CI/CD (YAML)** | 83 | 4,488 | 693 | 266 | **5,447** | `[█░░░░░░░░░░░░░░]   8.0%` |
| **JSON Data** | 18 | 3,664 | 0 | 1 | **3,665** | `[█░░░░░░░░░░░░░░]   5.4%` |
| **Shell Automation (Bash)** | 30 | 1,871 | 386 | 361 | **2,618** | `[█░░░░░░░░░░░░░░]   3.8%` |
| **Build & Config (Gradle/Properties)** | 17 | 1,083 | 46 | 184 | **1,313** | `[░░░░░░░░░░░░░░░]   1.9%` |
| **Terraform & Cloud (HCL)** | 28 | 835 | 157 | 142 | **1,134** | `[░░░░░░░░░░░░░░░]   1.7%` |
| **SQL & DB Migrations** | 6 | 145 | 0 | 20 | **165** | `[░░░░░░░░░░░░░░░]   0.2%` |
| **XML & HTML** | 3 | 95 | 6 | 12 | **113** | `[░░░░░░░░░░░░░░░]   0.2%` |
| **Protocol Buffers (Proto3)** | 1 | 31 | 6 | 8 | **45** | `[░░░░░░░░░░░░░░░]   0.1%` |
| **CSS & Styling** | 1 | 12 | 0 | 3 | **15** | `[░░░░░░░░░░░░░░░]   0.0%` |

---

## 🧩 3. Microservice & Module LOC Breakdown

| Microservice / Module | Files | Code LOC | Comment LOC | Total LOC | Share of Project |
|---|---|---|---|---|---|
| **`docs (Architecture, Guides, ADRs)`** | 50 | 13,590 | 55 | **16,204** | `[████░░░░░░░░░░░]  23.7%` |
| **`movie-service (Port 8081)`** | 71 | 6,424 | 2,526 | **10,068** | `[██░░░░░░░░░░░░░]  14.7%` |
| **`infrastructure (Terraform, K8s, Scripts)`** | 127 | 6,726 | 941 | **8,415** | `[██░░░░░░░░░░░░░]  12.3%` |
| **`frontend (React 19 / MUI 9 / Vite 8)`** | 99 | 6,724 | 564 | **8,332** | `[██░░░░░░░░░░░░░]  12.2%` |
| **`api-gateway (Port 8080)`** | 44 | 4,364 | 1,707 | **7,005** | `[██░░░░░░░░░░░░░]  10.2%` |
| **`shared-library (Common DTOs & Mappers)`** | 33 | 2,343 | 1,464 | **4,406** | `[█░░░░░░░░░░░░░░]   6.4%` |
| **`actor-service (Port 8083)`** | 30 | 1,929 | 1,070 | **3,328** | `[█░░░░░░░░░░░░░░]   4.9%` |
| **`ai-service (Port 8084 / gRPC 9090)`** | 40 | 1,753 | 722 | **2,775** | `[█░░░░░░░░░░░░░░]   4.1%` |
| **`user-service (Port 8082)`** | 30 | 1,638 | 813 | **2,753** | `[█░░░░░░░░░░░░░░]   4.0%` |
| **`config-service (Spring Config 8888)`** | 19 | 946 | 303 | **1,496** | `[░░░░░░░░░░░░░░░]   2.2%` |
| **`media-service (Port 8085)`** | 17 | 873 | 286 | **1,305** | `[░░░░░░░░░░░░░░░]   1.9%` |
| **`discovery-service (Eureka 8761)`** | 8 | 374 | 196 | **671** | `[░░░░░░░░░░░░░░░]   1.0%` |
| **`e2e (Postman & Newman Regression)`** | 6 | 170 | 47 | **250** | `[░░░░░░░░░░░░░░░]   0.4%` |

---

## 🏗️ 4. Architecture & Object Topology

### Backend Architecture (Spring Boot & Java 25)
- **Total Java Type Declarations:** `175`
  - Classes (`public class`): `100`
  - Records (`public record` DTOs/Value Objects): `51`
  - Interfaces (`public interface` Contracts/Clients): `20`
  - Enums (`public enum`): `4`
- **REST Controllers:** `16` (`@RestController`)
- **Business Services & Handlers:** `22` (`@Service`)
- **Spring Data Repositories:** `10` (Postgres JPA + MongoDB)
- **Persistence Entities:** `11` (`@Entity` + `@Document`)
- **Flyway Database Migrations:** `12` versioned SQL migration scripts
- **Spring Cloud Contract Tests:** `8` stubs/verifier tests
- **gRPC & Protobuf Schemas:** `1` (`.proto`)

### Frontend Architecture (React 19, MUI 9, Redux Toolkit)
- **React Components:** `49` (`.jsx`)
- **Redux State Slices:** `4` (`createSlice`)
- **Custom React Hooks:** `6`

---

## 🧪 5. Testing & Quality Assurance Analytics

| Test Category | Test Count | Test Files | Tooling & Test Slices |
|---|---|---|---|
| **Backend Test Suite** | **478** | 82 | JUnit 5, Mockito, Testcontainers (Postgres/Mongo/Kafka), WireMock, Contract Verifier, Gatling |
| **Frontend Test Suite** | **201** | 34 | Vitest, React Testing Library, jsdom |
| **Combined Test Coverage** | **679** | 116 | **100% Passing Test Matrix** |

- **Test-to-Production Code Ratio:** `13.8` automated tests per 1,000 lines of production code.
- **Security & Dependency Centralization:** 100% of versions managed via `gradle.properties` with proactive CVE security overrides.

---

## 🏛️ 6. Architectural Decision Records (ADRs)

The repository includes **19 formal Architectural Decision Records** in `docs/architecture/adr/`:

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
