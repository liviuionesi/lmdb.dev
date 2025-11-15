# Phase 2: Infrastructure Services - GitHub Issues

**Sprint:** 1-2 (2 weeks)  
**Focus:** Discovery, Config, API Gateway  
**Status:** Ready to Start

---

## Epic

### Issue #6: [EPIC] Infrastructure Services

**Labels:** `epic`, `P0-critical`, `sprint-1`, `infrastructure`

**Description:**
Set up core infrastructure services that enable microservices communication, configuration management, and API routing.

**Business Value:**
Establishes the foundation for microservices architecture with service discovery, centralized configuration, and API gateway for routing and security.

**User Stories:**
- #7 - Implement Discovery Service (Eureka)
- #8 - Implement Config Service
- #9 - Implement API Gateway
- #10 - Implement Shared Library

**Technical Stack:**
- Spring Cloud Netflix Eureka
- Spring Cloud Config
- Spring Cloud Gateway
- Spring Boot 3.5.8-SNAPSHOT
- Java 25

**Story Points:** 21  
**Target Sprint:** Sprint 1-2  
**Estimated Time:** 16-20 hours

---

## User Stories / Tasks

### Issue #7: [TASK] Implement Discovery Service (Eureka Server)

**Labels:** `task`, `P0-critical`, `sprint-1`, `infrastructure`, `discovery-service`

**Description:**
Implement Spring Cloud Netflix Eureka Server for service discovery and registration.

**Implementation Checklist:**
- [ ] Create Eureka Server main class with @EnableEurekaServer
- [ ] Configure application.yml (port 8761, standalone mode)
- [ ] Add actuator endpoints for health checks
- [ ] Configure Eureka dashboard
- [ ] Create Dockerfile with multi-stage build
- [ ] Write unit tests for server startup
- [ ] Write integration tests for registration
- [ ] Update README with usage instructions

**Dependencies (build.gradle):**
```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

**Configuration (application.yml):**
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

**Acceptance Criteria:**
- [ ] Eureka server starts on port 8761
- [ ] Dashboard accessible at http://localhost:8761
- [ ] Health endpoint returns UP status
- [ ] Can register services
- [ ] All tests passing (unit + integration)
- [ ] Docker image builds successfully

**Testing Commands:**
```bash
./gradlew :backend:discovery-service:bootRun
curl http://localhost:8761/actuator/health
open http://localhost:8761
```

**Story Points:** 5  
**Estimated Time:** 3-4 hours

---

### Issue #8: [TASK] Implement Config Service (Spring Cloud Config)

**Labels:** `task`, `P0-critical`, `sprint-1`, `infrastructure`, `config-service`

**Description:**
Implement Spring Cloud Config Server for centralized configuration management with Git backend.

**Implementation Checklist:**
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

**Dependencies (build.gradle):**
```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-config-server'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

**Config Repository Structure:**
```
filmpire-config-repo/
├── application.yml          # Shared configuration
├── application-dev.yml      # Development environment
├── application-prod.yml     # Production environment
├── movie-service.yml        # Movie service config
├── user-service.yml         # User service config
├── actor-service.yml        # Actor service config
├── ai-service.yml           # AI service config
├── media-service.yml        # Media service config
└── api-gateway.yml          # Gateway config
```

**Acceptance Criteria:**
- [ ] Config server starts on port 8888
- [ ] Can retrieve configuration from Git
- [ ] Environment-specific configs work
- [ ] Encryption/decryption working
- [ ] Registered with Eureka
- [ ] All tests passing
- [ ] Config repository created and documented

**Testing Commands:**
```bash
./gradlew :backend:config-service:bootRun
curl http://localhost:8888/movie-service/dev
curl http://localhost:8888/actuator/health
```

**Story Points:** 8  
**Estimated Time:** 6-8 hours

---

### Issue #9: [TASK] Implement API Gateway (Spring Cloud Gateway)

**Labels:** `task`, `P0-critical`, `sprint-2`, `infrastructure`, `api-gateway`

**Description:**
Implement Spring Cloud Gateway as the single entry point for all client requests with routing, rate limiting, and security.

