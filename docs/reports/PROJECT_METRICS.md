# 📊 LMDB Microservices — Project Analytics & Metrics Report

> **Dynamically Generated:** `2026-08-13 13:21:53 UTC`  
> **Git Status:** Branch `develop` | Commit [`447b685`](https://github.com/liviuionesi/lmdb.dev/commit/447b685)  
> **Auto-Update Trigger:** Executes automatically on each push and via `./gradlew projectStats`

---

## 🌟 Key Performance Indicators (KPIs)

| Metric | Value | Category | Significance |
|---|---|---|---|
| **Total Git Commits** | **405** | Git Velocity | Evolutionary depth across 2.5+ years of active development |
| **Total Code Churn** | **460,102 LOC** (+268,722 / -191,380) | Git Velocity | Continuous refactoring and enterprise hardening |
| **Total Codebase Lines** | **67,931 LOC** (48,632 code / 10,677 comments) | Codebase Volume | Full-stack polyglot microservice ecosystem |
| **Total Automated Tests** | **671 Tests** (474 Backend + 197 Frontend) | Quality & Reliability | 100% Green Unit, Slice, Contract & Integration suites |
| **Total REST Endpoints** | **60 Endpoints** (39 GET, 14 POST, 2 PUT, 5 DELETE) | API Surface | Microservice REST surface exposed via API Gateway |
| **Database Migrations** | **6 Flyway SQL Scripts** | Persistence | Versioned, reproducible relational schemas |
| **Architecture Decisions** | **19 ADRs** Documented | Governance | Comprehensive decision records (ADR-001 through ADR-018) |
| **Cloud Deployment Targets** | **3 Targets** (Azure AKS, AWS EC2 k3s, Local Minikube) | Multi-Cloud | $0-budget tripwire protected infrastructure |
| **Known Vulnerabilities** | **0 CVEs** | Security | Proactive BOM security overrides in `gradle.properties` |

---

## 📈 1. Git Velocity & Lifecycle Churn

- **Development Timeline:** `2024-03-14 (2 years, 5 months ago)` ➔ `2026-08-13 (11 seconds ago)`
- **Total Commits:** `405`
- **Total Lines Added (+):** `268,722`
- **Total Lines Deleted / Refactored (-):** `191,380`
- **Total Churn Volume (Add + Del):** `460,102` lines processed
- **Net Repository Growth:** `+77,342` lines

---

## 💻 2. Codebase Distribution by Technology

| Technology / Language | Files | Code LOC | Comment LOC | Blank LOC | Total LOC | Share of Project |
|---|---|---|---|---|---|---|
| **Java (Spring Boot / gRPC)** | 229 | 15,676 | 8,812 | 2,891 | **27,379** | `[██████░░░░░░░░░]  40.3%` |
| **Documentation (Markdown)** | 69 | 14,630 | 1 | 3,761 | **18,392** | `[████░░░░░░░░░░░]  27.1%` |
| **JavaScript / React (JSX)** | 90 | 6,175 | 592 | 986 | **7,753** | `[██░░░░░░░░░░░░░]  11.4%` |
| **Kubernetes & CI/CD (YAML)** | 83 | 4,483 | 693 | 266 | **5,442** | `[█░░░░░░░░░░░░░░]   8.0%` |
| **JSON Data** | 18 | 3,664 | 0 | 1 | **3,665** | `[█░░░░░░░░░░░░░░]   5.4%` |
| **Shell Automation (Bash)** | 30 | 1,853 | 386 | 360 | **2,599** | `[█░░░░░░░░░░░░░░]   3.8%` |
| **Build & Config (Gradle/Properties)** | 14 | 1,039 | 24 | 172 | **1,235** | `[░░░░░░░░░░░░░░░]   1.8%` |
| **Terraform & Cloud (HCL)** | 26 | 829 | 157 | 142 | **1,128** | `[░░░░░░░░░░░░░░░]   1.7%` |
| **SQL & DB Migrations** | 6 | 145 | 0 | 20 | **165** | `[░░░░░░░░░░░░░░░]   0.2%` |
| **XML & HTML** | 3 | 95 | 6 | 12 | **113** | `[░░░░░░░░░░░░░░░]   0.2%` |
| **Protocol Buffers (Proto3)** | 1 | 31 | 6 | 8 | **45** | `[░░░░░░░░░░░░░░░]   0.1%` |
| **CSS & Styling** | 1 | 12 | 0 | 3 | **15** | `[░░░░░░░░░░░░░░░]   0.0%` |

---

## 🧩 3. Microservice & Module LOC Breakdown

| Microservice / Module | Files | Code LOC | Comment LOC | Total LOC | Share of Project |
|---|---|---|---|---|---|
| **`docs (Architecture, Guides, ADRs)`** | 50 | 13,590 | 55 | **16,204** | `[████░░░░░░░░░░░]  23.9%` |
| **`movie-service (Port 8081)`** | 71 | 6,424 | 2,527 | **10,069** | `[██░░░░░░░░░░░░░]  14.8%` |
| **`infrastructure (Terraform, K8s, Scripts)`** | 122 | 6,676 | 919 | **8,332** | `[██░░░░░░░░░░░░░]  12.3%` |
| **`frontend (React 19 / MUI 9 / Vite 8)`** | 93 | 6,366 | 545 | **7,927** | `[██░░░░░░░░░░░░░]  11.7%` |
| **`api-gateway (Port 8080)`** | 44 | 4,354 | 1,707 | **6,995** | `[██░░░░░░░░░░░░░]  10.3%` |
| **`shared-library (Common DTOs & Mappers)`** | 33 | 2,343 | 1,464 | **4,406** | `[█░░░░░░░░░░░░░░]   6.5%` |
| **`actor-service (Port 8083)`** | 30 | 1,929 | 1,071 | **3,329** | `[█░░░░░░░░░░░░░░]   4.9%` |
| **`ai-service (Port 8084 / gRPC 9090)`** | 40 | 1,734 | 724 | **2,756** | `[█░░░░░░░░░░░░░░]   4.1%` |
| **`user-service (Port 8082)`** | 30 | 1,638 | 813 | **2,753** | `[█░░░░░░░░░░░░░░]   4.1%` |
| **`config-service (Spring Config 8888)`** | 19 | 946 | 303 | **1,496** | `[░░░░░░░░░░░░░░░]   2.2%` |
| **`media-service (Port 8085)`** | 17 | 873 | 286 | **1,305** | `[░░░░░░░░░░░░░░░]   1.9%` |
| **`discovery-service (Eureka 8761)`** | 8 | 374 | 196 | **671** | `[░░░░░░░░░░░░░░░]   1.0%` |
| **`e2e (Postman & Newman Regression)`** | 6 | 170 | 47 | **250** | `[░░░░░░░░░░░░░░░]   0.4%` |

---

## 🏗️ 4. Architecture & Object Topology

### Backend Architecture (Spring Boot & Java 25)
- **Total Java Type Declarations:** `162`
  - Classes (`public class`): `93`
  - Records (`public record` DTOs/Value Objects): `51`
  - Interfaces (`public interface` Contracts/Clients): `14`
  - Enums (`public enum`): `4`
- **REST Controllers:** `16` (`@RestController`)
- **Business Services & Handlers:** `22` (`@Service`)
- **Spring Data Repositories:** `10` (Postgres JPA + MongoDB)
- **Persistence Entities:** `11` (`@Entity` + `@Document`)
- **Flyway Database Migrations:** `6` versioned SQL migration scripts
- **Spring Cloud Contract Tests:** `8` stubs/verifier tests
- **gRPC & Protobuf Schemas:** `1` (`.proto`)

### Frontend Architecture (React 19, MUI 9, Redux Toolkit)
- **React Components:** `44` (`.jsx`)
- **Redux State Slices:** `4` (`createSlice`)
- **Custom React Hooks:** `6`

---

## 🧪 5. Testing & Quality Assurance Analytics

| Test Category | Test Count | Test Files | Tooling & Test Slices |
|---|---|---|---|
| **Backend Test Suite** | **474** | 78 | JUnit 5, Mockito, Testcontainers (Postgres/Mongo/Kafka), WireMock, Contract Verifier, Gatling |
| **Frontend Test Suite** | **197** | 32 | Vitest, React Testing Library, jsdom |
| **Combined Test Coverage** | **671** | 110 | **100% Passing Test Matrix** |

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
