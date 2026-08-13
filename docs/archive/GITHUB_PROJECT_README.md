# 🎬 LMDB Microservices — Product Delivery & Agile Governance Board

This GitHub Project board tracks the end-to-end product delivery lifecycle for **LMDB** — a full-stack monorepo featuring a **Spring Boot 4.1.0 & Java 25** polyglot microservices backend and an integrated **React frontend application** (`frontend/lmdb` with CRA, Redux Toolkit Query, Material UI, and native AI voice / Speech-to-Text integration powered by `ai-service` and local Ollama).

---

## 🎯 Product Goal

Clone the TMDB v3 API contract (`https://api.themoviedb.org/3`) so the integrated **LMDB React App** (`frontend/lmdb`) runs against this microservice cluster by changing *only* its base URL. Requests are served via read-through/save-through caching (**Redis → Local Catalog [MongoDB/PostgreSQL] → Real TMDB Fallback**), while user authentication and accounts run natively in **`user-service`** (PostgreSQL 17, BCrypt, JWT).

---

## 🔄 Agile Engineering Lifecycle & Board Governance

Work on this board is governed by strict Scrum engineering standards:

### 1. Backlog Structure & Hierarchy
- **Epic**: High-level capability domain (e.g., *Phase 4: TMDB v3 Facade & React App Integration*).
- **User Story**: Business requirements formatted as `As a <role>, I want <goal>, so that <benefit>` with explicit **Given / When / Then** BDD acceptance criteria and Story Point estimates.
- **Technical Task**: Implementation task assigned under a User Story, hour-estimated.
- **Bug**: Defect item with reproduction steps, expected vs. actual behavior, and severity rating (`P0-critical` → `P3-low`).

### 2. Quality Gates & Entry / Exit Criteria
- 📋 **[Definition of Ready (DoR)](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/process/DEFINITION_OF_READY.md)**: User Stories must have clear acceptance criteria, resolved dependencies, and story points before entering a Sprint.
- ✅ **[Definition of Done (DoD)](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/process/DEFINITION_OF_DONE.md)**: Zero failing unit/integration tests, 85%+ JaCoCo coverage, SonarQube quality gate compliance, green CI pipeline, and verified runtime behavior.
- 🛠️ **[Autonomous Development Contract (CLAUDE.md)](https://github.com/pehlivanu/lmdb.dev/blob/develop/CLAUDE.md)**: Governs developer and AI session work loops (isolation on `develop`, `.githooks` compile/commit-msg checks, and 3-pass code reviews before merging to `main`).

---

## 🚦 Column Lifecycle

| Column | Description & Criteria |
| :--- | :--- |
| **📋 Product Backlog** | Prioritized Epics and Stories mapped from the [Project Roadmap](https://github.com/pehlivanu/lmdb.dev/blob/develop/.github/issues/PROJECT_ROADMAP.md). |
| **🎯 Sprint Backlog** | Stories pulled into the active Sprint Milestone satisfying the [Definition of Ready](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/process/DEFINITION_OF_READY.md). |
| **🏃 In Progress** | Active development tasks currently being worked on (`develop` branch). |
| **🔍 Quality Gate & Review** | Automated CI validation, SonarQube static analysis, Testcontainers/WireMock execution, and code review. |
| **✅ Done** | Fully verified increments closed under the [Definition of Done](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/process/DEFINITION_OF_DONE.md). |

---

## 🏛️ Architecture Highlights & ADR References

Rather than repeating technical specs on this board, complete system documentation lives in the repository:

- 🎨 **Monorepo Frontend**: Integrated React app (`frontend/lmdb` — CRA, Redux Toolkit Query, Material UI, Speech-to-Text & AI Voice Assistant) running drop-in against the backend gateway (**[ADR-013](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/architecture/adr/013-frontend-merged-into-monorepo.md)**).
- 🏗️ **Microservices System Architecture**: 8 microservices (`api-gateway`, `discovery-service`, `config-service`, `movie-service`, `user-service`, `actor-service`, `ai-service`, `media-service`). Detailed in **[ARCHITECTURE.md](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/architecture/ARCHITECTURE.md)**.
- 🤖 **Spring AI & Local LLM**: REST + gRPC `ai-service` paired with local Ollama models, Speech-to-Text voice interface, and PostgreSQL + `pgvector` semantic taste search (**[ADR-012](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/architecture/adr/012-ai-service-postgresql-pgvector.md)**).
- 🔄 **Self-Healing Catalog**: Automatic eviction & TMDB sync on schema drift (**[ADR-011](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/architecture/adr/011-self-healing-read-through-on-schema-drift.md)**).
- ☁️ **$0 Cloud IaC**: Ephemeral Azure AKS & AWS k3s provisioned via Terraform (**[ADR-004](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/architecture/adr/004-zero-budget-cloud-strategy.md)**).
- 📊 **Full Telemetry**: Prometheus/Grafana metrics, ELK JSON logging, and Micrometer Zipkin tracing (**[ADR-007](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/architecture/adr/007-distributed-tracing-zipkin.md)**).

---

## 🔗 Quick Documentation Links

- 📖 **[Repository README](https://github.com/pehlivanu/lmdb.dev/blob/develop/README.md)**
- 🏛️ **[Full Architecture Document](https://github.com/pehlivanu/lmdb.dev/blob/develop/docs/architecture/ARCHITECTURE.md)**
- 📓 **[Architecture Decision Records (ADRs)](https://github.com/pehlivanu/lmdb.dev/tree/develop/docs/architecture/adr/)**
- 📋 **[Scrum Process & Ceremonies](https://github.com/pehlivanu/lmdb.dev/tree/develop/docs/process/)**
- 🗺️ **[Project Roadmap](https://github.com/pehlivanu/lmdb.dev/blob/develop/.github/issues/PROJECT_ROADMAP.md)**
