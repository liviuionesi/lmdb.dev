# Gradle Multi-Module Build Configuration

## Overview
Complete Gradle multi-module build system for Filmpire microservices platform.

## Architecture

### Build Structure
```
filmpire-microservices/
├── settings.gradle           # Module definitions & repository management
├── build.gradle              # Root build configuration
├── gradle.properties         # Version management
└── backend/
    ├── api-gateway/
    ├── config-service/
    ├── discovery-service/
    ├── movie-service/
    ├── user-service/
    ├── actor-service/
    ├── ai-service/
    ├── media-service/
    └── shared-library/
```

## Technology Stack

### Versions (Managed in gradle.properties)
**IMPORTANT:** All versions are centralized in `gradle.properties` at the root level for easy updates.

- **Java**: 25 (via SDKMAN)
- **Gradle**: 9.2.0 (via Gradle Wrapper)
- **Spring Boot**: 3.5.8-SNAPSHOT
- **Spring Cloud**: 2025.0.0
- **Spring AI**: 1.0.0-SNAPSHOT
- **Spring Dependency Management**: 1.1.7

### Key Dependencies (gradle.properties)
- Lombok: 1.18.42
- MapStruct: 1.6.3
- JJWT: 0.13.0
- gRPC: 1.76.0
- Springdoc OpenAPI: 2.8.14
- MinIO: 8.5.7

### Testing Dependencies (gradle.properties)
- JUnit: 5.11.3 **(Jupiter ONLY - JUnit 4 FORBIDDEN)**
- Mockito: 5.19.0
- TestContainers: 1.21.2
- JaCoCo: 0.8.14

### Critical Testing Requirements
- ✅ `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` - **REQUIRED for Cursor IDE Test Runner**
- ✅ JUnit 5 (Jupiter) exclusively - NO JUnit 4
- ✅ Testcontainers with `@ServiceConnection` - NO H2
- ✅ Tests run via Cursor IDE (CodeLens "Run Test" buttons)

## Configuration Details

### Root Build Configuration

#### gradle.properties ⭐ **VERSION MANAGEMENT HUB**
**THIS IS THE SINGLE SOURCE OF TRUTH FOR ALL DEPENDENCY VERSIONS**

```properties
# Java
javaVersion=25

# Spring Boot  
springBootVersion=3.5.8-SNAPSHOT
springDependencyManagementVersion=1.1.7

# Spring Cloud
springCloudVersion=2025.0.0

# Spring AI
springAiVersion=1.0.0-SNAPSHOT

# Dependencies
lombokVersion=1.18.42
mapstructVersion=1.6.3
jjwtVersion=0.13.0
grpcVersion=1.76.0
springdocVersion=2.8.14
minioVersion=8.5.7

# Testing
junitVersion=5.11.3
mockitoVersion=5.19.0
testcontainersVersion=1.21.2
jacocoVersion=0.8.14

# Build
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
org.gradle.parallel=false
org.gradle.caching=false
org.gradle.configuration-cache=false
```

**To update a dependency across the entire project:**
1. Edit version in `gradle.properties`
2. Run `./gradlew clean build`
3. All modules automatically use the new version

#### settings.gradle
- Configures plugin management with Spring repositories (milestone, snapshot, release)
- Enables dependency resolution management (FAIL_ON_PROJECT_REPOS mode)
- Includes all 9 modules

#### build.gradle (Root - Groovy DSL)
- Applies Spring Boot & dependency management plugins to root
- Configures Java 25 toolchain for all subprojects
- Sets up JaCoco for test coverage
- Configures common test dependencies (including `junit-platform-launcher`)
- UTF-8 encoding for all Java compilation
- **Uses Groovy DSL, NOT Kotlin DSL**

### Service-Specific Configurations

#### Infrastructure Services
**Discovery Service (Eureka)**
- Spring Cloud Netflix Eureka Server
- Actuator for health monitoring

**Config Service**
- Spring Cloud Config Server
- Eureka client registration

**API Gateway**
- Spring Cloud Gateway (reactive)
- Redis for rate limiting & session
- JWT authentication (JJWT)
- Spring Security

#### Domain Services
**Movie Service**
- MongoDB for document storage
- Redis caching
- OpenFeign for inter-service communication
- Springdoc OpenAPI
- TestContainers (MongoDB)

**User Service**
- PostgreSQL with JPA
- Flyway migrations
- Spring Security + JWT
- TestContainers (PostgreSQL)

**Actor Service**
- PostgreSQL with JPA
- Flyway migrations
- TestContainers (PostgreSQL)

**AI Service**
- Spring AI (OpenAI integration)
- MongoDB storage
- gRPC communication
- TestContainers (MongoDB)

