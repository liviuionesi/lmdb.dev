#!/bin/bash

# Filmpire Microservices - Create Phase 2 Issues Script
# Creates all issues for Phase 2: Infrastructure Services

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Creating Phase 2 Issues - Infrastructure Services      ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}Error: GitHub CLI (gh) is not installed${NC}"
    echo "Install it with: sudo dnf install gh"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo -e "${RED}Error: Not authenticated with GitHub${NC}"
    echo "Run: gh auth login"
    exit 1
fi

echo -e "${GREEN}✓ GitHub CLI authenticated${NC}"
echo ""

# Create Epic #6
echo -e "${BLUE}Creating Epic #6: Infrastructure Services...${NC}"
EPIC6=$(gh issue create \
  --title "[EPIC] Infrastructure Services" \
  --label "epic,P0-critical,sprint-1,infrastructure" \
  --body "$(cat <<'EOF'
## Epic Description
Set up core infrastructure services that enable microservices communication, configuration management, and API routing.

## Business Value
Establishes the foundation for microservices architecture with service discovery, centralized configuration, and API gateway for routing and security.

## User Stories
- #7 - Implement Discovery Service (Eureka)
- #8 - Implement Config Service
- #9 - Implement API Gateway
- #10 - Implement Shared Library

## Technical Stack
- Spring Cloud Netflix Eureka
- Spring Cloud Config
- Spring Cloud Gateway
- Spring Boot 3.5.8
- Java 25

## Story Points
21

## Target Sprint
Sprint 1-2

## Estimated Time
16-20 hours

## Dependencies
Phase 1 (Complete ✅)

## Acceptance Criteria
- [ ] All infrastructure services running
- [ ] Service discovery working
- [ ] Configuration management operational
- [ ] API Gateway routing all requests
- [ ] Zero downtime during service restarts
- [ ] All health checks green
- [ ] Documentation complete

## Definition of Done
- [ ] All code reviewed and merged
- [ ] Tests passing (85%+ coverage)
- [ ] Documentation updated
- [ ] Services deployed to dev
- [ ] Health checks passing

For detailed implementation, see: .github/issues/PHASE2_INFRASTRUCTURE_SERVICES.md
EOF
)")

echo -e "${GREEN}✓ Epic #6 created: $EPIC6${NC}"
echo ""

# Create Issue #7: Discovery Service
echo -e "${BLUE}Creating Issue #7: Discovery Service (Eureka)...${NC}"
ISSUE7=$(gh issue create \
  --title "[TASK] Implement Discovery Service (Eureka Server)" \
  --label "task,P0-critical,sprint-1,infrastructure,discovery-service" \
  --body "$(cat <<'EOF'
## Task Description
Implement Spring Cloud Netflix Eureka Server for service discovery and registration.

## Implementation Checklist
- [ ] Create Eureka Server main class with @EnableEurekaServer
- [ ] Configure application.yml (port 8761, standalone mode)
- [ ] Add actuator endpoints for health checks
- [ ] Configure Eureka dashboard
- [ ] Create Dockerfile with multi-stage build
- [ ] Write unit tests for server startup
- [ ] Write integration tests for registration
- [ ] Update README with usage instructions

## Dependencies
```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

## Configuration
```yaml
server:
  port: 8761

spring:
  application:
    name: discovery-service

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: false
```

## Acceptance Criteria
- [ ] Eureka server starts on port 8761
- [ ] Dashboard accessible at http://localhost:8761
- [ ] Health endpoint returns UP status
- [ ] Can register services
- [ ] All tests passing (unit + integration)
- [ ] Docker image builds successfully

## Testing Commands
```bash
./gradlew :backend:discovery-service:bootRun
curl http://localhost:8761/actuator/health
open http://localhost:8761
```

## Story Points
5

## Estimated Time
3-4 hours

Related to: Epic $EPIC6

For detailed implementation, see: .github/issues/PHASE2_INFRASTRUCTURE_SERVICES.md
EOF
)")

echo -e "${GREEN}✓ Issue #7 created: $ISSUE7${NC}"
echo ""

# Create Issue #8: Config Service
echo -e "${BLUE}Creating Issue #8: Config Service...${NC}"
ISSUE8=$(gh issue create \
  --title "[TASK] Implement Config Service (Spring Cloud Config)" \
  --label "task,P0-critical,sprint-1,infrastructure,config-service" \
  --body "$(cat <<'EOF'
## Task Description
Implement Spring Cloud Config Server for centralized configuration management with Git backend.

## Implementation Checklist
- [ ] Create Config Server main class with @EnableConfigServer
- [ ] Configure application.yml (port 8888, Git backend)
- [ ] Create separate config repository (filmpire-config-repo)
- [ ] Add environment-specific configs (dev, prod)
- [ ] Add service-specific configs for each microservice
- [ ] Configure encryption for sensitive data
- [ ] Register with Eureka
- [ ] Create Dockerfile
- [ ] Write tests for config retrieval
- [ ] Document configuration structure

## Dependencies
```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-config-server'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

## Config Repository Structure
```
filmpire-config-repo/
├── application.yml          # Shared configuration
├── application-dev.yml      # Development environment
├── application-prod.yml     # Production environment
├── movie-service.yml
├── user-service.yml
├── actor-service.yml
├── ai-service.yml
├── media-service.yml
└── api-gateway.yml
```

## Acceptance Criteria
- [ ] Config server starts on port 8888
- [ ] Can retrieve configuration from Git
- [ ] Environment-specific configs work
- [ ] Encryption/decryption working
- [ ] Registered with Eureka
- [ ] All tests passing
- [ ] Config repository created and documented

## Testing Commands
```bash
./gradlew :backend:config-service:bootRun
curl http://localhost:8888/movie-service/dev
curl http://localhost:8888/actuator/health
```

## Story Points
8

## Estimated Time
6-8 hours

Related to: Epic $EPIC6

For detailed implementation, see: .github/issues/PHASE2_INFRASTRUCTURE_SERVICES.md
EOF
)")