**Implementation Checklist:**
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

**Dependencies (build.gradle):**
```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation "io.jsonwebtoken:jjwt-api:${jjwtVersion}"
    implementation "io.jsonwebtoken:jjwt-impl:${jjwtVersion}"
}
```

**Route Configuration:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: movie-service
          uri: lb://movie-service
          predicates:
            - Path=/api/v1/movies/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
```

**Acceptance Criteria:**
- [ ] Gateway starts on port 8080
- [ ] Routes to all services working
- [ ] Rate limiting functional
- [ ] Circuit breaker working
- [ ] CORS configured
- [ ] JWT authentication working
- [ ] Registered with Eureka
- [ ] All tests passing
- [ ] API documentation updated

**Testing Commands:**
```bash
./gradlew :backend:api-gateway:bootRun
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/movies
```

**Story Points:** 8  
**Estimated Time:** 6-8 hours

---

### Issue #10: [TASK] Implement Shared Library

**Labels:** `task`, `P1-high`, `sprint-1`, `backend`, `shared-library`

**Description:**
Create shared library module with common DTOs, exceptions, utilities, and constants used across all microservices.

**Implementation Checklist:**
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

**Structure:**
```
shared-library/
└── src/main/java/com/filmpire/shared/
    ├── dto/
    │   ├── ApiResponse.java
    │   ├── ErrorResponse.java
    │   └── PageResponse.java
    ├── exception/
    │   ├── ResourceNotFoundException.java
    │   ├── ValidationException.java
    │   └── GlobalExceptionHandler.java
    ├── util/
    │   ├── DateUtils.java
    │   ├── StringUtils.java
    │   └── ValidationUtils.java
    ├── constant/
    │   ├── ApiConstants.java
    │   └── ErrorCodes.java
    └── annotation/
        ├── LogExecutionTime.java
        └── ValidateRequest.java
```

**Key Classes to Implement:**

**ApiResponse.java:**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
}
```

**GlobalExceptionHandler.java:**
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex);
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex);
}
```

**Acceptance Criteria:**
- [ ] All common DTOs implemented
- [ ] Exception handling framework complete
- [ ] Utility classes tested
- [ ] Constants defined
- [ ] Annotations working
- [ ] All services can import and use shared library
- [ ] JavaDoc complete
- [ ] All tests passing (100% coverage)

**Testing Commands:**
```bash
./gradlew :backend:shared-library:build
./gradlew :backend:shared-library:test
./gradlew :backend:shared-library:jacocoTestReport
```

**Story Points:** 5  
**Estimated Time:** 4-5 hours

---

## Testing Strategy

### Integration Testing
- [ ] Test service discovery with Eureka
- [ ] Test config retrieval from Config Server
- [ ] Test API Gateway routing to all services
- [ ] Test rate limiting and circuit breaker
- [ ] Test end-to-end request flow

### Performance Testing
- [ ] Measure service discovery latency
- [ ] Measure config retrieval time
- [ ] Measure gateway routing overhead
- [ ] Test rate limiting under load

---

## Definition of Done

For all tasks in this phase:

✅ **Code Quality**
- All code follows Clean Code principles
- SOLID principles applied
- Design patterns documented
- No SonarQube issues

✅ **Testing**
- Unit tests with min 85% coverage
- Integration tests with TestContainers
- All tests passing
- Performance benchmarks met

✅ **Documentation**
- JavaDoc complete for all public APIs
- README updated with setup instructions
- Architecture Decision Records (ADRs) created
- OpenAPI specs updated

✅ **Deployment**
- Dockerfile created and tested
- Service starts successfully
- Health checks passing
- Registered with Eureka (where applicable)

---

## Success Metrics

- [ ] All infrastructure services running
- [ ] Service discovery working
- [ ] Configuration management operational
- [ ] API Gateway routing all requests
- [ ] Zero downtime during service restarts
- [ ] All health checks green
- [ ] Documentation complete

---

**Phase Status:** Ready to Start  
**Dependencies:** Phase 1 (Complete)  
**Estimated Duration:** 2 weeks  
**Total Story Points:** 26