**Media Service**
- MinIO for object storage
- MongoDB metadata storage
- TestContainers (MongoDB)

**Shared Library**
- Common DTOs, utilities, exceptions
- Reusable across all services
- Not a bootable JAR (plain JAR)

## Build Commands

### Full Build
```bash
./gradlew clean build
```

### Build Without Tests
```bash
./gradlew clean build -x test
```

### Build Specific Service
```bash
./gradlew :backend:movie-service:build
```

### Run Specific Service
```bash
./gradlew :backend:discovery-service:bootRun
```

### Check Dependencies
```bash
./gradlew :backend:movie-service:dependencies
```

### Generate Coverage Report
```bash
./gradlew jacocoTestReport
```

### List All Tasks
```bash
./gradlew tasks --all
```

## Testing Configuration (Spring Boot 3.5.x Standards)

### Test Framework Requirements
**CRITICAL for Cursor IDE Test Runner compatibility:**

1. **JUnit 5 (Jupiter) ONLY** - JUnit 4 is **FORBIDDEN**
2. **junit-platform-launcher** - Required in every service:
   ```groovy
   testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
   ```
3. **@MockitoBean** instead of `@MockBean` (Spring Boot 3.4+)
4. **Testcontainers with @ServiceConnection** - NO H2 databases
5. **Tests run via Cursor IDE Test Runner** - NOT terminal

### Test Dependencies (Common to all services)
```groovy
dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'  // if security enabled
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'  // or mongodb
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'  // CRITICAL!
}

tasks.named('test') {
    useJUnitPlatform()  // Enable JUnit 5
}
```

### Coverage
- JaCoco plugin enabled on all modules
- Reports in XML and HTML formats
- Test coverage reports auto-generated after tests
- Minimum 85% coverage required

## Dependency Management

### Dependency Resolution
- Centralized in `settings.gradle` using `dependencyResolutionManagement`
- Spring Cloud BOM imported in each service
- Version variables from `gradle.properties`
- No project-level repositories allowed (FAIL_ON_PROJECT_REPOS)

### Inter-Module Dependencies
**Services with shared-library dependency:**
- `actor-service` - REST APIs, error handling
- `movie-service` - REST APIs, pagination, error handling  
- `user-service` - REST APIs, validation, authentication
- `ai-service` - REST APIs, error handling
- `media-service` - REST APIs, file handling, error handling
- `api-gateway` - REST endpoints, error handling, security

**Services without shared-library dependency:**
- `config-service` - Infrastructure only (Config Server)
- `discovery-service` - Infrastructure only (Eureka Server)

Dependency declaration:
```groovy
implementation project(':backend:shared-library')
```

## Repository Configuration

### Plugin Repositories
1. Gradle Plugin Portal
2. Maven Central
3. Spring Milestone
4. Spring Snapshot
5. Spring Release

### Dependency Repositories
1. Maven Central
2. Spring Milestone
3. Spring Release

## Important Notes

### Version Management Strategy ⭐
**ALWAYS use gradle.properties for version management:**
```groovy
// In service build.gradle - reference properties from gradle.properties
dependencies {
    implementation "org.projectlombok:lombok:${lombokVersion}"
    testImplementation "org.junit.jupiter:junit-jupiter:${junitVersion}"
    testImplementation "org.testcontainers:testcontainers-bom:${testcontainersVersion}"
}
```

**Benefits:**
- ✅ Single source of truth for all versions
- ✅ Easy project-wide updates
- ✅ Consistent versions across all modules
- ✅ No version conflicts

### Version Notes
⚠️ **Using SNAPSHOT versions:**
- Spring Boot 3.5.8-SNAPSHOT (from Spring snapshot repository)
- Spring AI 1.0.0-SNAPSHOT (from Spring snapshot repository)
- Spring Cloud 2025.0.0 ✅ (stable release)

### Java 25 Toolchain
- Configured in root `build.gradle`
- Applied to all subprojects automatically
- Release target set to Java 25
- Installed via SDKMAN: `sdk install java 25-open`

### Gradle Groovy DSL (NOT Kotlin)
- All build files use Groovy syntax
- `build.gradle` (NOT `build.gradle.kts`)
- String properties: `'dependency'` not `"dependency"`
- No parentheses in many places

### Shared Library
- Produces plain JAR (not executable)
- `bootJar` disabled, `jar` enabled
- Must be built before services that depend on it

### TestContainers with @ServiceConnection
- ✅ Modern approach (Spring Boot 3.1+): Use `@ServiceConnection`
- ❌ Old approach: `@DynamicPropertySource` not needed
- Enabled for services with databases
- PostgreSQL containers: user-service, actor-service (and ai-service when built —
  `pgvector/pgvector:pg17`, not the stock image, per ADR-012)
