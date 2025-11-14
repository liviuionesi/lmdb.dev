#!/bin/bash

# Create Phase 1 Issues for Filmpire Microservices
# Run this after creating the GitHub repository

set -e

echo "Creating Phase 1: Project Setup Issues..."

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "GitHub CLI (gh) is not installed. Please install it first."
    echo "Visit: https://cli.github.com/"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "Please authenticate with GitHub CLI first:"
    echo "gh auth login"
    exit 1
fi

echo -e "${BLUE}Step 1: Creating labels...${NC}"

# Create labels
gh label create "P0-critical" --color "d73a4a" --description "Critical priority" --force
gh label create "P1-high" --color "ff6b6b" --description "High priority" --force
gh label create "P2-medium" --color "ffd93d" --description "Medium priority" --force
gh label create "P3-low" --color "6bcf7f" --description "Low priority" --force

gh label create "epic" --color "5319e7" --description "Epic" --force
gh label create "user-story" --color "0075ca" --description "User story" --force
gh label create "task" --color "008672" --description "Task" --force
gh label create "bug" --color "d73a4a" --description "Bug" --force

gh label create "backend" --color "fbca04" --description "Backend service" --force
gh label create "frontend" --color "006b75" --description "Frontend" --force
gh label create "infrastructure" --color "0e8a16" --description "Infrastructure" --force
gh label create "devops" --color "bfdadc" --description "DevOps" --force
gh label create "documentation" --color "c5def5" --description "Documentation" --force

gh label create "sprint-0" --color "e4e669" --description "Sprint 0 - Setup" --force
gh label create "sprint-1" --color "e4e669" --description "Sprint 1" --force

gh label create "movie-service" --color "1d76db" --description "Movie Service" --force
gh label create "user-service" --color "1d76db" --description "User Service" --force
gh label create "actor-service" --color "1d76db" --description "Actor Service" --force
gh label create "ai-service" --color "1d76db" --description "AI Service" --force
gh label create "api-gateway" --color "1d76db" --description "API Gateway" --force

echo -e "${GREEN}✓ Labels created${NC}"

echo -e "${BLUE}Step 2: Creating Epic...${NC}"

# Create Epic Issue
EPIC=$(gh issue create \
  --title "[EPIC] Project Setup Phase" \
  --label "epic,P0-critical,sprint-0,infrastructure" \
  --body "$(cat <<'EOF'
## Epic Description
Complete initial project setup including directory structure, Gradle configuration, and GitHub workflows. This epic encompasses all foundational work needed before feature development can begin.

## Business Value
Establishes solid foundation for microservices development with proper tooling, CI/CD, and project management infrastructure.

## User Stories
Will be linked once created:
- Task: Initialize Project Structure
- Task: Setup Gradle Multi-Module Build
- Task: Create GitHub Templates & Workflows
- Task: Setup Docker Compose Infrastructure

## Technical Considerations
- Java 25 with Spring Boot 3.5.7
- Gradle 9.2.0 multi-module build
- Spring Cloud 2024.0.0
- Docker/Podman for local development
- PostgreSQL 17, MongoDB 8.0, Redis 7.4

## Definition of Done
- [ ] All tasks completed
- [ ] Documentation updated
- [ ] Build system working
- [ ] Local development environment functional
- [ ] CI/CD pipelines configured

## Sprint Planning
**Target Sprint:** Sprint 0  
**Story Points:** 16  
**Priority:** P0-critical
**Estimated Time:** 16-20 hours
EOF
)")

echo -e "${GREEN}✓ Epic created: $EPIC${NC}"

echo -e "${BLUE}Step 3: Creating Task Issues...${NC}"

# Task 1: Initialize Project Structure
TASK1=$(gh issue create \
  --title "[TASK] Initialize Project Structure" \
  --label "task,P0-critical,sprint-0,infrastructure" \
  --body "$(cat <<'EOF'
## Task Description
Create complete directory structure for all microservices, frontend applications, infrastructure configs, and documentation.

## Context
First step in project setup. Establishes the foundation directory structure that all subsequent work will build upon.

## Implementation Details
1. Create backend service directories (8 services + shared-library)
2. Create standard Java package structure for each service
3. Create frontend directories (web-nextjs, mobile-react-native)
4. Create infrastructure directories (docker, kubernetes, scripts)
5. Create documentation directories (architecture/adr, api, guides)
6. Create README.md for each service
7. Create .gitignore files

## Files to Create
```
backend/
├── api-gateway/
├── discovery-service/
├── config-service/
├── movie-service/
├── user-service/
├── actor-service/
├── ai-service/
├── media-service/
└── shared-library/

frontend/
├── web-nextjs/
└── mobile-react-native/

infrastructure/
├── docker/
├── kubernetes/
└── scripts/

docs/
├── architecture/adr/
├── api/
└── guides/
```

## Testing Requirements
- [ ] Verify all directories created
- [ ] Check package structure follows convention
- [ ] Ensure README.md exists for each service

## Acceptance Criteria
- [ ] All 8 backend service directories created with standard structure
- [ ] Frontend directories created
- [ ] Infrastructure directories created
- [ ] Each service has README.md
- [ ] Directory structure matches ARCHITECTURE.md specification
- [ ] .gitignore files in place

## Definition of Done
- [ ] Directory structure created
- [ ] README files written
- [ ] .gitignore configured
- [ ] Verified with `tree` command
- [ ] Committed to repository

## Estimated Time
**Story Points:** 3  
**Time Estimate:** 2-3 hours

Related to: Epic ${EPIC}
EOF
)")

