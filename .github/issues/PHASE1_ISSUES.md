# Phase 1: Project Setup - GitHub Issues

## Epic

### Issue #1: [EPIC] Project Setup Phase

**Labels:** `epic`, `P0-critical`, `sprint-0`, `infrastructure`

**Description:**
Complete initial project setup including directory structure, Gradle configuration, and GitHub workflows. This epic encompasses all foundational work needed before feature development can begin.

**Business Value:**
Establishes solid foundation for microservices development with proper tooling, CI/CD, and project management infrastructure.

**User Stories:**
- #2 - Task: Initialize Project Structure
- #3 - Task: Setup Gradle Multi-Module Build  
- #4 - Task: Create GitHub Templates & Workflows
- #5 - Task: Setup Docker Compose Infrastructure

**Story Points:** 13  
**Target Sprint:** Sprint 0

---

## User Stories / Tasks

### Issue #2: [TASK] Initialize Project Structure

**Labels:** `task`, `P0-critical`, `sprint-0`, `infrastructure`

**Description:**
Create complete directory structure for all microservices, frontend applications, infrastructure configs, and documentation.

**Implementation Checklist:**
- [ ] Create backend service directories (8 services)
  - api-gateway
  - discovery-service
  - config-service
  - movie-service
  - user-service
  - actor-service
  - ai-service
  - media-service
  - shared-library
- [ ] Create frontend directories
  - web-nextjs/
  - mobile-react-native/
- [ ] Create infrastructure directories
  - docker/
  - kubernetes/
  - scripts/
- [ ] Create documentation directories
  - architecture/adr/
  - architecture/diagrams/
  - api/
  - guides/
- [ ] Create README.md for each service
- [ ] Create .gitignore files

**Files to Create:**
```
backend/<service-name>/
  ├── src/main/java/com/filmpire/<service>/
  ├── src/main/resources/
  ├── src/test/java/
  ├── build.gradle
  ├── Dockerfile
  └── README.md
```

**Acceptance Criteria:**
- [ ] All 8 backend service directories created
- [ ] Standard Java package structure in place
- [ ] Frontend directories created
- [ ] Infrastructure directories created
- [ ] Each service has README.md
- [ ] Directory structure matches ARCHITECTURE.md

**Story Points:** 3  
**Estimated Time:** 2-3 hours

**Commands to Run:**
```bash
# Structure will be created via script
./infrastructure/scripts/setup-environment.sh
```

---

### Issue #3: [TASK] Setup Gradle Multi-Module Build

**Labels:** `task`, `P0-critical`, `sprint-0`, `backend`

**Description:**
Configure Gradle multi-module build system for all backend microservices with Spring Boot 3.5.8, Spring Cloud 2025.0.0, and Java 25.

**Implementation Checklist:**
- [x] Create root `settings.gradle` with correct order (pluginManagement first)
- [x] Create root `build.gradle` with common configuration
- [x] Create `gradle.properties` with version management
- [x] Configure each service's `build.gradle`
- [x] Setup Spring Boot and Spring Cloud dependencies
- [x] Configure Java 25 toolchain
- [x] Add common plugins (spotless, jacoco, etc.)
- [x] Create shared-library module

**Files to Create/Modify:**
- `settings.gradle` - Module definitions
- `build.gradle` - Root build config
- `gradle.properties` - Version management
- `backend/shared-library/build.gradle`
- `backend/<each-service>/build.gradle`

**Dependencies to Configure:**
```properties
springBootVersion=3.5.8
springCloudVersion=2025.0.0
springAiVersion=1.1.0
lombokVersion=1.18.42
```

**Acceptance Criteria:**
- [x] `./gradlew build` runs successfully
- [x] All modules recognized by Gradle
- [x] Java 25 toolchain configured
- [x] Spring Boot 3.5.8 dependency management working
- [x] Shared library can be imported by services
- [x] Build completes without errors

**Testing:**
```bash
./gradlew clean build
./gradlew tasks --all
```

**Story Points:** 5  
**Estimated Time:** 4-5 hours

---

### Issue #4: [TASK] Create GitHub Templates & Workflows

**Labels:** `task`, `P1-high`, `sprint-0`, `devops`

**Description:**
Setup GitHub issue templates, PR template, and basic CI/CD workflows for automated testing and quality checks.

**Implementation Checklist:**
- [x] Create issue templates
  - epic.md
  - user-story.md
  - task.md
  - bug.md
- [x] Create PR template
- [x] Create GitHub Actions workflows
  - backend-ci.yml
  - frontend-ci.yml
  - project-automation.yml
- [x] Configure branch protection rules
- [x] Create labels
- [x] Setup dependabot

