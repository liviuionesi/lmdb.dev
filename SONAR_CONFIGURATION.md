# 🎯 SonarLint Configuration - All Backend Services

## Overview

This project uses **SonarLint** for static code analysis with customized rules for test files.

## Configuration Structure

```
filmpire-microservices/
├── .sonarlint/settings.json              # Root config
├── sonar-project.properties              # Root Sonar config
└── backend/
    ├── movie-service/
    │   ├── .sonarlint/settings.json      # Service-specific config
    │   └── sonar-project.properties      # Service-specific Sonar config
    ├── api-gateway/
    │   └── .sonarlint/settings.json
    ├── config-service/
    │   └── .sonarlint/settings.json
    ├── discovery-service/
    │   └── .sonarlint/settings.json
    ├── user-service/
    │   └── .sonarlint/settings.json
    ├── actor-service/
    │   └── .sonarlint/settings.json
    ├── ai-service/
    │   └── .sonarlint/settings.json
    ├── media-service/
    │   └── .sonarlint/settings.json
    └── shared-library/
        └── .sonarlint/settings.json
```

## Disabled Rules for Test Files

The following Sonar rules are **disabled** for all files matching `**/src/test/**`:

| Rule ID | Rule Name | Reason |
|---------|-----------|--------|
| **java:S100** | Method naming convention | Test methods use descriptive names with underscores (e.g., `shouldReturnMovie_WhenIdExists`) |
| **java:S3305** | Field injection warning | Field injection via `@Autowired` is acceptable and standard practice in Spring test classes |
| **java:S1192** | String literal duplication | Test readability is more important than DRY principle for test strings |

## Why These Rules?

### 1. Method Naming (S100)

**Production:**
```java
// ✅ Follows camelCase
public void calculateTotalPrice() { }
```

**Tests:**
```java
// ✅ Descriptive with underscores
@Test
void shouldCalculateTotalPrice_WhenCartHasItems() { }

@Test
void shouldThrowException_WhenCartIsEmpty() { }
```

**Benefits:**
- ✅ Extremely readable test names
- ✅ Self-documenting behavior
- ✅ Industry standard (JUnit 5, Spring Testing)

### 2. Field Injection (S3305)

**Production:**
```java
// ✅ Constructor injection preferred
@Service
public class MovieService {
    private final MovieRepository repository;
    
    public MovieService(MovieRepository repository) {
        this.repository = repository;
    }
}
```

**Tests:**
```java
// ✅ Field injection acceptable
@WebMvcTest(MovieController.class)
class MovieControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private MovieService movieService;
}
```

**Benefits:**
- ✅ Less verbose test setup
- ✅ Framework manages lifecycle
- ✅ No manual instantiation needed
- ✅ Standard Spring testing pattern

### 3. String Duplication (S1192)

**Production:**
```java
// ❌ Should use constants
log.error("User not found");
throw new Exception("User not found");
return Response.error("User not found");
```

**Tests:**
```java
// ✅ Duplication OK for readability
mockMvc.perform(get("/api/v1/movies"))
    .andExpect(jsonPath("$.success").value(true))
    .andExpect(jsonPath("$.data").isArray());
```

**Benefits:**
- ✅ Test readability
- ✅ Self-contained test cases
- ✅ Easy to understand at a glance

## Configuration Files

### `.sonarlint/settings.json`

Used by **SonarLint IDE plugin** (IntelliJ, VS Code, Cursor):

```json
{
  "rules": {
    "java:S100": {
      "level": "off",
      "filePathPattern": "**/src/test/**"
    },
    "java:S3305": {
      "level": "off",
      "filePathPattern": "**/src/test/**"
    },
    "java:S1192": {
      "level": "off",
      "filePathPattern": "**/src/test/**"
    }
  }
}
```

### `sonar-project.properties`

Used by **SonarQube/SonarCloud** for CI/CD:

```properties
# Rules disabled for test files
sonar.issue.ignore.multicriteria=e1,e2,e3

# Disable method naming convention in tests (S100)
sonar.issue.ignore.multicriteria.e1.ruleKey=java:S100
sonar.issue.ignore.multicriteria.e1.resourceKey=**/src/test/**/*

# Disable field injection warning in tests (S3305)
sonar.issue.ignore.multicriteria.e2.ruleKey=java:S3305
sonar.issue.ignore.multicriteria.e2.resourceKey=**/src/test/**/*

# Disable string literal duplication in tests (S1192)
sonar.issue.ignore.multicriteria.e3.ruleKey=java:S1192
sonar.issue.ignore.multicriteria.e3.resourceKey=**/src/test/**/*
```

## Applied to All Services

✅ **Infrastructure Services:**
- api-gateway
- config-service
- discovery-service

✅ **Business Services:**
- movie-service
- user-service
- actor-service
- ai-service
- media-service

✅ **Library:**
- shared-library

## How to Use

### For Developers

1. **Configuration is automatic** - no action needed
2. Write tests with descriptive names using underscores
3. Use field injection in tests freely
4. Duplicate test strings for readability

### For New Services

When creating a new backend service:

```bash
# 1. Create SonarLint directory
mkdir -p backend/new-service/.sonarlint

# 2. Copy configuration
cp backend/movie-service/.sonarlint/settings.json \
   backend/new-service/.sonarlint/settings.json

# 3. (Optional) Copy Sonar properties if needed for CI/CD
cp backend/movie-service/sonar-project.properties \
   backend/new-service/sonar-project.properties
```

### IDE Integration

#### IntelliJ IDEA
- **SonarLint plugin** automatically reads `.sonarlint/settings.json`
- Reload: **File** → **Invalidate Caches** → **Restart**

#### VS Code / Cursor
- **SonarLint extension** automatically reads configuration
- Reload: **Ctrl+Shift+P** → "Reload Window"

#### Eclipse
- **SonarLint plugin** reads configuration on project import
- Update: Right-click project → **SonarLint** → **Update bindings**

## CI/CD Integration

For SonarQube/SonarCloud in GitHub Actions or Jenkins:

```bash
# Run Sonar analysis
./gradlew sonarqube \
  -Dsonar.projectKey=filmpire-microservices \
  -Dsonar.host.url=$SONAR_HOST_URL \
  -Dsonar.login=$SONAR_TOKEN
```

The `sonar-project.properties` files ensure test rules are disabled in CI/CD too.

## Maintenance

### Adding New Rules

To disable additional rules for tests:

1. Edit `.sonarlint/settings.json`:
   ```json
   {
     "rules": {
       "java:SXXXX": {
         "level": "off",
         "filePathPattern": "**/src/test/**"
       }
     }
   }
   ```

2. Edit `sonar-project.properties`:
   ```properties
   sonar.issue.ignore.multicriteria=e1,e2,e3,e4
   
   sonar.issue.ignore.multicriteria.e4.ruleKey=java:SXXXX
   sonar.issue.ignore.multicriteria.e4.resourceKey=**/src/test/**/*
   ```

3. Apply to all services:
   ```bash
   for service in backend/*/; do
     cp .sonarlint/settings.json "$service/.sonarlint/"
   done
   ```

### Checking Coverage

Each service can generate coverage reports:

```bash
# For a specific service
./gradlew :backend:movie-service:test jacocoTestReport

# View report
open backend/movie-service/build/reports/jacoco/test/html/index.html
```

## Best Practices

### ✅ DO in Tests:
- Use descriptive test method names with underscores
- Use field injection (`@Autowired`, `@MockBean`)
- Duplicate strings for clarity
- Focus on readability over DRY

### ❌ DON'T in Tests:
- Use `@SuppressWarnings` (rules are globally disabled)
- Over-optimize for code reuse at cost of clarity
- Mix production and test code standards

### ✅ DO in Production:
- Follow strict camelCase naming
- Use constructor injection
- Extract string constants
- Maintain high code quality standards

## Support

For issues or questions about Sonar configuration:
1. Check this document first
2. Review `.sonarlint/settings.json` in any service
3. Consult SonarLint documentation: https://www.sonarsource.com/products/sonarlint/

---

**Last Updated:** November 17, 2025  
**Configuration Version:** 1.0  
**Applies to:** All backend services in `filmpire-microservices`