- MongoDB containers: movie-service, media-service
- Automatic lifecycle management in tests

## Gradle Optimization

### Performance Settings
```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
org.gradle.parallel=true
org.gradle.caching=true
```

### Build Cache
- Enabled by default
- Speeds up incremental builds
- Shares build outputs across branches

## Verification

### Build Status
✅ All 9 modules recognized  
✅ `./gradlew clean build` completes successfully  
✅ Java 25 toolchain configured  
✅ Spring Boot 3.5.8-SNAPSHOT operational  
✅ Spring Cloud 2025.0.0 operational  
✅ TestContainers BOM configured (1.21.2)  
✅ Shared library imports working  
✅ All service tasks available  

### Test Results
```bash
BUILD SUCCESSFUL in 1m 42s
55 actionable tasks: 44 executed, 11 up-to-date
```

### Verified Artifacts
All services built successfully:
- ✅ `actor-service-1.0.0-SNAPSHOT.jar`
- ✅ `ai-service-1.0.0-SNAPSHOT.jar`
- ✅ `api-gateway-1.0.0-SNAPSHOT.jar`
- ✅ `config-service-1.0.0-SNAPSHOT.jar`
- ✅ `discovery-service-1.0.0-SNAPSHOT.jar`
- ✅ `media-service-1.0.0-SNAPSHOT.jar`
- ✅ `movie-service-1.0.0-SNAPSHOT.jar`
- ✅ `shared-library-1.0.0-SNAPSHOT.jar`
- ✅ `user-service-1.0.0-SNAPSHOT.jar`

## Troubleshooting

### Common Issues

**Issue**: Plugin not found  
**Solution**: Ensure Spring milestone/snapshot repos in `settings.gradle`

**Issue**: Repository conflict  
**Solution**: Repositories only in `settings.gradle`, not `build.gradle`

**Issue**: JaCoco error  
**Solution**: JaCoco is core plugin, don't use `apply false` in root

**Issue**: Shared library dependency error  
**Solution**: Ensure `springBootVersion` variable used in shared-library BOM

**Issue**: Java version mismatch  
**Solution**: Verify Java 25 installed: `java -version`

**Issue**: `Resolution of configuration ':backend:annotationProcessor' was attempted without an exclusive lock`  
**Root Cause**: Gradle 9.2.0 has stricter locking requirements. IDE Gradle plugin and terminal daemons can lock each other.  
**Solution**:
```bash
# 1. Kill all Gradle processes
pkill -9 -f "java.*gradle"

# 2. Delete all caches
rm -rf ~/.gradle/caches ~/.gradle/daemon .gradle

# 3. Disable parallel/caching in gradle.properties
org.gradle.parallel=false
org.gradle.caching=false
org.gradle.configuration-cache=false

# 4. Reload IDE window (Ctrl+Shift+P → "Developer: Reload Window")
```

**Issue**: Unresolved testcontainers dependencies  
**Solution**: Add testcontainers BOM to root `build.gradle`:
```groovy
dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:${springBootVersion}"
        mavenBom "org.testcontainers:testcontainers-bom:${testcontainersVersion}"
    }
}
```

## Task Completion Status

### ✅ Task #3: Setup Gradle Multi-Module Build - COMPLETE

**Acceptance Criteria Met:**
- ✅ `./gradlew build` runs successfully (1m 42s)
- ✅ All 9 modules recognized by Gradle
- ✅ Java 25 toolchain configured and active
- ✅ Spring Boot 3.5.8-SNAPSHOT working
- ✅ Spring Cloud 2025.0.0 working
- ✅ Shared library imports functional (5 services using it)
- ✅ Build completes without errors
- ✅ Tasks visible via `./gradlew tasks`
- ✅ TestContainers BOM configured
- ✅ All JARs built (9 artifacts)

**Files Created/Modified:**
- ✅ `settings.gradle` - Module definitions & repository management
- ✅ `build.gradle` - Root build config with common plugins
- ✅ `gradle.properties` - Centralized version management
- ✅ `backend/*/build.gradle` - All 9 service build files

**Story Points:** 5  
**Actual Time:** ~5 hours  
**Status:** ✅ COMPLETE

### Next Steps

1. ⏭️ Task #4: Create GitHub repository structure
2. ⏭️ Task #5: Setup CI/CD pipeline

## References

- [Spring Boot Gradle Plugin](https://docs.spring.io/spring-boot/docs/current/gradle-plugin/reference/htmlsingle/)
- [Spring Cloud Release Train](https://spring.io/projects/spring-cloud)
- [Gradle Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [JaCoco Gradle Plugin](https://docs.gradle.org/current/userguide/jacoco_plugin.html)

