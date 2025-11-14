# Phase 3: Core Microservices - GitHub Issues

**Sprint:** 3-5 (3 weeks)  
**Focus:** Movie, User, Actor Services  
**Status:** Pending (After Phase 2)

---

## Epic

### Issue #11: [EPIC] Core Microservices

**Labels:** `epic`, `P0-critical`, `sprint-3`, `backend`

**Description:**
Implement the three core business microservices that form the foundation of the Filmpire platform: Movie Service (TMDB integration + caching), User Service (authentication + profiles), and Actor Service (cast information).

**Business Value:**
Delivers core functionality for movie discovery, user management, and actor information. Enables users to browse movies, create accounts, and explore cast details.

**User Stories:**
- #12 - Implement Movie Service
- #13 - Implement User Service  
- #14 - Implement Actor Service
- #15 - Implement Service Integration Tests

**Technical Stack:**
- Movie Service: Spring Boot + MongoDB + Redis + TMDB API
- User Service: Spring Boot + PostgreSQL + JWT + Spring Security
- Actor Service: Spring Boot + PostgreSQL + TMDB API

**Story Points:** 34  
**Target Sprint:** Sprint 3-5  
**Estimated Time:** 24-30 hours

---

## User Stories / Tasks

### Issue #12: [TASK] Implement Movie Service

**Labels:** `task`, `P0-critical`, `sprint-3`, `backend`, `movie-service`

**Description:**
Implement Movie Service with TMDB API integration, MongoDB storage, Redis caching, and comprehensive movie discovery endpoints.

**Implementation Checklist:**
- [ ] Create domain models (Movie, Genre, Certification, Video, Credits)
- [ ] Implement MongoDB repositories with custom queries
- [ ] Create DTOs and MapStruct mappers
- [ ] Implement TMDB API client with Feign
- [ ] Implement hybrid caching strategy (MongoDB + Redis)
- [ ] Create REST API endpoints (discover, search, details, trending, popular)
- [ ] Implement pagination and filtering
- [ ] Add rate limiting for TMDB API calls
- [ ] Configure Eureka client registration
- [ ] Add actuator endpoints and health checks
- [ ] Implement OpenAPI documentation
- [ ] Write unit tests (85%+ coverage)
- [ ] Write integration tests with TestContainers
- [ ] Create Dockerfile

**API Endpoints:**
```
GET  /api/v1/movies/discover        # Discover movies with filters
GET  /api/v1/movies/search          # Search movies
GET  /api/v1/movies/{id}            # Get movie details
GET  /api/v1/movies/trending        # Trending movies
GET  /api/v1/movies/popular         # Popular movies
GET  /api/v1/movies/top-rated       # Top rated movies
GET  /api/v1/movies/{id}/videos     # Movie trailers/videos
GET  /api/v1/movies/{id}/credits    # Movie cast & crew
GET  /api/v1/movies/{id}/similar    # Similar movies
GET  /api/v1/movies/{id}/recommendations  # Recommended movies
GET  /api/v1/genres                 # Get all genres
```

**Domain Model:**
```java
@Document(collection = "movies")
@Data
@Builder
public class Movie {
    @Id
    private String id;
    private Long tmdbId;
    private String title;
    private String overview;
    private String posterPath;
    private String backdropPath;
    private LocalDate releaseDate;
    private Double voteAverage;
    private Integer voteCount;
    private List<Genre> genres;
    private Integer runtime;
    private String status;
    private Long budget;
    private Long revenue;
    private List<String> spokenLanguages;
    private List<ProductionCompany> productionCompanies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer tmdbSyncVersion;
}
```

**Caching Strategy:**
```
1. Check Redis cache (5 min TTL)
2. If not found, check MongoDB
3. If not found, fetch from TMDB API
4. Store in MongoDB and Redis
5. Return to client
```

**Dependencies:**
```groovy
dependencies {
    implementation project(':backend:shared-library')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-cache'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation "org.mapstruct:mapstruct:${mapstructVersion}"
    implementation "org.projectlombok:lombok:${lombokVersion}"
    implementation "org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdocVersion}"
    
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation "org.testcontainers:mongodb:${testcontainersVersion}"
    testImplementation "org.testcontainers:junit-jupiter:${testcontainersVersion}"
}
```

