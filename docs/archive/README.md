# Archive

Historical setup documents, superseded planning material, and stale
snapshots. Kept for traceability but **no longer actively maintained** —
don't treat anything in here as current. If a file below is what you're
looking for, check whether it's been superseded first (the table says by
what).

---

## Initial Setup (Nov 2025)

| File | Purpose |
|------|---------|
| AGILE_WORKFLOW_GUIDE.md | Agile workflow guide for GitHub Projects integration |
| PROJECT_SETUP.md | GitHub repository and project board setup instructions |
| GITHUB_SETUP.md | GitHub templates & workflows setup guide |
| GITHUB_PROJECT_README.md | Early GitHub Projects board README draft |
| create-phase1-issues.sh | Script used once to create the Phase 1 issues and labels |

## Superseded Phase Specs

Issues #1–#20 were seeded from these `.github/issues/PHASE*.md` specs via
one-time creation scripts; the live GitHub issues are now the source of
truth, not these files. Phase 4's spec is the one exception — it's still
referenced by [`PROJECT_ROADMAP.md`](../../.github/issues/PROJECT_ROADMAP.md)
and stays at `.github/issues/PHASE4_ADVANCED_SERVICES.md`, not archived.

| File | Purpose |
|------|---------|
| PHASE1_ISSUES.md | Original spec for issues #1–#5 (project setup) |
| PHASE2_INFRASTRUCTURE_SERVICES.md | Original spec for issues #10–#14 (Eureka/Config/Gateway/shared-lib) |
| PHASE3_CORE_SERVICES.md | Original spec for issues #15–#20 (Movie/User/Actor services) |
| PHASE5_WEB_FRONTEND_LEGACY.md | Draft spec for a separate Next.js web frontend — descoped (v1.2.0); the real frontend is `frontend/lmdb/`, [ADR-013](../architecture/adr/013-frontend-merged-into-monorepo.md) |
| PHASES_6-8_MOBILE_TESTING_DEPLOYMENT_LEGACY.md | Draft spec for React Native mobile apps — descoped, never scaffolded |

## Superseded Reference Material

| File | Purpose | Superseded By |
|------|---------|---------------|
| CURSOR_PROMPTS.md | Cursor IDE prompt catalogue | `CLAUDE.md` + `ARCHITECTURE.md` |
| NEXTJS_UI_LIBRARIES.md | UI library research for the descoped Next.js frontend | N/A — frontend is React/MUI, not Next.js |
| SONAR_CONFIGURATION.md | Early SonarLint rule notes | `docs/architecture/CODE_QUALITY.md` |
| DDOS_PROTECTION_IMPROVEMENTS.md | Pre-implementation DDoS protection recommendations (Nov 2025) | `docs/security/DDOS_PROTECTION_IMPLEMENTED.md` — describes what was actually built |
| MOVIE_SERVICE_TEST_SUMMARY.md | movie-service test suite design notes, Nov 2025 (~13 test classes) | Stale — the suite has grown to 25+ test classes since; run `./gradlew :backend:movie-service:test` for current reality |
| MOVIE_SERVICE_TEST_EXECUTION_RESULTS.md | movie-service test run snapshot, Nov 18 2025 (86/100 passing) | Same as above — a point-in-time result, not current |

---

## Active Documentation

For current project documentation, see:
- **Main README:** `/README.md`
- **Architecture:** `/docs/architecture/ARCHITECTURE.md`
- **ADRs:** `/docs/architecture/adr/`
- **Port Mapping:** `/docs/architecture/PORT_MAPPING.md`
- **Docker Setup:** `/docs/architecture/DOCKER_INFRASTRUCTURE_SETUP.md`
- **Gradle Setup:** `/docs/architecture/GRADLE_BUILD_SETUP.md`
- **Integration Testing:** `/docs/architecture/INTEGRATION_TESTING.md`
- **Security:** `/docs/security/`
- **API / Postman:** `/docs/api/`
- **Project Roadmap:** `/.github/issues/PROJECT_ROADMAP.md`