echo -e "${GREEN}✓ Task 1 created: $TASK1${NC}"

# Task 2: Setup Gradle Multi-Module Build
TASK2=$(gh issue create \
  --title "[TASK] Setup Gradle Multi-Module Build" \
  --label "task,P0-critical,sprint-0,backend" \
  --body "$(cat <<'EOF'
## Task Description
Configure Gradle multi-module build system for all backend microservices with Spring Boot 3.5.7, Spring Cloud 2024.0.0, and Java 25.

## Context
Essential build system configuration that enables all services to be built, tested, and managed from root directory.

## Implementation Details
1. Create root `settings.gradle` with pluginManagement FIRST (critical order)
2. Include all 9 modules (8 services + shared-library)
3. Create root `build.gradle` with common configuration
4. Create `gradle.properties` with version management
5. Configure Java 25 toolchain for all subprojects
6. Setup Spring Boot and Spring Cloud dependency management
7. Configure each service's `build.gradle`
8. Add common plugins: spotless, jacoco, sonarqube

## Files to Create/Modify
- `settings.gradle` - Module definitions
- `build.gradle` - Root build config
- `gradle.properties` - Version management
- `backend/shared-library/build.gradle`
- `backend/discovery-service/build.gradle`
- `backend/config-service/build.gradle`
- `backend/api-gateway/build.gradle`
- `backend/movie-service/build.gradle`
- `backend/user-service/build.gradle`
- `backend/actor-service/build.gradle`
- `backend/ai-service/build.gradle`
- `backend/media-service/build.gradle`

## Key Dependencies
```properties
javaVersion=25
springBootVersion=3.5.7
springCloudVersion=2024.0.0
springAiVersion=1.0.0-M6
lombokVersion=1.18.42
mapstructVersion=1.6.3
jjwtVersion=0.12.6
grpcVersion=1.65.1
testcontainersVersion=1.20.4
```

## Testing Requirements
- [ ] Run `./gradlew clean build` successfully
- [ ] Verify all modules recognized
- [ ] Test shared-library import in services
- [ ] Check Java 25 toolchain active

## Acceptance Criteria
- [ ] `./gradlew build` runs successfully
- [ ] All 9 modules recognized by Gradle
- [ ] Java 25 toolchain configured
- [ ] Spring Boot 3.5.7 working
- [ ] Spring Cloud 2024.0.0 working
- [ ] Shared library can be imported
- [ ] Build completes without errors
- [ ] Tasks visible via `./gradlew tasks`

## Testing Commands
```bash
./gradlew clean build
./gradlew tasks --all
./gradlew :backend:movie-service:dependencies
java -version  # Should show Java 25
```

## Definition of Done
- [ ] All build files created
- [ ] Build succeeds
- [ ] Dependencies resolved
- [ ] Java 25 toolchain working
- [ ] Documentation updated

## Estimated Time
**Story Points:** 5  
**Time Estimate:** 4-5 hours

Depends on: ${TASK1}
Related to: Epic ${EPIC}
EOF
)")

echo -e "${GREEN}✓ Task 2 created: $TASK2${NC}"