**Acceptance Criteria:**
- [ ] All TMDB endpoints implemented
- [ ] Caching working (Redis + MongoDB)
- [ ] Pagination and filtering functional
- [ ] Error handling complete
- [ ] Registered with Eureka
- [ ] OpenAPI documentation complete
- [ ] All tests passing (85%+ coverage)
- [ ] Performance: < 100ms response time (cached)
- [ ] Performance: < 500ms response time (TMDB)

**Testing Commands:**
```bash
./gradlew :backend:movie-service:bootRun
curl http://localhost:8081/api/v1/movies/discover?genre=28
curl http://localhost:8081/api/v1/movies/search?query=Inception
curl http://localhost:8081/api/v1/movies/550
curl http://localhost:8081/swagger-ui.html
```

**Story Points:** 13  
**Estimated Time:** 10-12 hours

---

### Issue #13: [TASK] Implement User Service

**Labels:** `task`, `P0-critical`, `sprint-4`, `backend`, `user-service`

**Description:**
Implement User Service with JWT authentication, Spring Security, user registration/login, profile management, and favorites/watchlist functionality.

**Implementation Checklist:**
- [ ] Create domain models (User, Role, Favorite, Watchlist)
- [ ] Implement PostgreSQL repositories
- [ ] Create DTOs and MapStruct mappers
- [ ] Implement JWT token generation and validation
- [ ] Configure Spring Security with JWT
- [ ] Implement authentication endpoints (register, login, refresh)
- [ ] Implement user profile endpoints
- [ ] Implement favorites and watchlist endpoints
- [ ] Add password encryption (BCrypt)
- [ ] Configure Flyway migrations
- [ ] Add role-based access control (RBAC)
- [ ] Register with Eureka
- [ ] Add health checks
- [ ] Implement OpenAPI documentation
- [ ] Write unit tests (85%+ coverage)
- [ ] Write integration tests with TestContainers
- [ ] Create Dockerfile

**API Endpoints:**
```
POST /api/v1/auth/register          # User registration
POST /api/v1/auth/login             # User login
POST /api/v1/auth/refresh           # Refresh JWT token
POST /api/v1/auth/logout            # User logout
GET  /api/v1/users/profile          # Get user profile
PUT  /api/v1/users/profile          # Update profile
PUT  /api/v1/users/password         # Change password
GET  /api/v1/users/favorites        # Get favorite movies
POST /api/v1/users/favorites/{id}   # Add to favorites
DELETE /api/v1/users/favorites/{id} # Remove from favorites
GET  /api/v1/users/watchlist        # Get watchlist
POST /api/v1/users/watchlist/{id}   # Add to watchlist
DELETE /api/v1/users/watchlist/{id} # Remove from watchlist
```

**Domain Model:**
```java
@Entity
@Table(name = "users")
@Data
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Enumerated(EnumType.STRING)
    private Role role;
    
    private boolean enabled;
    private boolean accountNonLocked;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "last_login")
    private LocalDateTime lastLogin;
}
```

**Security Configuration:**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .csrf().disable()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**Database Schema (Flyway):**
```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    account_non_locked BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- V2__create_favorites_table.sql
CREATE TABLE favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    movie_id BIGINT NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, movie_id)
);

-- V3__create_watchlist_table.sql
CREATE TABLE watchlist (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    movie_id BIGINT NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, movie_id)
);
```

**Acceptance Criteria:**
- [ ] User registration and login working
- [ ] JWT tokens generated and validated
- [ ] Password encryption with BCrypt
- [ ] Profile management functional
- [ ] Favorites and watchlist working
- [ ] Role-based access control implemented
- [ ] Database migrations successful
- [ ] Registered with Eureka
- [ ] OpenAPI documentation complete
- [ ] All tests passing (85%+ coverage)
- [ ] Security tests passing

**Testing Commands:**
```bash
./gradlew :backend:user-service:bootRun
curl -X POST http://localhost:8082/api/v1/auth/register -d '{"username":"john","email":"john@example.com","password":"password"}'
curl -X POST http://localhost:8082/api/v1/auth/login -d '{"username":"john","password":"password"}'
curl http://localhost:8082/swagger-ui.html
```

**Story Points:** 13  
**Estimated Time:** 10-12 hours

---

### Issue #14: [TASK] Implement Actor Service

**Labels:** `task`, `P1-high`, `sprint-5`, `backend`, `actor-service`

**Description:**
Implement Actor Service with TMDB API integration for cast and crew information, biographies, filmographies, and images.

