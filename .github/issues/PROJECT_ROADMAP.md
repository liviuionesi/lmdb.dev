# Filmpire Microservices - Project Roadmap

**Version:** 2.0.0
**Date:** July 21, 2026
**Supersedes:** v1.0.0 (November 14, 2025)

---

## 📋 Product Goal (the north star)

Clone the TMDB v3 API in Spring so the **existing Filmpire React app**
(`~/Desktop/filmpire`) consumes this backend as a drop-in replacement for
`api.themoviedb.org/3` — only its base URL changes. Data is served
read-through (**Redis → MongoDB → real TMDB fallback**) with save-through
persistence; TMDB auth/account endpoints are proxied to the real TMDB.

Reference: [ARCHITECTURE.md v1.2.0](../../docs/architecture/ARCHITECTURE.md) §5.1.

**Descoped (v1.2.0):** the dedicated Next.js web app and React Native mobile
apps. The `frontend/` directories are legacy placeholders. The former
PHASE5_WEB_FRONTEND.md and the mobile part of PHASES_6-8 are archived specs,
not planned work.

**Budget:** cloud is strictly $0 — see ARCHITECTURE.md §11.1 (non-billable
account types, ephemeral demo clusters, zero-spend alarms, local-first).

---

## 🎯 Phases

### ✅ Phase 1: Project Setup — COMPLETE
**Issues:** #1 (Epic), #2, #3, #4, #5 — all closed.
Validated 2026-07-21: structure ✓, Gradle build ✓, templates/CI ✓, compose ✓.
Residual gaps tracked in #29 (prod compose file, branch-protection decision,
board-automation workflow).

### 🔄 Phase 2: Infrastructure Services — MOSTLY COMPLETE
**Issues:** #10 (Epic), #11 discovery ✓, #12 config (**reopened** — Git
backend/encryption missing, runs native-mode), #13 gateway ✓, #14 shared-lib
(closed; annotation-aspect gaps tracked in #29).

### 🔄 Phase 3: Core Microservices — IN PROGRESS
**Issues:** #15 (Epic), #16 movie ✓ (bucket4j test regression fixed
2026-07-21, commit ca87c10), #17 user (open), #18 actor (open), #19
integration testing (open), #20 SonarQube (open).

### 🔄 Phase 4: TMDB v3 Facade & React App Integration — **THE CORE PRODUCT**
**Issues:** [#30](https://github.com/pehlivanu/filmpire-microservices/issues/30) (Epic), #31–#34.
- #31 TMDB-shaped movie/genre/search/discover endpoints, read-through/save-through (movie-service)
- #32 TMDB-shaped person + discover-by-cast endpoints (actor-service, extends #18)
- #33 Gateway facade routing + auth/account proxy to real TMDB
- #34 Filmpire React app integration (env base URL, CORS, runbook)
**Dependencies:** movie-service ✓; #32 depends on #18.

### 🔄 Phase 5: Advanced Services
**Issues:** #35 (Epic), #36 AI service (Spring AI + gRPC, Ollama for $0),
#37 media service (MinIO). Spec: [PHASE4_ADVANCED_SERVICES.md](PHASE4_ADVANCED_SERVICES.md)
(file kept under its historical name).

### 🔄 Phase 6: Comprehensive Testing
**Issues:** #19 (service integration tests), #38 (E2E: Playwright driving the
real Filmpire React app against the local stack — the facade's acceptance
test). Performance/security per ARCHITECTURE.md §10.

### 🔄 Phase 7: Observability & Cloud Deployment ($0 hard budget)
**Issues:** #22 (Epic), #23 service instrumentation, #24 local
Prometheus+Grafana / ELK, #25 Kubernetes Kustomize manifests, #26 Terraform
Azure (AKS), #27 Terraform AWS (k3s), #28 CI/CD publish+deploy.
Local-first on the dev laptop (podman Kubernetes); cloud clusters are
ephemeral demos on non-billable free-tier accounts.

---

## 🔢 Issue Creation Mechanics

Issues #1–#20 were created from the `.github/issues/PHASE*.md` specs via
`.github/scripts/create-phase*-issues.sh`. Issues #22–#38 were created
directly with `gh issue create` on 2026-07-21 and added to the
[Filmpire Microservices project board](https://github.com/users/pehlivanu/projects/1).
The board is the single source of truth for remaining work.

## 🗺️ Architecture → Issue Coverage Matrix (v1.2.0)

| Architecture section | Covered by |
|---|---|
| §3.1 Discovery / §3.2 Config / §3.3 Gateway | #11 ✓ / #12 (reopened) / #13 ✓ |
| §3.4 Movie / §3.5 User / §3.6 Actor | #16 ✓ / #17 / #18 + #32 |
| §3.7 AI / §3.8 Media | #36 / #37 |
| §4 Database strategy & caching | #16 ✓, #31 (TMDB-shape persistence) |
| §5.1 TMDB v3 facade (primary API) | #30, #31, #32, #33, #34 |
| §5.1b native /api/v1, §5.2 OpenAPI | #16 ✓, per-service tasks |
| §6 Security | #13 ✓ (JWT/rate-limit), #20, #29 |
| §7 Dev environment | #5 ✓, #34 (runbook) |
| §10 Testing strategy | #19, #38, per-service test criteria |
| §11 Deployment (Terraform/K8s/$0 free tier) | #22, #25, #26, #27, #28 |
| §12 Observability (Prometheus/Grafana/ELK) | #23, #24 |
| §12.3 Distributed tracing (ADR-007) | #42 |
| §12.4 SLOs / §10.5 performance testing | #45 |
| §2.2 Kafka event bus (ADR-006) | #39, #40, #41 |
| §10.6 Contract testing (ADR-008) | #43 |
| §2.4 Failure-mode matrix / facade hardening | #44 |
| §2.3 ADRs 001–008 | docs/architecture/adr/ (committed) |
| §13 Success criteria | epic acceptance criteria (#22, #30) |