**Files to Create:**
- `.github/ISSUE_TEMPLATE/epic.md`
- `.github/ISSUE_TEMPLATE/user-story.md`
- `.github/ISSUE_TEMPLATE/task.md`
- `.github/ISSUE_TEMPLATE/bug.md`
- `.github/PULL_REQUEST_TEMPLATE.md`
- `.github/workflows/backend-ci.yml`
- `.github/workflows/frontend-ci.yml`
- `.github/workflows/project-automation.yml`
- `.github/dependabot.yml`

**Acceptance Criteria:**
- [x] All issue templates available when creating issues
- [x] PR template appears for new PRs
- [x] CI workflow runs on push to main/develop
- [x] Labels created and documented
- [x] Branch protection documented (requires Pro/public repo)

**Story Points:** 3  
**Estimated Time:** 2-3 hours

**Status:** ✅ COMPLETE  
**Completed:** 2025-11-14  
**Verification:** All files created, workflows tested, Dependabot configured

---

### Issue #5: [TASK] Setup Docker Compose Infrastructure

**Labels:** `task`, `P1-high`, `sprint-0`, `infrastructure`

**Description:**
Create Docker Compose configuration for local development environment with all required databases and services.

**Implementation Checklist:**
- [x] Create docker-compose.yml
- [x] Configure PostgreSQL 17
- [x] Configure MongoDB 8.0
- [x] Configure Redis 7.4
- [x] Configure MinIO (optional)
- [x] Setup networking
- [x] Configure volumes
- [x] Add health checks
- [x] Create startup script

**Files to Create:**
- `infrastructure/docker/docker-compose.yml`
- `infrastructure/docker/docker-compose.prod.yml`
- `infrastructure/docker/.env.example`
- `infrastructure/scripts/start-infrastructure.sh`
- `infrastructure/scripts/stop-infrastructure.sh`

**Services to Configure:**
```yaml
services:
  - postgres:17
  - mongodb:8.0
  - redis:7.4
  - eureka-server:8761
  - config-server:8888
  - api-gateway:8080
```

**Acceptance Criteria:**
- [x] `docker-compose up -d` starts all services
- [x] All databases accessible
- [x] Health checks pass
- [x] Services can connect to databases
- [x] Proper networking configured
- [x] Volumes persist data

**Testing:**
```bash
cd infrastructure/docker
docker-compose up -d
docker-compose ps
docker-compose logs -f
```

**Story Points:** 5  
**Estimated Time:** 4-5 hours

---

## How to Use These Issues

### Step 1: Create GitHub Repo
```bash
cd /home/liviu/Desktop/filmpire-microservices
gh repo create filmpire-microservices --public --source=. --remote=origin
```

### Step 2: Create Labels
```bash
gh label create "P0-critical" --color "d73a4a"
gh label create "P1-high" --color "ff6b6b"
gh label create "P2-medium" --color "ffd93d"
gh label create "P3-low" --color "6bcf7f"
gh label create "epic" --color "5319e7"
gh label create "task" --color "008672"
gh label create "backend" --color "fbca04"
gh label create "infrastructure" --color "0e8a16"
gh label create "sprint-0" --color "c5def5"
```

### Step 3: Create Issues
```bash
# Epic
gh issue create --title "[EPIC] Project Setup Phase" \
  --body "See .github/issues/PHASE1_ISSUES.md for details" \
  --label "epic,P0-critical,sprint-0,infrastructure"

# Task 1
gh issue create --title "[TASK] Initialize Project Structure" \
  --body "See .github/issues/PHASE1_ISSUES.md #2" \
  --label "task,P0-critical,sprint-0,infrastructure"

# Task 2
gh issue create --title "[TASK] Setup Gradle Multi-Module Build" \
  --body "See .github/issues/PHASE1_ISSUES.md #3" \
  --label "task,P0-critical,sprint-0,backend"

# Task 3
gh issue create --title "[TASK] Create GitHub Templates & Workflows" \
  --body "See .github/issues/PHASE1_ISSUES.md #4" \
  --label "task,P1-high,sprint-0,devops"

# Task 4
gh issue create --title "[TASK] Setup Docker Compose Infrastructure" \
  --body "See .github/issues/PHASE1_ISSUES.md #5" \
  --label "task,P1-high,sprint-0,infrastructure"
```

### Step 4: Add to Project Board
```bash
# Issues will auto-add to project board via automation
# Manually prioritize in project board view
```

### Step 5: Start Working
```bash
# Pick first task
gh issue develop 2 --checkout

# Work on it, then:
git add .
git commit -m "feat(setup): initialize project structure"
git push origin 2-initialize-project-structure

# Create PR
gh pr create --title "feat(setup): initialize project structure" --body "Closes #2"
```

---

## Project Timeline

**Sprint 0 (Week 1):**
- Days 1-2: Issues #2, #3 (Structure & Gradle)
- Days 3-4: Issues #4, #5 (GitHub & Docker)
- Day 5: Review, testing, documentation

**Total Estimate:** 16-20 hours (4-5 days at 4 hours/day)