**Implementation Checklist:**
- [ ] Create domain models (Actor, Credit, Biography)
- [ ] Implement PostgreSQL repositories
- [ ] Create DTOs and MapStruct mappers
- [ ] Implement TMDB API client for people endpoints
- [ ] Create REST API endpoints (details, credits, images)
- [ ] Implement caching strategy
- [ ] Add pagination for filmography
- [ ] Configure Eureka client
- [ ] Add health checks
- [ ] Implement OpenAPI documentation
- [ ] Write unit tests (85%+ coverage)
- [ ] Write integration tests with TestContainers
- [ ] Create Dockerfile

**API Endpoints:**
```
GET /api/v1/actors/{id}              # Get actor details
GET /api/v1/actors/{id}/credits      # Get actor filmography
GET /api/v1/actors/{id}/images       # Get actor images
GET /api/v1/actors/popular           # Get popular actors
GET /api/v1/actors/search            # Search actors
```

**Domain Model:**
```java
@Entity
@Table(name = "actors")
@Data
@Builder
public class Actor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "tmdb_id", unique = true)
    private Long tmdbId;
    
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String biography;
    
    private LocalDate birthday;
    private LocalDate deathday;
    private String birthplace;
    private String profilePath;
    private Double popularity;
    private String knownForDepartment;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

**Acceptance Criteria:**
- [ ] All TMDB person endpoints implemented
- [ ] Actor details retrieval working
- [ ] Filmography pagination functional
- [ ] Images retrieval working
- [ ] Caching implemented
- [ ] Registered with Eureka
- [ ] OpenAPI documentation complete
- [ ] All tests passing (85%+ coverage)
- [ ] Performance: < 300ms response time

**Testing Commands:**
```bash
./gradlew :backend:actor-service:bootRun
curl http://localhost:8083/api/v1/actors/287  # Brad Pitt
curl http://localhost:8083/api/v1/actors/287/credits
curl http://localhost:8083/swagger-ui.html
```

**Story Points:** 8  
**Estimated Time:** 6-8 hours

---

### Issue #15: [TASK] Service Integration Testing

**Labels:** `task`, `P1-high`, `sprint-5`, `backend`, `testing`

**Description:**
Implement comprehensive integration tests across all core services to ensure proper communication and data flow.

**Implementation Checklist:**
- [ ] Set up integration test framework
- [ ] Create test data builders
- [ ] Write Movie-User integration tests (favorites)
- [ ] Write Movie-Actor integration tests (cast)
- [ ] Write Gateway routing tests
- [ ] Test service discovery
- [ ] Test error propagation
- [ ] Test circuit breaker scenarios
- [ ] Test rate limiting
- [ ] Create test documentation

**Test Scenarios:**
1. **Movie-User Integration:**
   - User adds movie to favorites
   - User adds movie to watchlist
   - User retrieves favorite movies
   
2. **Movie-Actor Integration:**
   - Get movie with cast details
   - Get actor with filmography
   - Search movies by actor

3. **Gateway Integration:**
   - Route requests to correct service
   - Handle service unavailability
   - Apply rate limiting

**Acceptance Criteria:**
- [ ] All integration tests passing
- [ ] Test coverage > 80%
- [ ] Performance benchmarks met
- [ ] Error scenarios tested
- [ ] Documentation complete

**Testing Commands:**
```bash
./gradlew integrationTest
./gradlew :backend:movie-service:integrationTest
./gradlew :backend:user-service:integrationTest
```

**Story Points:** 5  
**Estimated Time:** 4-5 hours

---

## Definition of Done

For all tasks in this phase:

✅ **Code Quality**
- Clean Code principles
- SOLID principles
- Design patterns documented
- SonarQube quality gate passed

✅ **Testing**
- Unit tests (85%+ coverage)
- Integration tests with TestContainers
- API contract tests
- Performance tests
- Security tests (for User Service)

✅ **Documentation**
- JavaDoc complete
- OpenAPI specs generated
- README updated
- ADRs for major decisions

✅ **Deployment**
- Dockerfile created
- Health checks passing
- Registered with Eureka
- Config pulled from Config Server

---

## Success Metrics

- [ ] All core services running
- [ ] Service-to-service communication working
- [ ] TMDB API integration functional
- [ ] Authentication and authorization working
- [ ] All CRUD operations functional
- [ ] Performance benchmarks met
- [ ] Zero critical bugs

---

**Phase Status:** Pending  
**Dependencies:** Phase 2 (Infrastructure Services)  
**Estimated Duration:** 3 weeks  
**Total Story Points:** 39