echo -e "${GREEN}✓ Issue #8 created: $ISSUE8${NC}"
echo ""

# Create Issue #9: API Gateway
echo -e "${BLUE}Creating Issue #9: API Gateway...${NC}"
ISSUE9=$(gh issue create \
  --title "[TASK] Implement API Gateway (Spring Cloud Gateway)" \
  --label "task,P0-critical,sprint-2,infrastructure,api-gateway" \
  --body "$(cat <<'EOF'
## Task Description
Implement Spring Cloud Gateway as the single entry point for all client requests with routing, rate limiting, and security.

## Implementation Checklist
- [ ] Create Gateway main class
- [ ] Configure application.yml (port 8080, routes)
- [ ] Implement route predicates for each service
- [ ] Add rate limiting with Redis
- [ ] Add circuit breaker with Resilience4j
- [ ] Implement CORS configuration
- [ ] Add request/response logging
- [ ] Implement JWT authentication filter
- [ ] Register with Eureka for service discovery
- [ ] Create Dockerfile
- [ ] Write unit tests for routing
- [ ] Write integration tests with TestContainers
- [ ] Document all routes and filters

## Dependencies
```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation "io.jsonwebtoken:jjwt-api:${jjwtVersion}"
}
```

## Acceptance Criteria
- [ ] Gateway starts on port 8080
- [ ] Routes to all services working
- [ ] Rate limiting functional
- [ ] Circuit breaker working
- [ ] CORS configured
- [ ] JWT authentication working
- [ ] Registered with Eureka
- [ ] All tests passing
- [ ] API documentation updated

## Testing Commands
```bash
./gradlew :backend:api-gateway:bootRun
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/movies
```

## Story Points
8

## Estimated Time
6-8 hours

Related to: Epic $EPIC6

For detailed implementation, see: .github/issues/PHASE2_INFRASTRUCTURE_SERVICES.md
EOF
)")

echo -e "${GREEN}✓ Issue #9 created: $ISSUE9${NC}"
echo ""

# Create Issue #10: Shared Library
echo -e "${BLUE}Creating Issue #10: Shared Library...${NC}"
ISSUE10=$(gh issue create \
  --title "[TASK] Implement Shared Library" \
  --label "task,P1-high,sprint-1,backend,shared-library" \
  --body "$(cat <<'EOF'
## Task Description
Create shared library module with common DTOs, exceptions, utilities, and constants used across all microservices.

## Implementation Checklist
- [ ] Create common DTOs (ApiResponse, ErrorResponse, PageResponse)
- [ ] Create custom exception classes (ResourceNotFoundException, etc.)
- [ ] Implement utility classes (DateUtils, StringUtils, ValidationUtils)
- [ ] Create constants (ApiConstants, ErrorCodes)
- [ ] Implement common annotations (@LogExecutionTime, @ValidateRequest)
- [ ] Add MapStruct mappers configuration
- [ ] Add Lombok configuration
- [ ] Write unit tests for all utilities
- [ ] Create comprehensive JavaDoc
- [ ] Update README with usage examples

## Structure
```
shared-library/
└── src/main/java/com/filmpire/shared/
    ├── dto/
    ├── exception/
    ├── util/
    ├── constant/
    └── annotation/
```

## Key Classes
- ApiResponse<T>
- ErrorResponse
- PageResponse<T>
- ResourceNotFoundException
- ValidationException

## Acceptance Criteria
- [ ] All common DTOs implemented
- [ ] Exception classes and error DTOs implemented (exception handling framework)
- [ ] Utility classes tested
- [ ] Constants defined
- [ ] Annotations working
- [ ] All services can import and use shared library
- [ ] JavaDoc complete
- [ ] All tests passing (100% coverage)

## Testing Commands
```bash
./gradlew :backend:shared-library:build
./gradlew :backend:shared-library:test
./gradlew :backend:shared-library:jacocoTestReport
```

## Story Points
5

## Estimated Time
4-5 hours

Related to: Epic $EPIC6

For detailed implementation, see: .github/issues/PHASE2_INFRASTRUCTURE_SERVICES.md
EOF
)")

echo -e "${GREEN}✓ Issue #10 created: $ISSUE10${NC}"
echo ""

echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✓ All Phase 2 issues created successfully!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo ""
echo -e "${BLUE}Issue Summary:${NC}"
echo "  Epic #6: Infrastructure Services"
echo "  Issue #7: Discovery Service (Eureka)"
echo "  Issue #8: Config Service"
echo "  Issue #9: API Gateway"
echo "  Issue #10: Shared Library"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "1. View all issues: gh issue list"
echo "2. Add issues to project board (GitHub web UI)"
echo "3. Start Sprint 1 with: gh issue develop 7 --checkout"
echo ""