# Task 3: Create GitHub Templates & Workflows
TASK3=$(gh issue create \
  --title "[TASK] Create GitHub Templates & Workflows" \
  --label "task,P1-high,sprint-0,devops" \
  --body "$(cat <<'EOF'
## Task Description
Setup GitHub issue templates, PR template, and basic CI/CD workflows for automated testing and quality checks.

## Context
Establishes professional project management and automated quality assurance processes.

## Implementation Details
1. Create issue templates (epic, user-story, task, bug)
2. Create pull request template with comprehensive checklist
3. Create GitHub Actions workflows:
   - backend-ci.yml (test, build, sonarqube)
   - frontend-ci.yml (lint, test, build)
   - project-automation.yml (auto-label, link issues)
4. Configure branch protection rules
5. Setup dependabot for dependency updates

## Files to Create
- `.github/ISSUE_TEMPLATE/epic.md`
- `.github/ISSUE_TEMPLATE/user-story.md`
- `.github/ISSUE_TEMPLATE/task.md`
- `.github/ISSUE_TEMPLATE/bug.md`
- `.github/PULL_REQUEST_TEMPLATE.md`
- `.github/workflows/backend-ci.yml`
- `.github/workflows/frontend-ci.yml`
- `.github/workflows/project-automation.yml`
- `.github/dependabot.yml`

## Testing Requirements
- [ ] Create test issue with each template
- [ ] Create test PR to verify template
- [ ] Trigger CI workflow
- [ ] Verify automation workflows

## Acceptance Criteria
- [ ] All issue templates available
- [ ] PR template appears for new PRs
- [ ] CI workflows run on push
- [ ] Project automation working
- [ ] Branch protection enabled
- [ ] Dependabot configured

## Definition of Done
- [ ] Templates created
- [ ] Workflows configured
- [ ] CI pipeline passing
- [ ] Branch protection active
- [ ] Documentation updated

## Estimated Time
**Story Points:** 3  
**Time Estimate:** 2-3 hours

Related to: Epic ${EPIC}
EOF
)")

echo -e "${GREEN}✓ Task 3 created: $TASK3${NC}"

# Task 4: Setup Docker Compose Infrastructure
TASK4=$(gh issue create \
  --title "[TASK] Setup Docker Compose Infrastructure" \
  --label "task,P1-high,sprint-0,infrastructure" \
  --body "$(cat <<'EOF'
## Task Description
Create Docker Compose configuration for local development environment with all required databases and services.

## Context
Provides consistent local development environment for all team members with all dependencies.

## Implementation Details
1. Create docker-compose.yml for local development
2. Configure PostgreSQL 17 container
3. Configure MongoDB 8.0 container
4. Configure Redis 7.4 container
5. Setup proper networking
6. Configure persistent volumes
7. Add health checks for all services
8. Create helper scripts (start, stop, logs)

## Files to Create
- `infrastructure/docker/docker-compose.yml`
- `infrastructure/docker/docker-compose.prod.yml`
- `infrastructure/docker/.env.example`
- `infrastructure/scripts/start-infrastructure.sh`
- `infrastructure/scripts/stop-infrastructure.sh`
- `infrastructure/scripts/logs.sh`

## Services to Configure
```yaml
services:
  postgres:
    image: postgres:17-alpine
    ports: ["5432:5432"]
  
  mongodb:
    image: mongo:8.0
    ports: ["27017:27017"]
  
  redis:
    image: redis:7.4-alpine
    ports: ["6379:6379"]
```

## Testing Requirements
- [ ] All containers start successfully
- [ ] Can connect to PostgreSQL
- [ ] Can connect to MongoDB
- [ ] Can connect to Redis
- [ ] Health checks pass
- [ ] Data persists after restart

## Acceptance Criteria
- [ ] `docker-compose up -d` starts all services
- [ ] All databases accessible from host
- [ ] Health checks pass
- [ ] Services can connect to databases
- [ ] Proper networking configured
- [ ] Volumes persist data
- [ ] Helper scripts work correctly

## Testing Commands
```bash
cd infrastructure/docker
docker-compose up -d
docker-compose ps
docker-compose logs -f
psql -h localhost -U admin -d filmpire
mongosh mongodb://admin:admin123@localhost:27017
redis-cli ping
```

## Definition of Done
- [ ] Docker Compose files created
- [ ] All services start successfully
- [ ] Connections verified
- [ ] Helper scripts created
- [ ] Documentation updated (README)

## Estimated Time
**Story Points:** 5  
**Time Estimate:** 4-5 hours

Related to: Epic ${EPIC}
EOF
)")

echo -e "${GREEN}✓ Task 4 created: $TASK4${NC}"

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✓ All Phase 1 issues created successfully!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "1. View all issues: gh issue list"
echo "2. Add issues to project board"
echo "3. Start with: gh issue develop $TASK1 --checkout"
echo ""
echo -e "${BLUE}Issue Summary:${NC}"
echo "  Epic: $EPIC"
echo "  Task 1 (Structure): $TASK1"
echo "  Task 2 (Gradle): $TASK2"
echo "  Task 3 (GitHub): $TASK3"
echo "  Task 4 (Docker): $TASK4"
echo ""

