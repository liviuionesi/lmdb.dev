# Filmpire Microservices - Enterprise Software Architecture Document

**Version:** 1.8.0  
**Date:** August 11, 2026 (#160: cloud lifecycle — ADR-018, §11.5 stop-not-destroy, §11.7 lifecycle management; #151: bug closed — resolveApiUrl null sentinel verified live)
**Author:** Liviu Ionesi  
**Purpose:** Portfolio project demonstrating enterprise-grade full-stack development for a movie platform

---

## Executive Summary

This document outlines the complete architecture for Filmpire, a production-ready microservices-based movie platform.

**Core product goal:** clone the TMDB v3 API in Spring so that the existing
**Filmpire React application** (`frontend/filmpire` — merged into this repo
as a monorepo on 2026-07-30, full original commit history preserved; was
previously the separate `~/Desktop/filmpire` project. CRA + Redux Toolkit
Query + MUI + Vosk voice control) can consume this backend as a **drop-in replacement**
for `https://api.themoviedb.org/3` — the React app changes only its base URL.
Requests are served read-through: **Redis cache → MongoDB → real TMDB API
(fallback)**; anything fetched from the real TMDB is saved to MongoDB and
returned to the app, so the local database grows organically with use. TMDB's
account/authentication endpoints are proxied straight through to the real
TMDB (login and favorites keep using the user's real TMDB account).

The system demonstrates modern Java 25 and Spring Boot 4.1 capabilities and
emphasizes enterprise best practices, comprehensive testing (TDD), IaC-based
free-tier cloud deployment, and full observability. A dedicated Next.js web
app and React Native mobile apps were considered and **descoped** (v1.2.0) —
the existing Filmpire React app is the only frontend.

---

## Table of Contents

- [1. Technology Stack](#1-technology-stack)
- [2. System Architecture](#2-system-architecture)
- [3. Microservices Design](#3-microservices-design)
- [4. Database Strategy](#4-database-strategy)
- [5. API Specifications](#5-api-specifications)
- [6. Security Architecture](#6-security-architecture)
- [7. Development Environment Setup](#7-development-environment-setup)
- [8. Version Management](#8-version-management)
- [9. Enterprise Development Process](#9-enterprise-development-process)
- [10. Testing Strategy](#10-testing-strategy)
- [11. Deployment Architecture](#11-deployment-architecture) — Terraform, Kubernetes, AWS & Azure free tier
- [12. Monitoring & Observability](#12-monitoring--observability) — Prometheus/Grafana, ELK stack
- [13. Success Criteria](#13-success-criteria)
- [Appendix A: Project Structure](#appendix-a-project-structure)
- [Appendix B: Spring Boot 4.1.x + Java 25 Best Practices](#appendix-b-spring-boot-41x--java-25-best-practices)

---

## 1. Technology Stack

### 1.1 Backend (Exact Versions)

| Technology | Version | Installation Method | Purpose |
|------------|---------|---------------------|---------|
| Java | 25 | SDKMAN | Programming language |
| Spring Boot | 4.1.0 | Gradle | Framework (Framework 7, Jackson 3, Jakarta EE 11 — see ADR-009) |
| Gradle | 9.6.1 | Wrapper / SDKMAN | Build tool |
| Spring Cloud | 2025.1.2 | Gradle | Microservices infrastructure |
| Spring AI | 2.0.0 | Gradle | AI/ML integration (implemented in ai-service — see §3.7) |
| PostgreSQL | 17 (pgvector) | Docker/Podman | Relational database (`pgvector/pgvector:pg17` for ai-service embeddings per ADR-012) |
| MongoDB | 8.0 | Docker/Podman | Document database |
| Redis | 7.4-alpine | Docker/Podman | Caching layer |
| gRPC | 1.76.0 | Gradle | Service communication |
| JWT (jjwt) | 0.13.0 | Gradle | Authentication |
| MapStruct | 1.6.3 | Gradle | DTO mapping (available on the classpath; hand-written DTO mapping so far) |
| Lombok | 1.18.46 | Gradle | Boilerplate reduction |
| MinIO | 8.5.7 | Gradle | Object storage client |
| Logstash Logback Encoder | 8.1 | Gradle | JSON structured logging for ELK pipeline (#23) |
| Protobuf Plugin | 0.9.6 | Gradle | gRPC code generation plugin for Gradle (#36) |
| Protoc | 4.29.3 | Gradle | Protocol Buffer compiler (#36) |
| JUnit | 5.11.3 | Gradle | Testing framework |
| Mockito | 5.19.0 | Gradle | Mocking framework |
| TestContainers | 2.0.5 | Gradle | Integration testing (Postgres/Redis stable; MongoDB saw transient flakiness under podman — see ADR-009) |
| WireMock | 3.9.1 | Gradle | Fake-TMDB HTTP stubbing in tests |
| Bucket4j | 8.10.1 | Gradle | Gateway rate limiting (`bucket4j-core`, not deprecated starter — see §11) |
| Springdoc OpenAPI | 3.0.3 | Gradle | API documentation |
| JaCoCo | 0.8.14 | Gradle | Code coverage |
| OpenRewrite | 7.37.0 | Gradle | Standing framework-migration tool (see ADR-009) |
| SonarQube Plugin | 6.2.0.5505 | Gradle | Static code analysis Gradle plugin (#20) |

### 1.2 Frontend — Existing Filmpire React App (consumer, built elsewhere, merged in-repo)

The frontend is the pre-existing Filmpire application, now living at
`frontend/filmpire/` in this repo. It was originally a standalone project
(`~/Desktop/filmpire`, github.com/pehlivanu/filmpire) and was folded into
this repo as a monorepo on 2026-07-30 — its full commit history (44 commits,
authorship intact) was preserved via `git filter-repo` (to relocate every
commit's paths under `frontend/filmpire/`) followed by a
`--allow-unrelated-histories` merge, after first scrubbing a leaked `.env`
file and a hardcoded TMDB API key from its history. It consumes this
backend without frontend logic changes beyond configuration — see
`docs/guides/RUN_WITH_FILMPIRE_APP.md` for the runbook:

| Technology | Version | Notes |
|------------|---------|-------|
| React (CRA) | 17.0.2 | `react-scripts` 5.0.1 |
| Redux Toolkit | 1.6.2 | `@reduxjs/toolkit` 1.6.2 (`react-redux` 7.2.5) — TMDB calls in `src/services/TMDB.js` |
| axios | 1.6.8 | Auth calls in `src/utils/index.js` |
| Material UI | 5.15.18 | `@mui/material` & `@mui/styles` 5.15.18, `@mui/icons-material` 5.0.3 |
| Emotion | 11.4.1 / 11.3.0 | `@emotion/react` 11.4.1, `@emotion/styled` 11.3.0 |
| Vosk Speech-to-Text | Latest | Offline voice control via `ai-service` SpeechToTextService |
| React Router DOM | 5.3.0 | Client-side routing |
| TMDB API contract | v3 | Base URL `https://api.themoviedb.org/3` → becomes this backend's gateway via `REACT_APP_API_URL` |

> A dedicated Next.js web app and React Native mobile apps were part of
> earlier drafts and are **descoped** as of v1.2.0 — they were never
> actually scaffolded in this repo (no `frontend/web-nextjs` or
> `frontend/mobile-react-native` directory ever existed in git history),
> despite some earlier-draft doc sections describing them as if present.

### 1.3 DevOps & Infrastructure

| Technology | Version | Purpose |
|------------|---------|---------|
| Podman | 5.x | Container runtime (Fedora native) |
| Docker Compose | Latest | Multi-container orchestration |
| Minikube | 1.34.x | Local Kubernetes |
| kubectl | 1.31.x | Kubernetes CLI |
| k9s | Latest | Kubernetes TUI |
| GitHub Actions | Latest | CI/CD |
| SonarQube | Community (Plugin 6.2.0.5505) | Static code analysis (#20) |
| ELK Stack | 8.15.3 | Elasticsearch, Logstash, Kibana, Filebeat (#24) |
| Ollama | Latest | Local LLM model runner backing ai-service (#36) |

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│         Filmpire React App (now Vite, frontend/filmpire)    │
│    RTK Query, TMDB v3 contract, Vosk AI voice via ai-service │
│  baseURL: resolved per-request (local/cloud/tunnel — ADR-016)│
└──────────────────────────────┬──────────────────────────────┘
                               │  TMDB v3-shaped requests
                               ▼
          ┌──────────────────────────────────┐
          │   API Gateway (Spring Cloud)     │
          │         Port: 8080               │
          │  - TMDB v3 facade routing        │
          │  - /authentication/*, /account/* │
          │    → proxied to real TMDB        │
          │  - Rate Limiting, CORS           │
          │  - Load Balancing                │
          └─────────────┬────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│ Eureka Server │ │ Config Server │ │ Microservices │
│   (8761)      │ │    (8888)     │ │   Cluster     │
└───────────────┘ └───────────────┘ └───────┬───────┘
                                             │
        ┌────────────────┬────────────────┬──┴────┬─────────┐
        ▼                ▼                ▼       ▼         ▼
   ┌────────┐      ┌─────────┐      ┌─────────┐ ┌───────┐ ┌───────┐
   │ Movie  │      │  User   │      │ Actor   │ │  AI   │ │Media  │
   │Service │      │ Service │      │ Service │ │Service│ │Service│
   │ (8081) │      │ (8082)  │      │ (8083)  │ │(8084) │ │(8085) │
   └───┬────┘      └────┬────┘      └────┬────┘ └──┬────┘ └──┬────┘
       │                │                 │         │        │
       ▼                ▼                 ▼         ▼        ▼
   ┌────────┐      ┌──────────┐     ┌──────────┐ ┌───────┐ ┌─────┐
   │MongoDB │      │PostgreSQL│     │PostgreSQL│ │MongoDB│ │MinIO│
   └────────┘      └──────────┘     └──────────┘ └───────┘ └─────┘
```

### 2.2 Communication Patterns

- **Synchronous**: REST APIs (JSON) between clients and services
- **Asynchronous**: gRPC — `ai-service` exposes a gRPC server (port 9084,
  `ai-service.proto`), but no other service currently calls it as a
  client; all real ai-service traffic today goes through its REST API via
  the gateway (§3.7). The gRPC surface is available for a future
  backend-to-backend caller, not load-bearing yet.
- **Event-Driven**: Kafka (ADR-006, local profiles only) — the TMDB facade
  publishes a `tmdb.document.saved` event (key: canonical request key;
  payload: endpoint type, path, timestamp) on every save-through; an
  analytics consumer maintains a most-requested-movies view served at
  `/api/v1/analytics/most-requested`. Publishing is fire-and-forget: an
  unavailable broker must never fail the request path.
- **Caching**: Redis for frequently accessed data

### 2.3 Architecture Decision Records

Significant decisions are recorded in [`adr/`](adr/):

| ADR | Decision |
|-----|----------|
| [001](adr/001-microservices-architecture.md) | Microservices over monolith (conscious over-decomposition for the learning goal) |
| [002](adr/002-database-per-service.md) | Per-service database choices |
| [003](adr/003-tmdb-raw-passthrough-facade.md) | ~~TMDB facade serves raw stored JSON, not re-mapped DTOs~~ — **superseded by ADR-010** |
| [004](adr/004-zero-budget-cloud-strategy.md) | $0 cloud budget: local-first, ephemeral free-tier clusters |
| [005](adr/005-eureka-config-vs-kubernetes-native.md) | Eureka/Config Server in compose profile; K8s-native mechanisms in overlays |
| [006](adr/006-kafka-event-bus.md) | Kafka event bus for save-through events & analytics |
| [007](adr/007-distributed-tracing-zipkin.md) | Distributed tracing now (Micrometer Tracing + Zipkin) |
| [008](adr/008-contract-testing.md) | Contract testing with Spring Cloud Contract |
| [009](adr/009-openrewrite-spring-boot-4-migration.md) | OpenRewrite-driven Spring Boot 3.5 → 4.0 migration (Framework 7, Jackson 3, Cloud 2025.1); a routine follow-up chore then bumped 4.0.7 → 4.1.0 |
| [010](adr/010-tmdb-facade-mapped-persisted-schema.md) | TMDB facade serves TMDB-shaped responses backed by Filmpire's own mapped, persisted data — supersedes ADR-003's raw-passthrough model |
| [011](adr/011-self-healing-read-through-on-schema-drift.md) | Read-through treats a schema-drifted MongoDB document as a cache miss: evict + re-fetch instead of a permanent 500 |
| [012](adr/012-ai-service-postgresql-pgvector.md) | ai-service stores conversations in PostgreSQL + pgvector, not MongoDB — amends ADR-002's AI row, since user-owned data can't be self-healed |
| [013](adr/013-frontend-merged-into-monorepo.md) | Filmpire React frontend merged into this repo at `frontend/filmpire/` with full history preserved — this is now a monorepo |
| [014](adr/014-media-service-s3-mongo-storage.md) | media-service: dual-tier storage — MinIO/S3-compatible object storage for user-uploaded binaries, MongoDB for their metadata; TMDB's own media stays on TMDB's CDN, never proxied |
| [015](adr/015-local-only-deploy-trigger.md) | Deploy/destroy triggered only from a local shell (`./gradlew deploy*`) — the web-triggered `/admin` button and its serverless token proxy were removed outright, not just secured further, once found to have no authentication of its own |
| [016](adr/016-dynamic-backend-resolution.md) | Frontend resolves its backend per-request (local → cloud → published tunnel fallback, health-checked), fronted by an ephemeral Cloudflare tunnel for HTTPS — one Vercel deploy works against any live backend, no redeploy needed |
| [017](adr/017-full-cloud-service-parity.md) | Cloud overlays deploy the full local application service set (incl. Ollama), not a movie-only slice — re-sized nodes once verified live pricing showed the cost difference was negligible for ephemeral demo usage |
| [018](adr/018-cloud-lifecycle-stop-not-destroy.md) | Stop cloud compute (not destroy) between demo sessions — de-allocate the VM to zero the dominant compute charge while PVCs/EBS preserve all database state; `terraform destroy` reserved for long breaks or state rebuilds |

### 2.4 Failure-Mode Matrix

Behavior when a dependency fails (the resilience contract; each row is
enforced by code and, where marked ✓, by an automated test):

| Failure | Behavior | Status |
|---------|----------|--------|
| Redis down | Cache layer skipped; requests fall through to MongoDB/TMDB (slower, correct) | built-in |
| MongoDB down | Facade read-through fails → 502 TMDB-shaped error; native API 5xx | acceptable (single-node dev DB) |
| TMDB unreachable | Facade serves stale MongoDB copy if present ✓; else 502 TMDB-shaped error ✓ | implemented (#31) |
| TMDB 4xx/5xx | Error status + body replayed to client verbatim ✓ | implemented (#31) |
| TMDB rate limit | Bucket4j blocks the calling thread until a token frees (40 req/10 s, single shared bucket) ✓ | implemented (#16) |
| Downstream service down (gateway view) | Resilience4j circuit breaker → fallback response | implemented (#13) |
| Kafka down | Event publish fails silently (logged); request path unaffected | planned (ADR-006) |
| Eureka down | Existing clients use cached registry; K8s profile unaffected (DNS) | built-in / ADR-005 |

---

## 3. Microservices Design

> **Reading note:** the code blocks in this section illustrate each
> service's real *pattern* (persistence strategy, layering, DI style) —
> they're not always a byte-for-byte mirror of current source (class/method
> names drift as the code evolves faster than this doc). Where a claim is
> about **what exists or its status** — a feature, a technology choice, a
> "planned"/"not implemented" note — that's been verified against running
> code and kept accurate; treat those as load-bearing. For exact
> implementation, the linked source file is always the source of truth.

### 3.1 Discovery Service (Eureka Server)

**Port:** 8761  
**Database:** None  
**Dependencies:** Spring Cloud Netflix Eureka Server

**Responsibilities:**
- Service registration and discovery
- Health monitoring
- Load balancing support

**Key Configuration:**
```yaml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

---

### 3.2 Config Service (Spring Cloud Config)

**Port:** 8888  
**Database:** Git repository (configuration files)  
**Dependencies:** Spring Cloud Config Server

**Responsibilities:**
- Centralized configuration management
- Environment-specific configurations
- Dynamic configuration refresh

**Configuration Repository Structure:**
```
config-repo/
├── application.yml (shared config)
├── application-dev.yml
├── application-prod.yml
├── movie-service.yml
├── user-service.yml
└── ...
```

---

### 3.3 API Gateway (Spring Cloud Gateway)

**Port:** 8080  
**Database:** Redis (for rate limiting)  
**Dependencies:** Spring Cloud Gateway, Spring Security

**Responsibilities:**
- Single entry point for all clients
- Request routing to microservices
- Authentication/authorization
- Rate limiting
- CORS configuration
- Request/response transformation
- Circuit breaker pattern

**Route Configuration Example:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: movie-service
          uri: lb://MOVIE-SERVICE
          predicates:
            - Path=/api/v1/movies/**
          filters:
            - RewritePath=/api/v1/movies/(?<segment>.*), /${segment}
            - name: RequestRateLimiter
```

---

### 3.4 Movie Service

**Port:** 8081  
**Database:** MongoDB  
**Architecture:** Domain-Driven Design (DDD)

**Why MongoDB?**
- Complex nested structures (cast arrays, crew arrays, videos, genres)
- Flexible schema for different movie types
- High read performance for movie catalogs
- Easy to handle embedded documents

**Domain Model (Java 25 Record):**
```java
@Document(collection = "movies")
@Builder
@Slf4j
public class Movie {
    @Id 
    private String id;
    private Long tmdbId;
    private String title;
    private String originalTitle;
    private String overview;
    private LocalDate releaseDate;
    private Integer runtime;
    private Double voteAverage;
    private Integer voteCount;
    private String posterPath;
    private String backdropPath;
    private List<Genre> genres;
    private List<CastMember> cast;
    private List<CrewMember> crew;
    private List<Video> videos;
    private List<ProductionCompany> productionCompanies;
    private List<String> spokenLanguages;
    private MovieStatus status;
}
```

**TMDB v3 Facade Endpoints Replicated (drop-in frontend compatibility — see §5.1):**
- `GET /genre/movie/list` - Get movie genres
- `GET /movie/popular` - Popular movies
- `GET /movie/top_rated` - Top rated movies
- `GET /movie/upcoming` - Upcoming movies
- `GET /movie/now_playing` - Now playing movies
- `GET /movie/{id}?append_to_response=videos,credits` - Movie details with append parameters
- `GET /movie/{id}/recommendations` - Recommended movies
- `GET /movie/{id}/similar` - Similar movies
- `GET /discover/movie?with_genres={id}` - Discover movies by genre or cast
- `GET /search/movie?query={query}` - Search movies

**Native Internal Microservice Endpoints (`/api/v1/movies/...`):**
- `GET /api/v1/movies/{id}` - Get movie DTO
- `GET /api/v1/movies/search?query={query}` - Native search
- `GET /api/v1/genres` - Native genres DTO

**Service Layer (Constructor Injection - NO Field Injection):**
```java
@Service
@Slf4j
public class MovieService {
    
    private final MovieRepository movieRepository;
    private final TmdbClient tmdbClient;
    private final CacheManager cacheManager;
    
    // Constructor injection - Spring Boot best practice (3.x and 4.x alike)
    public MovieService(
            MovieRepository movieRepository, 
            TmdbClient tmdbClient,
            CacheManager cacheManager) {
        this.movieRepository = movieRepository;
        this.tmdbClient = tmdbClient;
        this.cacheManager = cacheManager;
    }
    
    /**
     * Retrieves movies by category with caching and fallback to TMDB.
     * 
     * @param category Movie category (popular, top_rated, upcoming)
     * @param page Page number (0-indexed)
     * @return Paginated list of movies
     * @throws MovieServiceException if retrieval fails
     */
    public Page<MovieDTO> getMoviesByCategory(
            MovieCategory category, 
            int page
    ) {
        log.debug("Fetching {} movies, page {}", category, page);
        
        // Check cache first
        String cacheKey = "movies:" + category + ":" + page;
        Page<MovieDTO> cachedMovies = cacheManager.get(cacheKey);
        if (cachedMovies != null) {
            log.debug("Cache hit for {}", cacheKey);
            return cachedMovies;
        }
        
        // Fetch from database
        Page<Movie> movies = movieRepository.findByCategory(
            category, 
            PageRequest.of(page, 20)
        );
        
        // Fallback to TMDB if not found
        if (movies.isEmpty()) {
            log.info("No movies found in DB, fetching from TMDB");
            movies = tmdbClient.fetchMoviesByCategory(category, page);
            movieRepository.saveAll(movies.getContent());
        }
        
        Page<MovieDTO> result = movies.map(MovieMapper::toDTO);
        cacheManager.put(cacheKey, result, Duration.ofHours(1));
        
        return result;
    }
}
```

**Test Example (JUnit 5 Jupiter + Mockito):**
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("MovieService Unit Tests")
class MovieServiceTest {
    
    @Mock
    private MovieRepository movieRepository;
    
    @Mock
    private TmdbClient tmdbClient;
    
    @Mock
    private CacheManager cacheManager;
    
    @InjectMocks
    private MovieService movieService;
    
    // NOTE: Using JUnit 5 (Jupiter) exclusively - JUnit 4 is FORBIDDEN
    
    @Test
    @DisplayName("Should return cached movies when cache hit")
    void shouldReturnCachedMovies() {
        // Given
        MovieCategory category = MovieCategory.POPULAR;
        int page = 0;
        Page<MovieDTO> cachedMovies = createMockMoviePage();
        
        when(cacheManager.get(anyString())).thenReturn(cachedMovies);
        
        // When
        Page<MovieDTO> result = movieService.getMoviesByCategory(category, page);
        
        // Then
        assertThat(result).isEqualTo(cachedMovies);
        verify(movieRepository, never()).findByCategory(any(), any());
        verify(tmdbClient, never()).fetchMoviesByCategory(any(), anyInt());
    }
    
    @Test
    @DisplayName("Should fetch from TMDB when database is empty")
    void shouldFetchFromTmdbWhenDatabaseEmpty() {
        // Given
        MovieCategory category = MovieCategory.POPULAR;
        int page = 0;
        Page<Movie> tmdbMovies = createMockTmdbMovies();
        
        when(cacheManager.get(anyString())).thenReturn(null);
        when(movieRepository.findByCategory(any(), any()))
            .thenReturn(Page.empty());
        when(tmdbClient.fetchMoviesByCategory(category, page))
            .thenReturn(tmdbMovies);
        
        // When
        Page<MovieDTO> result = movieService.getMoviesByCategory(category, page);
        
        // Then
        assertThat(result).isNotEmpty();
        verify(movieRepository).saveAll(anyList());
        verify(cacheManager).put(anyString(), any(), any());
    }
}
```

---

### 3.5 User Service

**Port:** 8082  
**Database:** PostgreSQL  
**Architecture:** Layered Architecture with Security

**Why PostgreSQL?**
- ACID compliance for user accounts and transactions
- Strong relational integrity for user-movie relationships
- Mature authentication/session management
- Complex queries for user analytics

**Domain Model:**
```java
@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Enumerated(EnumType.STRING)
    private UserRole role;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<Favorite> favorites = new HashSet<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<Watchlist> watchlist = new HashSet<>();
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "last_login")
    private Instant lastLogin;
    
    private boolean enabled;
    private boolean accountNonLocked;
}

@Entity
@Table(name = "favorites")
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "movie_id")
    private String movieId;
    
    @Column(name = "added_at")
    private Instant addedAt;
}
```

**API Endpoints:**
- `POST /api/v1/auth/register` - User registration
- `POST /api/v1/auth/login` - User login (JWT)
- `POST /api/v1/auth/refresh` - Refresh token
- `POST /api/v1/auth/logout` - Logout
- `GET /api/v1/users/profile` - Get user profile
- `PUT /api/v1/users/profile` - Update profile
- `GET /api/v1/users/favorites` - Get favorite movies
- `POST /api/v1/users/favorites/{movieId}` - Add to favorites
- `DELETE /api/v1/users/favorites/{movieId}` - Remove from favorites
- `GET /api/v1/users/watchlist` - Get watchlist
- `POST /api/v1/users/watchlist/{movieId}` - Add to watchlist

**Security Configuration:**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter(), 
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

---

### 3.6 Actor Service

**Port:** 8083  
**Database:** PostgreSQL  
**Architecture:** Repository Pattern

**Why PostgreSQL?**
- Structured, strongly-typed actor profiles
- Queryable/indexable attributes (name, popularity, department)
- Referential integrity for the actor's owned sub-collections
- Flyway-managed schema evolution

**Domain Model (as implemented — `Actor`, plus two element collections):**
```java
@Entity
@Table(name = "actors")
public class Actor {

    // TMDB's person id IS the primary key — no surrogate. The whole catalog
    // is keyed by TMDB ids, so a generated UUID would add a lookup for nothing.
    @Id
    @Column(name = "tmdb_id")
    private Long tmdbId;

    private String name;
    private String biography;          // TEXT — TMDB serves long ones
    private LocalDate birthDate;
    private String birthPlace;
    private String profilePath;
    private Double popularity;
    private String knownForDepartment;
    private Integer gender;            // TMDB code: 0/1/2/3
    private String imdbId;
    private String homepage;
    private Boolean adult;
    private LocalDateTime syncedAt;    // last refresh from TMDB

    // EAGER on both: the facade reads them outside the service's transaction
    // boundary, where LAZY would throw LazyInitializationException.
    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> alsoKnownAs;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<ActorProfileImage> profileImages;  // CDN refs only, never bytes
}
```

> **DELIBERATE DEVIATION — no actor↔movie join table.** Earlier drafts of this
> section specced a `@ManyToMany` to movies plus a `MovieCast` entity. Neither
> exists, and neither should: movies live in **movie-service's** database
> (database-per-service, ADR-002), so a join table here would duplicate
> foreign data with no owner and no way to keep it consistent. Filmography is
> served from TMDB's `person/{id}/movie_credits` on every request instead, and
> the credits reference movie ids that movie-service resolves. Recorded in the
> entity's Javadoc as well.

**API Endpoints (native):**
- `GET /api/v1/actors/{id}` - Actor details (HATEOAS `_links` to movies/images)
- `GET /api/v1/actors/{id}/movies?page=&size=` - Paged filmography
- `GET /api/v1/actors/{id}/images` - Profile images
- `GET /api/v1/actors/popular?page=` - Popular actors
- `GET /api/v1/actors/search?query=&page=` - Search actors

**TMDB-shaped facade endpoints** (same persisted data, TMDB's snake_case wire
format): `/person/{id}`, `/person/{id}/movie_credits`, `/person/{id}/images`,
`/person/popular`, `/search/person` — see §5.1.

---

### 3.7 AI Service (Advanced)

**Status: implemented and live-verified (#36, #68, #151)** — all four
features below are real, tested against the running service on Azure and
AWS, not aspirational. Two deliberate deviations from the original spec:
**Spring AI 2.0.0** (not the 1.0.0-SNAPSHOT this doc originally named —
2.x is what tracks Spring Boot 4.x), and **Ollama only, no OpenAI starter on
the classpath at all** (not "OpenAI/Ollama" — ADR-004's $0 budget rules out
a paid provider outright, so there's nothing to switch between). Voice
recognition uses **Vosk** (offline, local, no API key — see below), not
Whisper, which needs a paid OpenAI key ADR-004 rules out. Semantic search is
nearest-neighbour search over `user_taste_profiles` embeddings specifically
(no separate per-movie embedding store exists), see
`backend/ai-service/README.md` for the exact API surface.

**Port:** 8084 (REST), 9084 (gRPC)  
**Database:** PostgreSQL + pgvector (`filmpire_ai`) — **ADR-012**  
**Protocols:** REST + gRPC  
**Dependencies:** Spring AI, Ollama (local, $0 — see ADR-004)

**Why PostgreSQL + pgvector, not MongoDB (ADR-012 amends ADR-002):**
- **Conversation history is user-owned and not re-derivable.** Unlike the movie
  and actor catalogs, it cannot be re-fetched from TMDB if a document stops
  matching the model. ADR-011's self-healing — discard the drifted record and
  re-fetch — would therefore *destroy real user data* rather than repair it.
- On MongoDB this service would be the only one with **neither** protection:
  no Flyway/`ddl-auto: validate` gate to catch drift at startup, and no safe
  recovery once it happens. PostgreSQL gives it the same guarantees
  user-service already has for accounts and favorites.
- `pgvector` stores embeddings in a `vector` column with an ANN index —
  sufficient for this project's scale, and it avoids running a separate vector
  database on 1–2 GB free-tier nodes (ADR-004).
- Provider-specific message metadata, where the shape genuinely varies, goes in
  a `JSONB` column: structured where structure exists, flexible where it doesn't.

> Requires a Postgres image with pgvector available (`pgvector/pgvector:pg17`
> rather than stock `postgres:17-alpine`) — a real change to `docker-compose.yml`
> and the K8s overlays, and the main implementation cost of ADR-012.

**Features (all live, REST via the gateway at `/api/v1/ai/**`):**
1. **Speech-to-text** (`POST /speech-to-text`) — offline, Vosk, no cloud API
2. **Movie recommendations** (`POST /recommendations`) — Ollama, grounded in movie-service's real catalog, never invents titles
3. **Chat assistant** (`POST /chat`) — Ollama, persisted conversation history
4. **Semantic search** (`GET /search/semantic`) — pgvector ANN query over taste-profile embeddings

**Domain Model (JPA entities on PostgreSQL — ADR-012):**

Conversations are relational and Flyway-managed, exactly like user-service's
accounts and favorites, because they are user-owned data that cannot be
regenerated. Records are still used for DTOs and events; the *persisted* model
is entity classes, since JPA needs mutable managed instances.

```java
@Entity
@Table(name = "conversations")
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owning user (user-service's id). No FK: ADR-002 forbids cross-service joins. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private ConversationType type;

    /** Ordered message thread; cascaded so a conversation is one aggregate. */
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timestamp ASC")
    private List<Message> messages = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;
}

@Entity
@Table(name = "messages")
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    private String role;              // user, assistant, system

    @Column(columnDefinition = "text")
    private String content;

    private Instant timestamp;

    /** Provider-specific fields whose shape genuinely varies — structured
     *  where structure exists, flexible where it doesn't (ADR-012). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}

@Entity
@Table(name = "user_taste_profiles")
public class UserTasteProfile {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** pgvector column; dimension must match the embedding model in use. */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(columnDefinition = "vector(768)")
    private float[] embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Double> featureWeights;

    private Instant lastUpdated;
}
```

Semantic search is an ANN query against the `vector` column, e.g.
`ORDER BY embedding <=> :query LIMIT :k` with an HNSW or IVFFlat index —
no separate vector database (ADR-004, ADR-012).

**gRPC: exposed, not yet consumed.** `AiGrpcService` genuinely runs (port
9084, `ai-service.proto` defines `GetRecommendations`/`TranscribeVoice`/
`ChatWithAssistant`) — but no other service in this codebase calls it as a
client today. All real traffic to ai-service, from the frontend and in every
live test, goes through the REST API via the gateway. The gRPC surface is
available for a future backend-to-backend caller (e.g. if movie-service ever
wanted recommendations server-side) but isn't load-bearing yet — don't
describe it as the primary integration path.

**Real implementation shape** (Spring AI 2.0's fluent `ChatClient` API, not
the 1.0 `chatClient.call(new Prompt(...))` style):

```java
// RecommendationService — grounds every recommendation in movie-service's
// real catalog (via MovieCatalogClient) rather than letting the model
// invent titles; the system prompt explicitly forbids that.
@Service
public class RecommendationService {
  private final ChatClient chatClient;
  private final EmbeddingModel embeddingModel;
  private final MovieCatalogClient movieCatalogClient;
  private final UserTasteProfileRepository tasteProfileRepository;

  @Transactional
  public RecommendationResponseDto recommend(RecommendationRequestDto request) {
    List<CandidateMovie> candidates =
        movieCatalogClient.fetchCandidates(request.countOrDefault() * 3);
    refreshTasteProfile(request); // embeds recentMovies -> UserTasteProfile (pgvector)

    return new RecommendationResponseDto(
        chatClient.prompt()
            .system(SYSTEM_PROMPT) // "pick from CANDIDATES ONLY, never invent"
            .user(buildUserPrompt(request, candidates))
            .call()
            .entity(new ParameterizedTypeReference<List<MovieRecommendationDto>>() {}));
  }
}

// SpeechToTextService — fully offline: Vosk model loaded lazily (first
// request, not startup — a missing/undownloaded model shouldn't block
// ai-service's other features), a fresh Recognizer per request (Vosk's
// Recognizer isn't thread-safe), audio resampled to 16kHz mono PCM16
// regardless of what the browser sent.
@Service
public class SpeechToTextService implements DisposableBean {
  public String transcribe(MultipartFile audioFile) {
    byte[] pcm = toPcm16Mono16kHz(audioFile);
    try (Recognizer recognizer = new Recognizer(getOrLoadModel(), SAMPLE_RATE_HZ)) {
      recognizer.acceptWaveForm(pcm, pcm.length);
      return extractText(recognizer.getFinalResult());
    }
  }
}
```

**Deployment note (found live, #151):** the Vosk model (~40MB, small
English) isn't downloaded by `docker-compose`/Terraform automatically — it's
baked directly into the ai-service Docker image at build time so it's
present identically on every deploy target (local, Azure, AWS), matching
`VOSK_MODEL_PATH`'s default. `SpeechToTextService` loads it lazily, so a
missing model previously meant ai-service *started* fine and only failed
once a real transcription request needed it — a silent gap until it was
actually exercised.

---

### 3.8 Media Service

**Port:** 8085  
**Database:** MongoDB  
**Storage:** MinIO (S3-compatible) or local filesystem — for future
user-uploaded content only. TMDB-sourced media (posters, backdrops,
trailers) is NEVER downloaded or stored as a file: this service persists
only the TMDB CDN reference (`poster_path`/`backdrop_path`/a YouTube video
key) plus metadata, and the client resolves those into `image.tmdb.org` /
YouTube URLs itself, exactly as the native TMDB API does. Deliberate
constraint: the dev machine has limited local disk, and re-hosting TMDB's
media would add no value a CDN doesn't already provide.

**Why MongoDB?**
- Document-oriented metadata storage
- Nested file information (thumbnails, sizes, formats)
- Flexible schema for different media types

**Domain Model (Immutable Records - Java 25):**
```java
// Using Java records for all DTOs - NO mutable classes
@Document(collection = "media")
public record MediaFile(
    @Id String id,
    String entityId,  // movie ID or actor ID
    EntityType entityType,
    MediaType mediaType,
    String originalFilename,
    String storagePath,
    long fileSize,
    String mimeType,
    Map<String, String> thumbnails,  // size -> URL
    MediaMetadata metadata,
    Instant uploadedAt,
    String uploadedBy
) {
    // Validation in compact constructor
    public MediaFile {
        if (fileSize < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
    }
}

public record MediaMetadata(
    Integer width,
    Integer height,
    Integer duration,  // for videos
    String codec,
    Long bitrate
) {}
```

**API Endpoints:**
- `POST /api/v1/media/upload` - Upload media file
- `GET /api/v1/media/{id}` - Get media file
- `DELETE /api/v1/media/{id}` - Delete media file
- `GET /api/v1/media/entity/{entityId}` - Get all media for entity

---

## 4. Database Strategy

### 4.1 Database Assignment Rationale

The organizing principle (ADR-002, sharpened by ADR-011 and ADR-012):
**user-owned data lives in PostgreSQL under Flyway; re-derivable catalog data
lives in MongoDB, where self-healing on schema drift is safe.** Anything that
cannot be reconstructed from TMDB or recomputed must not sit on a store with
neither schema validation nor a recovery path.

| Service | Database | Re-derivable? | Reason |
|---------|----------|---------------|--------|
| Movie | MongoDB | Yes (TMDB) | Complex nested objects (cast, crew, videos), irregular shape; drift handled by ADR-011 self-healing |
| User | PostgreSQL | No | ACID compliance, relational integrity, authentication |
| Actor | PostgreSQL | Yes (TMDB) | Strong relationships, structured data, complex queries |
| AI | PostgreSQL + pgvector | **No** (conversations) | **ADR-012**, superseding ADR-002's MongoDB assignment: conversation history is user-generated and unrecoverable, so it needs Flyway + `validate` like any other user data; pgvector holds the embeddings |
| Media | MongoDB (+ MinIO) | Yes (TMDB CDN refs) | Document-oriented metadata; MinIO reserved for hypothetical user uploads, never TMDB bytes (§3.8) |

### 4.2 Data Migration Strategy

**Initial TMDB Import (NO @RequiredArgsConstructor pattern):**
```java
@Component
@Slf4j
public class TmdbDataImporter {
    
    private final TmdbClient tmdbClient;
    private final MovieRepository movieRepository;
    private final ActorRepository actorRepository;
    private final ReentrantLock importLock = new ReentrantLock();  // NO synchronized blocks
    
    // Explicit constructor - clearer than Lombok for critical components
    public TmdbDataImporter(
            TmdbClient tmdbClient,
            MovieRepository movieRepository,
            ActorRepository actorRepository) {
        this.tmdbClient = tmdbClient;
        this.movieRepository = movieRepository;
        this.actorRepository = actorRepository;
    }
    
    @Scheduled(cron = "0 0 2 * * ?")  // Daily at 2 AM
    public void importPopularMovies() {
        // Use ReentrantLock instead of synchronized to avoid pinning Virtual Threads
        if (!importLock.tryLock()) {
            log.warn("Import already in progress, skipping");
            return;
        }
        
        try {
            log.info("Starting TMDB import");
            
            int totalPages = 500;  // Import top 10,000 movies
            
            for (int page = 1; page <= totalPages; page++) {
                try {
                    List<Movie> movies = tmdbClient.fetchPopularMovies(page);
                    movieRepository.saveAll(movies);
                    log.debug("Imported page {} of {}", page, totalPages);
                    Thread.sleep(250);  // Rate limiting
                } catch (Exception e) {
                    log.error("Error importing page {}", page, e);
                }
            }
            
            log.info("TMDB import completed");
        } finally {
            importLock.unlock();
        }
    }
}
```

### 4.3 Caching Strategy

**Redis Cache Configuration (Constructor Injection):**
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    private final RedisConnectionFactory factory;
    
    // Constructor injection - NO field injection
    public CacheConfig(RedisConnectionFactory factory) {
        this.factory = factory;
    }
    
    @Bean
    public CacheManager cacheManager() {
        RedisCacheConfiguration config = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer())
            );
        
        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
            "movies", config.entryTtl(Duration.ofHours(6)),
            "actors", config.entryTtl(Duration.ofHours(12)),
            "genres", config.entryTtl(Duration.ofDays(1))
        );
        
        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
```

---

## 5. API Specifications

### 5.1 Primary API: TMDB v3-Compatible Facade

**This is the product — but as of ADR-010, it is not a proxy.** The gateway
exposes the exact TMDB v3 API surface — same paths, same query parameters,
same JSON response shapes (`{page, results, total_pages, total_results}` for
lists, TMDB's exact field names everywhere) — so the Filmpire React app
works by changing only its base URL and its auth flow (see endpoints 10–14
below). What sits behind that surface is Filmpire's own persisted, typed,
queryable catalog (`Movie`, `Actor`, …), fetched from TMDB once per resource
and mapped/save-through rather than cached as opaque bytes (ADR-010,
superseding ADR-003). The `api_key` query parameter sent by the app is
accepted and ignored; the real TMDB key lives server-side only, used to
populate that catalog.

**Endpoints required by the React app (`src/services/TMDB.js`,
`src/utils/index.js`) — the facade MUST implement all of these:**

| # | TMDB v3 Endpoint | Used by (React app) | Backing service | Strategy |
|---|------------------|---------------------|-----------------|----------|
| 1 | `GET /genre/movie/list` | Sidebar genres | Movie | live (small, static taxonomy) + Redis cache |
| 2 | `GET /movie/{category}?page=` (popular, top_rated, upcoming, now_playing) | Category browsing | Movie | live ranking, results upserted |
| 3 | `GET /discover/movie?with_genres={id}&page=` | Genre browsing | Movie | live ranking, results upserted |
| 4 | `GET /search/movie?query=&page=` | Search | Movie | live ranking, results upserted |
| 5 | `GET /movie/{id}?append_to_response=videos,credits` | Movie details page | Movie | read-through/save-through (MongoDB) |
| 6 | `GET /movie/{id}/recommendations` | Details page | Movie | live ranking, results upserted |
| 7 | `GET /movie/{id}/similar` | Details page | Movie | live ranking, results upserted |
| 8 | `GET /person/{id}` | Actor page | Actor | read-through/save-through (PostgreSQL) |
| 9 | `GET /discover/movie?with_cast={id}&page=` | Actor filmography | Actor (via Movie) | live ranking, results upserted |

**Additional TMDB person endpoints implemented beyond the React app's current
needs** (issue #18's acceptance criteria call for full person coverage, and
they cost nothing extra given the typed client is already there):

| TMDB v3 Endpoint | Backing service | Strategy |
|------------------|-----------------|----------|
| `GET /person/{id}/movie_credits` | Actor | live (the movies belong to movie-service, ADR-002 — nothing of actor-service's to persist) |
| `GET /person/{id}/images` | Actor | read-through/save-through (PostgreSQL) — CDN *references* only, never the image bytes (§3.8) |
| `GET /person/popular?page=` | Actor | live ranking, results upserted |
| `GET /search/person?query=&page=` | Actor | live ranking, results upserted |

**Schema drift is a cache miss, not an error (ADR-011).** Because the facade
now persists a *typed* catalog (ADR-010), a stored document can fall out of
sync with the model when the model changes — and MongoDB, unlike the
Flyway-managed PostgreSQL services, has nothing that catches this at startup.
movie-service therefore treats a document it can no longer deserialize as a
miss: it logs the drift, evicts the document by query (never via a derived
`deleteBy…`, which would load the entity and rethrow), and falls through to
the normal TMDB fetch + save-through. The document is rewritten in the current
shape, so the first request after a model change costs one upstream call and
self-heals. Only mapping/conversion failures are absorbed this way —
`DataAccessException` (MongoDB unreachable) still propagates, since masking an
outage as a miss would stampede TMDB. This is safe **only** because the catalog
is re-derivable from TMDB; user-owned data (favorites, watchlists, accounts)
lives in PostgreSQL under Flyway and must never adopt this pattern.
| 10 | Login | Gateway → user-service | **Filmpire JWT, not TMDB session proxy — implemented, see below** |
| 11 | Register | Gateway → user-service | **Filmpire JWT, not TMDB session proxy — implemented, see below** |
| 12 | Profile | Gateway → user-service | **Filmpire JWT, not TMDB session proxy — implemented, see below** |
| 13 | Favorites / watchlist lists | Gateway → user-service | **Filmpire JWT, not TMDB session proxy — implemented, see below** |
| 14 | Favorites / watchlist toggle | Gateway → user-service | **Filmpire JWT, not TMDB session proxy — implemented, see below** |

**Read-through / save-through flow (endpoints 5, 8 — near-immutable detail
resources):**
```
Request → MongoDB/PostgreSQL (by TMDB id)
            └─ miss → real TMDB API (rate-limited, Bucket4j)
                        └─ map into the typed entity → save → return
```
Once a detail record exists it is served locally indefinitely — budget,
runtime, cast, etc. for a released movie don't change. `append_to_response`
sub-resources (videos, credits) are fetched and persisted the same way, the
first time they're requested, then embedded on subsequent responses without
another TMDB round trip.

**Live-ranking flow (endpoints 1–4, 6, 7, 9 — lists/search/discovery):**
```
Request → real TMDB API (rate-limited, Bucket4j) → Redis-cached response
            └─ every movie in the results is upserted into MongoDB
```
TMDB's search/ranking/recommendation algorithms are not reimplemented — these
calls stay live — but every movie any endpoint has ever returned accumulates
in Filmpire's own MongoDB catalog, growing a real, queryable dataset from
traffic (ADR-010). A movie only ever seen via a list carries the list-item
fields until its own detail endpoint is hit at least once, which fills in
the rest (progressive enrichment).

- Images: the app builds `image.tmdb.org` URLs from `poster_path` fields —
  images stay on TMDB's CDN (no proxying; media-service stores/serves only
  those URLs, never the binaries — see §3.8).

**Auth/account (endpoints 10–14) — implemented end-to-end, not a TMDB
proxy:** these were originally speced as a transparent pass-through to
`api.themoviedb.org/3` (TMDB's own request-token/session-id flow). That plan
changed during implementation: they're retargeted at Filmpire's own
user-service JWT auth instead (register/login, favorites, watchlist — see
§3.5), so the account features are backed by Filmpire's own data, not
TMDB's. This was a real, non-trivial deviation from the original spec — it
required editing the React app's auth code, not just its base URL — and is
fully done on both sides: `frontend/filmpire/src/components/NavBar/
LoginDialog.jsx` calls `useLoginMutation`/`useRegisterMutation`
(`src/services/user.js`, RTK Query against `/api/v1/auth/**`), storing the
returned JWT and dispatching it into Redux auth state. No TMDB
session_id/request_token code remains in the app. Live-verified repeatedly
against both cloud deploys (#151): `POST /api/v1/auth/register` and
`/login` return real signed JWTs, validated end-to-end through the gateway.

### 5.1b Secondary API: Native `/api/v1`

The already-implemented native API (`/api/v1/movies/...`, ApiResponse
wrappers, camelCase field names, HATEOAS `_links` on detail resources) reads
and writes the exact same persisted catalog as the facade above — there is
one dataset behind both, not a cache and a source of truth. It remains
available for direct/Swagger consumption and future clients; the TMDB facade
is the contract that matters for the React app.

### 5.2 OpenAPI Documentation

Every service includes complete OpenAPI 3.0 specification:

```java
@OpenAPIDefinition(
    info = @Info(
        title = "Movie Service API",
        version = "1.0.0",
        description = "Movie catalog and search operations",
        contact = @Contact(
            name = "Filmpire Team",
            email = "api@filmpire.com"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8081", description = "Local"),
        @Server(url = "https://api.filmpire.com", description = "Production")
    }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class MovieServiceApplication {}
```

---

## 6. Security Architecture

### 6.1 Authentication Flow

```
Client                  API Gateway           User Service
  |                          |                      |
  |----(1) POST /login------>|                      |
  |                          |----(2) Validate----->|
  |                          |<---(3) JWT Token-----|
  |<---(4) Return JWT--------|                      |
  |                          |                      |
  |----(5) GET /movies------>|                      |
  |     (Authorization:      |                      |
  |      Bearer <JWT>)       |                      |
  |                          |----(6) Validate JWT->|
  |                          |<---(7) User Info-----|
  |                          |----(8) Forward------>| Movie Service
  |<---(9) Return Movies-----|<---(Response)--------|
```

### 6.2 JWT Token Structure

```java
public class JwtTokenProvider {
    
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + JWT_EXPIRATION_MS);
        
        return Jwts.builder()
            .setSubject(user.getId().toString())
            .claim("username", user.getUsername())
            .claim("email", user.getEmail())
            .claim("roles", user.getRoles())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(SignatureAlgorithm.HS512, JWT_SECRET)
            .compact();
    }
}
```

### 6.3 Security Checklist

- [ ] HTTPS enforced in production
- [ ] JWT tokens with 1-hour expiration
- [ ] Refresh tokens with 7-day expiration
- [ ] Password hashing with BCrypt (strength 12)
- [ ] SQL injection prevention (Prepared Statements)
- [ ] XSS protection (Content Security Policy)
- [ ] CSRF protection disabled (stateless JWT)
- [ ] Rate limiting (100 requests/minute per IP)
- [ ] CORS configuration (whitelist origins)
- [ ] Input validation with Bean Validation
- [ ] API versioning (/api/v1/)
- [ ] Sensitive data encryption at rest
- [ ] Secrets management (Spring Cloud Config + Vault)
- [ ] Security headers (X-Frame-Options, X-Content-Type-Options)
- [ ] Dependency vulnerability scanning (Snyk, OWASP)

---

## 7. Development Environment Setup

### 7.1 Prerequisites Installation (Fedora Core 43)

```bash
# Step 1: Install SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk version

# Step 2: Install Java 25 and Gradle
sdk install java 25-open
sdk default java 25-open
java -version

# Gradle is managed via wrapper (gradle-9.6.1)
# No need to install separately
gradle -version

# Step 3: Install NVM and Node.js 22
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.0/install.sh | bash
source ~/.bashrc
nvm install 22
nvm use 22
nvm alias default 22
node -v

# Step 4: Install Container Tools
sudo dnf install podman podman-compose podman-docker
echo "alias docker='podman'" >> ~/.bashrc
source ~/.bashrc

# Step 5: Install Kubernetes Tools
sudo dnf install minikube kubectl k9s

# Step 6: Install Database Clients
sudo dnf install postgresql postgresql-contrib mongodb-mongosh redis

# Step 7: Install Git and Development Tools
sudo dnf install git gh jq httpie
```

### 7.2 Project Initialization

```bash
# Clone repository
git clone https://github.com/yourusername/filmpire-microservices.git
cd filmpire-microservices

# Backend services setup
cd backend

# Each service follows this pattern:
cd movie-service
./gradlew clean build
./gradlew test
./gradlew bootRun

# Frontend (existing Filmpire React app — now frontend/filmpire, merged into
# this repo as a monorepo; see docs/guides/RUN_WITH_FILMPIRE_APP.md)
cd frontend/filmpire
echo "REACT_APP_API_URL=http://localhost:8080" >> .env.local  # point at gateway
npm install
npm start
```

### 7.3 Docker Compose for Local Development

**File: `infrastructure/docker/docker-compose.yml`**

```yaml
version: '3.9'

services:
  # PostgreSQL
  postgres:
    image: postgres:17-alpine
    container_name: filmpire-postgres
    environment:
      POSTGRES_DB: filmpire
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - filmpire-network

  # MongoDB
  mongodb:
    image: mongo:8.0
    container_name: filmpire-mongo
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_ROOT_PASSWORD}
      MONGO_INITDB_DATABASE: filmpire
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db
    networks:
      - filmpire-network

  # Redis
  redis:
    image: redis:7.4-alpine
    container_name: filmpire-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - filmpire-network

  # Eureka Server
  eureka-server:
    build: ../../backend/discovery-service
    container_name: filmpire-eureka
    ports:
      - "8761:8761"
    environment:
      SPRING_PROFILES_ACTIVE: docker
    networks:
      - filmpire-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5

  # Config Server
  config-server:
    build: ../../backend/config-service
    container_name: filmpire-config
    ports:
      - "8888:8888"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
    depends_on:
      - eureka-server
    networks:
      - filmpire-network

  # API Gateway
  api-gateway:
    build: ../../backend/api-gateway
    container_name: filmpire-gateway
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_CLOUD_CONFIG_URI: http://config-server:8888
    depends_on:
      - eureka-server
      - config-server
      - redis
    networks:
      - filmpire-network

  # Movie Service
  movie-service:
    build: ../../backend/movie-service
    container_name: filmpire-movie-service
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATA_MONGODB_URI: mongodb://admin:${MONGO_ROOT_PASSWORD}@mongodb:27017/filmpire?authSource=admin
    depends_on:
      - eureka-server
      - mongodb
    networks:
      - filmpire-network

  # User Service
  user-service:
    build: ../../backend/user-service
    container_name: filmpire-user-service
    ports:
      - "8082:8082"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/filmpire
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
    depends_on:
      - eureka-server
      - postgres
    networks:
      - filmpire-network

  # Actor Service
  actor-service:
    build: ../../backend/actor-service
    container_name: filmpire-actor-service
    ports:
      - "8083:8083"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/filmpire
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
    depends_on:
      - eureka-server
      - postgres
    networks:
      - filmpire-network

volumes:
  postgres_data:
  mongo_data:
  redis_data:

networks:
  filmpire-network:
    driver: bridge
```

**Usage:**
```bash
# Start all services
cd infrastructure/docker
podman-compose up -d

# View logs
podman-compose logs -f

# Stop all services
podman-compose down

# Stop and remove volumes
podman-compose down -v
```

---

## 8. Version Management

### 8.1 Version Lock Files

**Backend: `gradle.properties`** (reproduced from the actual file — this is the
single source of truth; if it drifts from here, trust the file)
```properties
# Java
javaVersion=25
projectVersion=1.0.0-SNAPSHOT

# Spring Boot  
springBootVersion=4.1.0
springDependencyManagementVersion=1.1.7

# Spring Cloud
springCloudVersion=2025.1.2

# Spring AI (2.x tracks Spring Boot 4.x; ai-service is the only consumer — #36)
springAiVersion=2.0.0

# gRPC codegen (ai-service.proto — #36)
protobufPluginVersion=0.9.6
protocVersion=4.29.3

# Dependencies
lombokVersion=1.18.46
mapstructVersion=1.6.3
jjwtVersion=0.13.0
grpcVersion=1.76.0
springdocVersion=3.0.3
minioVersion=8.5.7
logstashEncoderVersion=8.1

# Testing
junitVersion=5.11.3
mockitoVersion=5.19.0
testcontainersVersion=2.0.5
jacocoVersion=0.8.14
wiremockVersion=3.9.1
bucket4jVersion=8.10.1
redisTestcontainersVersion=2.2.2

# Build tooling
openRewriteVersion=7.37.0
rewriteRecipeBomVersion=3.35.0
sonarqubePluginVersion=6.2.0.5505
```

**Frontend: consumer app, own dependency set.** The Filmpire frontend lives at
`frontend/filmpire/` in this repo (Vite + Redux Toolkit Query, see §1.2). Its `package.json`/`package-lock.json`
are its own lock files. 

Automated checking and refactoring tools integrated in `frontend/filmpire/package.json`:
- **Dependency Version Checker (`ncu`)**: `npm run deps:check` (inspects available package updates) and `npm run deps:update` (upgrades package versions).
- **Automated JS/React Refactoring**: `npm run codemod:mui` runs `@mui/codemod` (Material UI codemods) via `npx`. Serves as the frontend equivalent to OpenRewrite. (The former `@codemod/cli` devDependency and its `codemod`/`codemod:react18` scripts were removed in #131 — unused, and pulled in a vulnerable transitive `got`; the standalone `codemod.com` CLI, registered separately in `.mcp.json`, is available for ad hoc AST transforms instead.)

### 8.2 Upgrade Strategy

**Quarterly Review Process:**
1. Monitor security advisories (Dependabot, Snyk)
2. Create upgrade branch
3. Update versions in lock files
4. Run full test suite
5. Manual testing in dev environment
6. Document changes in ADR
7. Staged rollout (dev → staging → prod)

**Upgrade Checklist:**
- [ ] Check Spring Boot release notes
- [ ] Verify Spring Cloud compatibility matrix
- [ ] Update Gradle wrapper: `./gradlew wrapper --gradle-version=X.X.X`
- [ ] Update Java: `sdk install java XX-open`
- [ ] Update Node.js: `nvm install XX`
- [ ] Update dependencies in `gradle.properties`
- [ ] Update dependencies in `package.json`
- [ ] Run tests: `./gradlew test` and `npm test`
- [ ] Run integration tests
- [ ] Update Docker base images
- [ ] Update documentation
- [ ] Create migration guide ADR
- [ ] Tag release: `git tag -a vX.X.X`

**Version Documentation:**
- `VERSIONS.md`: Complete version manifest
- `CHANGELOG.md`: Version history
- `UPGRADE_GUIDE.md`: Step-by-step instructions
- ADRs for major version changes

---

## 9. Enterprise Development Process

### 9.1 Project Management

**GitHub Projects Setup:**
- Kanban board with swim lanes: Backlog, To Do, In Progress, Review, Done
- Issue templates: Bug, Feature, Task, Question
- PR templates with checklist
- Milestones for sprints
- Labels: priority, type, service, status

**Sprint Structure (2-week sprints):**

| Sprint | Duration | Focus | Deliverables |
|--------|----------|-------|--------------|
| 0 | 1 week | Project setup | Repo, CI/CD, docs templates |
| 1-2 | 2 weeks | Infrastructure | Eureka, Config, Gateway, DB |
| 3-5 | 3 weeks | Core services | Movie, User, Actor services |
| 6-7 | 2 weeks | TMDB Facade | TMDB v3 facade + React app integration |
| 8-9 | 2 weeks | Advanced | AI Service, Media Service |
| 10 | 1 week | Testing | E2E (React app), performance, security |
| 11-12 | 2 weeks | Observability & Deploy | Prometheus/ELK, Terraform, K8s cloud |

**Total Timeline:** 12 weeks (~3 months)

### 9.2 Definition of Done (DoD)

Every task must meet these criteria:

✅ **Code Quality**
- Code follows Clean Code principles
- SOLID principles applied
- Design patterns used appropriately
- No code smells (SonarQube)

✅ **Testing**
- Unit tests written (min 85% coverage)
- Integration tests written
- All tests passing
- No flaky tests

✅ **Code Review**
- PR created with description
- 2 reviewers approved
- All comments addressed
- CI/CD pipeline passing

✅ **Documentation**
- Javadoc/JSDoc complete
- README updated
- Wiki updated
- OpenAPI spec updated
- ADR created (if architectural decision)

✅ **Quality Gates**
- SonarQube quality gate passed
- No security vulnerabilities
- Performance benchmarks met
- Accessibility standards met (web/mobile)

✅ **Deployment**
- Deployed to dev environment
- Manual testing completed
- Acceptance criteria verified
- Product owner approval

### 9.3 Development Workflow

> **Actual workflow (see `CLAUDE.md`'s "Where work happens"), not what's
> sketched below:** this is a single-collaborator repo — commits go
> straight to `main`, no `develop` branch, no feature branches, no PRs (the
> sole collaborator can't approve their own PR, so opening one is pure
> friction). **Branch protection is deliberately OFF** — a decision, not a
> gap: protection rules (required reviews, status checks before merge)
> exist to gate PRs into a shared branch, and there are no PRs to gate. The
> daily workflow, branching diagram, and PR-based steps below describe a
> multi-contributor process this project was drafted against early on but
> has never actually run — kept here as the original planning reference,
> not current practice. Revisit if a second collaborator joins.

**Daily Workflow (aspirational — see the note above for what actually happens):**
1. Pull latest changes from main
2. Review assigned GitHub issues
3. Create feature branch: `feature/ISSUE-123-description`
4. TDD: Write test → Implement → Refactor
5. Commit with conventional commits: `feat(movie): add search endpoint`
6. Push and create PR
7. Request code reviews
8. Address feedback
9. Merge after CI passes and approvals
10. Update documentation

**Branching Strategy:**
```
main (production)
  ├── develop (integration)
  │   ├── feature/ISSUE-123-movie-search
  │   ├── feature/ISSUE-124-user-auth
  │   └── feature/ISSUE-125-ai-recommendations
  ├── release/v1.0.0
  └── hotfix/critical-security-patch
```

**Commit Message Format (Conventional Commits):**
```
<type>(<scope>): <subject>

<body>

<footer>
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Example:
```
feat(movie): add genre-based movie discovery

Implement endpoint GET /api/v1/movies/discover?genre={id}
to allow users to filter movies by genre. Includes caching
and fallback to TMDB API.

Closes #123
```

### 9.4 Code Review Guidelines

**Reviewer Checklist:**
- [ ] Code follows style guide
- [ ] Tests are comprehensive
- [ ] No hardcoded values
- [ ] Error handling is robust
- [ ] Logging is appropriate
- [ ] Documentation is complete
- [ ] No security vulnerabilities
- [ ] Performance is acceptable
- [ ] Changes are backward compatible

**Review Etiquette:**
- Be constructive and respectful
- Explain the "why" behind suggestions
- Distinguish between blocking and non-blocking comments
- Approve quickly if LGTM

---

## 10. Testing Strategy

### 10.1 Testing Pyramid

```
           ▲
          / \
         /   \
        / E2E \           10% - Full system tests
       /-------\
      /  Integ. \         30% - Service integration tests
     /-----------\
    /    Unit     \       60% - Component/unit tests
   /---------------\
```

The pyramid above is the backend's own unit/integration/E2E split by test
*count*. It doesn't capture the full picture: **seven distinct test types
run across this codebase**, four of which are cross-cutting concerns
rather than points on that pyramid (they don't have a "% of total tests"
in the same sense — contract/performance/smoke tests each run once per
service or endpoint set, not scaled by class count like unit tests are).
All seven, with where each actually lives:

| # | Type | Tool | Scope | Where |
|---|------|------|-------|-------|
| 1 | Unit | JUnit 5 + Mockito | §10.2 | `<service>/src/test/` |
| 2 | Integration | Testcontainers + `@ServiceConnection` | §10.3 | `<service>/src/test/` |
| 3 | E2E (browser) | Playwright | §10.4 | `e2e/` (repo root) |
| 4 | API smoke | Postman/Newman | §10.5 | `docs/api/Filmpire-API.postman_collection.json`, `.github/workflows/e2e-smoke.yml` |
| 5 | Performance | Gatling (Java DSL) | §10.6 | `movie-service/src/test/.../performance/` |
| 6 | Contract | Spring Cloud Contract (ADR-008) | §10.7 | `<service>/src/contractTest/` — movie, user, actor, ai-service |
| 7 | Frontend unit/component | Vitest + Testing Library | §10.8 | `frontend/filmpire/src/**/*.test.{js,jsx}` |

### 10.2 Unit Testing (60% of tests)

**Tools:** JUnit 5 (Jupiter) ONLY, Mockito 5.19.0, AssertJ

**Critical Requirements:**
- ✅ JUnit 5 (Jupiter) exclusively - **JUnit 4 is FORBIDDEN**
- ✅ `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` in build.gradle
- ✅ Tests run via Cursor IDE Test Runner (CodeLens "Run Test" buttons)
- ✅ NO `@MockBean` - use `@MockitoBean` (Spring Boot 3.4+) for Spring context tests

**Example:**
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("MovieService Unit Tests")
class MovieServiceTest {
    
    @Mock
    private MovieRepository movieRepository;
    
    @Mock
    private TmdbClient tmdbClient;
    
    @InjectMocks
    private MovieService movieService;
    
    @Nested
    @DisplayName("Get Movies By Category")
    class GetMoviesByCategory {
        
        @Test
        @DisplayName("Should return popular movies when category is POPULAR")
        void shouldReturnPopularMovies() {
            // Given
            MovieCategory category = MovieCategory.POPULAR;
            List<Movie> expectedMovies = List.of(
                createMovie("1", "Inception"),
                createMovie("2", "The Dark Knight")
            );
            
            when(movieRepository.findByCategory(eq(category), any()))
                .thenReturn(new PageImpl<>(expectedMovies));
            
            // When
            Page<MovieDTO> result = movieService.getMoviesByCategory(category, 0);
            
            // Then
            assertThat(result).isNotEmpty();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).title()).isEqualTo("Inception");
            
            verify(movieRepository).findByCategory(eq(category), any());
            verifyNoInteractions(tmdbClient);
        }
        
        @Test
        @DisplayName("Should throw exception when repository fails")
        void shouldThrowExceptionWhenRepositoryFails() {
            // Given
            when(movieRepository.findByCategory(any(), any()))
                .thenThrow(new DataAccessException("DB error"));
            
            // When/Then
            assertThatThrownBy(() -> 
                movieService.getMoviesByCategory(MovieCategory.POPULAR, 0))
                .isInstanceOf(MovieServiceException.class)
                .hasMessageContaining("Failed to fetch movies");
        }
    }
}
```

### 10.3 Integration Testing (30% of tests)

**Tools:** Spring Boot Test, TestContainers 2.0.5, RestAssured

**Critical Requirements:**
- ✅ Testcontainers with `@ServiceConnection` (Spring Boot 3.1+)
- ✅ NO H2 database - use Testcontainers with real databases
- ✅ Tests run in Cursor IDE Test Runner

**Example (Modern @ServiceConnection approach):**
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class MovieServiceIntegrationTest {
    
    @Container
    @ServiceConnection  // Spring Boot 3.1+ automatic connection configuration
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:8.0");
    
    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
        .withExposedPorts(6379);
    
    // NO @DynamicPropertySource needed with @ServiceConnection!
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private MovieRepository movieRepository;
    
    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();
    }
    
    @Test
    @DisplayName("Should create and retrieve movie")
    void shouldCreateAndRetrieveMovie() {
        // Given
        MovieDTO newMovie = new MovieDTO(
            null, "Inception", "A mind-bending thriller", 
            LocalDate.of(2010, 7, 16), 8.8
        );
        
        // When - Create
        ResponseEntity<MovieDTO> createResponse = restTemplate
            .postForEntity("/api/v1/movies", newMovie, MovieDTO.class);
        
        // Then - Create
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody().id()).isNotNull();
        
        String movieId = createResponse.getBody().id();
        
        // When - Retrieve
        ResponseEntity<MovieDTO> getResponse = restTemplate
            .getForEntity("/api/v1/movies/" + movieId, MovieDTO.class);
        
        // Then - Retrieve
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().title()).isEqualTo("Inception");
        assertThat(getResponse.getBody().voteAverage()).isEqualTo(8.8);
    }
    
    @Test
    @DisplayName("Should return 404 for non-existent movie")
    void shouldReturn404ForNonExistentMovie() {
        // When
        ResponseEntity<String> response = restTemplate
            .getForEntity("/api/v1/movies/nonexistent-id", String.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

### 10.4 End-to-End Testing (browser, Playwright)

**Location:** `e2e/` (repo root, its own `package.json`/`playwright.config.js`
— not nested under `frontend/filmpire/`). Drives the real React app against
the real local backend stack (`start-infrastructure.sh` + `npm run start`),
not mocks throughout — this is the true acceptance test for the TMDB
facade's drop-in compatibility, and the one layer that would catch a
regression none of the backend's own tests could see (a facade response
shape change that the real app can't actually render, for instance).

**Three spec files, three different verification styles — not all mock the
network:**
- `user-journeys.spec.js` — full real-network flows against the live stack.
- `auth-flow.spec.js` — session-state and redirect behavior, with the
  backend mocked via `page.route()` (deliberately isolating DOM-layer
  behavior from live token exchange, which #33 already verifies at the API
  level — see the file's own header comment).
- `cache-metrics.spec.js` — asserts *backend* caching behavior (a repeated
  view doesn't re-hit TMDB) by reading Spring Boot Actuator metrics
  through Playwright's API request context, not just DOM assertions —
  blurring E2E and integration-level verification in one browser-driven test.

**Real example (`auth-flow.spec.js`, redirect behavior):**
```javascript
test.describe('Authentication & Session Flow (Mocked & Redirect)', () => {
  test('Given an unauthenticated user, when navigating to the profile page, then browser redirects directly to the home page (/)', async ({ page }) => {
    await page.addInitScript(() => localStorage.clear());
    await page.goto('/profile/123');
    await expect(page).toHaveURL(/.*(\/)$/);
  });
});
```

### 10.5 API Smoke Testing (Postman/Newman)

**Location:** `docs/api/Filmpire-API.postman_collection.json`, run via
`newman` in `.github/workflows/e2e-smoke.yml` — the same collection is also
usable directly in Postman for manual/exploratory API work, with an
auth pre-request script that handles the JWT login flow automatically.

**What it answers that the other six test types don't:** "does the live,
fully-composed stack actually work end to end," automatically, without a
human clicking through Postman — brings up the full stack with Docker
Compose, waits for it to be healthy, then runs the whole collection
against it. Triggers are nightly (03:00 UTC) + manual, not per-push —
building every service image and the full stack is heavy and needs a real
`TMDB_API_KEY` repo secret, so this is deliberately not in the per-commit
fast-feedback loop.

### 10.6 Performance Testing

**Tool:** Gatling, **Java DSL** (not Scala — this project is Java-only end
to end, no Scala toolchain).

**Real simulation** (`movie-service/src/test/java/.../performance/
MovieFacadeGatlingSimulation.java`, task #45) — targets the TMDB facade
paths directly (not the native `/api/v1` API), matching the SLOs this
simulation's own results feed into (§12.4):

```java
public class MovieFacadeGatlingSimulation extends Simulation {
  private final String baseUrl =
      System.getProperty("gatling.baseUrl", "http://localhost:8081");

  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(baseUrl).acceptHeader("application/json");

  // Cache-served reads (SLO: P95 < 200ms, §12.4)
  private final ScenarioBuilder cacheServedScenario =
      scenario("Cache-Served Reads")
          .exec(http("GET /movie/popular (Cache Hit)")
              .get("/movie/popular").queryParam("page", "1")
              .check(status().is(200)))
          .pause(Duration.ofMillis(100));
  // ... a second scenario covers TMDB-fallback reads (SLO: P95 < 800ms)
}
```

### 10.7 Contract Testing (ADR-008)

- **Spring Cloud Contract** protects internal service boundaries: producer
  contracts live in each service's own `src/contractTest/` — now
  `movie-service`, `user-service`, `actor-service`, and `ai-service` all
  have one; the build publishes stub jars; api-gateway's contract tests
  consume them via StubRunner instead of hand-written mocks.
- The TMDB-side contract stays fixture-based (recorded real responses) — we
  cannot impose contracts on a third party; that split is deliberate.

### 10.8 Frontend Testing (Vitest + Testing Library)

**Location:** `frontend/filmpire/src/**/*.test.{js,jsx}`, run via
`npm test` (`vitest run`) — component and service-layer unit tests for the
React app itself, not covered by any backend test type above and easy to
forget precisely because it lives in a different toolchain (JS/Vitest vs.
Java/JUnit). Covers RTK Query service definitions (endpoint URL/method/body
construction, exercised by actually dispatching against a mocked `fetch`
rather than introspecting the query function — RTK Query doesn't expose
`query` as a directly callable function), component rendering/interaction
(React Testing Library), and the dynamic backend-resolution logic itself
(`apiUrl.test.js` — the health-check waterfall, ADR-016). Coverage
threshold enforced separately from the backend's JaCoCo gate (§10.9): 80%
across branches/functions/lines/statements (`vite.config.mjs`).

### 10.9 Test Coverage Requirements

**Minimum Coverage:**
- Overall: 85%
- Service layer: 90%
- Controller layer: 80%
- Repository layer: 70%

**Gradle Configuration:**
```kotlin
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.85".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
            excludes = listOf(
                "*.config.*",
                "*.dto.*",
                "*.Application"
            )
        }
    }
}
```

---

## 11. Deployment Architecture

### 11.1 Deployment Strategy Overview

All cloud infrastructure is provisioned with **Terraform** (Infrastructure as
Code) and all services run on **Kubernetes**. Two cloud targets are supported,
both constrained to their **free tiers**:

| Target | Kubernetes Flavor | Free-Tier Basis | Notes |
|--------|-------------------|-----------------|-------|
| **Azure** (primary) | AKS (managed) | AKS control plane is free; $200 credit (30 days) | Managed control plane at $0 — but see the node-size note below, the node itself is not guaranteed free |
| **AWS** (secondary) | k3s (self-managed on EC2) | 750 h/month t2.micro/t3.micro (12 months); 30 GB EBS | EKS control plane is NOT free (~$73/month) — use single-node k3s instead |
| Local | minikube / k3d | n/a | Mirrors cloud manifests exactly |

**HARD CONSTRAINT: the budget is $0.** Every decision below follows from
that. Verification is layered, not assumed:

1. **Local-first.** The primary build/test/demo environment is the developer
   laptop (Fedora, Podman-based Kubernetes via `minikube --driver=podman`).
   The ENTIRE system — all services, Prometheus/Grafana, full ELK — runs and
   is verified locally at $0. Cloud is a demo target only, never the dev
   environment.
2. **Non-billable account types only.** Sign up for the account plans that
   cannot generate an invoice: Azure free account with the default spending
   limit ON (subscription deactivates when the credit is exhausted — it does
   not start billing), and the AWS free-account plan (post-July-2025
   credits-based model, which expires instead of converting to charges).
   Never upgrade either account to pay-as-you-go. Confirm these terms on the
   official free-tier pages at signup time — they change.
3. **Ephemeral clusters.** Cloud environments are created for a demo and
   destroyed after (`terraform apply` ≈ 15 min, demo, `terraform destroy`).
   Nothing runs unattended in the cloud, so nothing accumulates cost and the
   free hours/credits stretch across many months of demos.
4. **Zero-spend tripwires.** The FIRST resources in each Terraform
   composition are a zero-spend budget + email alert (AWS Budgets / Azure
   Cost Management). Any nonzero forecast triggers a same-day email.
5. **No paid managed extras.** Container images live on **ghcr.io (GitHub
   Container Registry — free for public repos)**, NOT ACR (Basic tier is
   ~$5/month) or ECR private. Avoid cloud load balancers where they bill
   hourly (expose via NodePort/hostPort on the node's public IP for demos);
   avoid NAT gateways entirely.

**Free-tier sizing reality (drives all sizing decisions) — three distinct
failure modes found live, none visible in `terraform plan`:**
- **Azure: AKS enforces a hard minimum of 2 vCPU and 4GB memory** for
  whatever SKU runs the system pool (`SystemPoolSkuTooLow` on anything
  smaller). Separately, **a whole VM-size family can be blocked outright**
  for a given subscription/region as an anti-abuse measure, independent of
  whether it clears that minimum (found live: the entire B-series family
  was blocked on this subscription in `eastus`). Confirmed working:
  `Standard_D2ls_v7` (2vCPU/4GB, the movie-only slice) and, once full
  local-parity landed (#151, below), `Standard_D4ls_v7` (4vCPU/8GB).
- **AWS is a harder gate: only Free Tier-*eligible instance types* can be
  launched at all**, on this account — not a spend cap like Azure's, an
  outright `RunInstances` rejection (`InvalidParameterCombination: ... not
  eligible for Free Tier`) for anything not on that list, found live when
  `t3.xlarge` was rejected outright while standing up the full-parity
  set. Check `aws ec2 describe-instance-types --filters
  Name=free-tier-eligible,Values=true` for the actual current list before
  picking a size — it includes more than the classic t2/t3.micro (e.g.
  `m7i-flex.large`, 8GiB/2vCPU, is what full local-parity actually runs on
  today).
- Neither cost model means "free" in the everyday sense — both are
  "verified to not silently overrun," not "$0 no matter what." See §11.5
  for what deliberately keeping something running would actually cost.
- Resizing a **live** node (not a fresh apply) has its own trap: changing
  `vm_size` in-place needs `temporary_name_for_rotation` set on the AKS
  module (added #151) or Terraform force-replaces the *entire cluster*.
  Even with that set, a graceful resize briefly needs old+new node
  capacity simultaneously — found live to exceed a tight regional vCPU
  quota, meaning the only working path was destroy → apply fresh at the
  new size, not an in-place bump. Always `terraform plan` a resize and
  actually read what it proposes before applying.
- Infrastructure MUST be destroyable with a single `terraform destroy` and
  rebuildable with a single `terraform apply` (no manual console changes,
  ever) — this is what makes the ephemeral-cluster model workable, and
  what makes a destroy-then-reapply resize an acceptable answer rather
  than a special case to avoid.

### 11.2 Terraform Layout

```
infrastructure/terraform/
├── modules/
│   ├── network/          # Azure: resource group, VNet, subnet, NSG
│   ├── network-aws/      # AWS: VPC, public subnet, security group
│   ├── cluster-aks/      # AKS cluster (free control plane, 1 node pool)
│   ├── cluster-k3s/      # EC2 (m7i-flex.large) + k3s bootstrap (user_data)
│   ├── budget-guard/     # Azure: zero-spend budget + alert (FIRST resource applied)
│   └── budget-guard-aws/ # AWS: zero-spend Budgets alert (FIRST resource applied)
├── azure/
│   ├── main.tf           # composes budget-guard + network + cluster-aks
│   ├── variables.tf
│   ├── outputs.tf        # kubeconfig
│   └── backend.tf        # remote state: Azure Storage account
├── aws/
│   ├── main.tf           # composes budget-guard + network + cluster-k3s
│   ├── variables.tf
│   ├── outputs.tf
│   └── backend.tf        # remote state: S3 + DynamoDB lock
└── README.md             # bootstrap: state backend creation, credentials
```

**Terraform rules:**
- Remote state per cloud (S3+DynamoDB on AWS, Storage account on Azure);
  never commit state files. On Azure, state access (read/lock) goes
  through Entra ID (`use_azuread_auth = true` on the backend) rather than
  a Storage Account key — both local (`az login`) and CI (OIDC, below)
  authenticate the same way, and there's no key to leak.
- All resources tagged/labeled `project=filmpire`, `managed-by=terraform`
  — except Azure Consumption Budgets (`modules/budget-guard`), which
  structurally don't support a `tags` argument at all.
- Credentials come from environment (`ARM_*`, `AWS_*`) locally, or GitHub
  OIDC federated credentials in CI (an Azure AD App Registration trusts
  GitHub's OIDC issuer for this repo+branch specifically, so CI requests a
  short-lived token per run) — never a stored `ARM_CLIENT_SECRET`, never
  from `.tf` files or committed `tfvars`.
- `terraform plan` runs in CI on every **push to `main`** touching
  `infrastructure/terraform/` — not on PRs: this repo commits straight to
  `main` with no PR workflow (single collaborator, nothing to review a PR
  against — see CLAUDE.md), so `pull_request` would be a trigger that
  never fires. `terraform apply` is never run in CI; it's a manual,
  human-supervised step every time, run locally against the reviewed plan.

**Example — AKS free-tier cluster (modules/cluster-aks):**
```hcl
resource "azurerm_kubernetes_cluster" "filmpire" {
  name                = "filmpire-aks"
  location            = var.location
  resource_group_name = var.resource_group_name
  dns_prefix          = "filmpire"
  sku_tier            = "Free"          # free control plane

  default_node_pool {
    name                    = "default"
    node_count              = 1
    vm_size                 = var.vm_size # do NOT hardcode a "free-tier" SKU here — see below
    node_public_ip_enabled  = true        # reach the gateway without a Standard LB/NAT gateway
  }

  identity { type = "SystemAssigned" }
}
```
`vm_size` is deliberately a variable, not a literal, and deliberately not
shown as a specific SKU name above: a live apply found that AKS enforces a
2 vCPU/4GB minimum for the system pool regardless of what's "free-tier
eligible" in general (rules out B1s/B2ats_v2), and that a brand-new
subscription can have the entire B-series family blocked in a given region
as an anti-abuse measure (rules out B2s too, despite it clearing AKS's
minimum). Neither is visible to `terraform plan` — both are live-API-only
failures. Check `az vm list-skus` for what's actually allowed and its real
specs (naming conventions like "low-memory" or "compute-optimized" aren't
reliable across VM generations — confirmed the hard way when
`Standard_F2as_v7`, expected to be 2 vCPU/4GB by older F-series
convention, turned out to be 2 vCPU/8GB on this generation) before picking
a size, every time, rather than trusting a value written here or anywhere
else in this document. `infrastructure/terraform/modules/cluster-aks/variables.tf`
has the full story and whatever size actually worked most recently.

**Example — AWS k3s node (modules/cluster-k3s):**
```hcl
resource "aws_instance" "k3s_server" {
  ami           = data.aws_ami.al2023.id
  instance_type = var.instance_type     # see below — NOT a literal
  user_data     = <<-EOF
    #!/bin/bash
    curl -sfL https://get.k3s.io | sh -s - \
      --disable traefik --write-kubeconfig-mode 644
  EOF
  root_block_device { volume_size = 30 }
  tags = { Name = "filmpire-k3s", project = "filmpire" }
}
```
`instance_type` is a variable for the same reason `vm_size` is on the Azure
side (§11.1): `t3.micro` OOM-thrashes under this app's real footprint
(found live, #27), and this account only permits launching **Free
Tier-eligible instance types at all** — `t3.xlarge` was rejected outright
mid-deploy when the full local-parity set (#151, actor/user/ai-service +
Postgres + Ollama) needed more headroom than `t3.small` could give.
`m7i-flex.large` (8GiB/2vCPU) is the largest free-tier-eligible type this
account currently allows — check `aws ec2 describe-instance-types
--filters Name=free-tier-eligible,Values=true` before assuming any size,
this list is account/region-specific and changes.

### 11.3 Kubernetes Layout

Kustomize base + overlays (no Helm for own services; Helm only for
third-party charts):

**Eureka and Config Server are deliberately absent from these manifests**
(ADR-005): Kubernetes already provides both capabilities, so in-cluster
services resolve each other via cluster DNS (`lb://movie-service` becomes
`http://movie-service`) and read configuration from ConfigMaps generated from
the same native config files. They remain first-class in the docker-compose /
bare-JVM profile, where nothing else supplies discovery or config.

```
infrastructure/kubernetes/
├── base/                      # cloud-agnostic manifests
│   ├── api-gateway/           # + Ingress
│   ├── movie-service/
│   ├── user-service/
│   ├── actor-service/
│   ├── ai-service/            # REST (8084) + gRPC (9084, exposed not yet consumed) — #36
│   ├── ollama/                # StatefulSet + PVC — local model server for ai-service
│   ├── postgres/              # StatefulSet + PVC (pgvector/pgvector:pg17, ADR-012) —
│   │                          #   user-service (filmpire), actor-service (filmpire_actor),
│   │                          #   ai-service (filmpire_ai)
│   ├── mongodb/               # StatefulSet + PVC — movie-service
│   ├── redis/
│   └── kustomization.yaml
├── overlays/
│   ├── local/                 # everything: all 8 app services + Kafka + Zipkin, generous resources
│   ├── azure/                 # full app-service parity (#151, see below) minus media-service; ghcr.io images
│   └── aws/                   # same as azure, m7i-flex.large sizing
└── monitoring/                # kube-prometheus-stack values + ServiceMonitors — see §12, not wired into any overlay
```

**Cloud overlays reached full local-parity in #151** — a real, deliberate
scope change from the original "core slice" plan (gateway + movie-service
only), made once the node-size constraints in §11.1 were re-examined and
found to have real headroom. `overlays/azure` and `overlays/aws` now both
deploy: `api-gateway`, `movie-service`, `actor-service`, `user-service`,
`ai-service`, MongoDB, Postgres, Redis, and Ollama — everything that runs
locally **except** `media-service` (no Kubernetes manifests exist for it
yet — it would also need an object-storage decision, MinIO locally) and
the observability-only services (`discovery-service`/Eureka,
`config-service`, Kafka, Zipkin — ADR-005 keeps these out of every K8s
overlay deliberately regardless of node size: Kubernetes Services +
cluster DNS already cover what Eureka/Config Server do locally, and
Kafka/Zipkin are internal analytics/tracing, not a user-facing feature).
Each app-service Deployment overrides `EUREKA_CLIENT_ENABLED=false` in its
overlay-level ConfigMap for exactly this reason. Live-verified end-to-end
on both clouds (movies, actors, register/login, voice control's
speech-to-text) — see §11.5.

**Deployment conventions:**
- Every service: readiness probe on `/actuator/health/readiness`, liveness on
  `/actuator/health/liveness`, resource requests/limits mandatory.
- Config via ConfigMaps generated from the config-service's native config
  files; secrets via Kubernetes Secrets (SOPS-encrypted in git, or created
  out-of-band by Terraform — never plaintext in the repo).
- Images built by CI, tagged with the git SHA, pushed to ghcr.io (free for public repos).

### 11.4 CI/CD Pipeline (GitHub Actions) and the Local Deploy Trigger

**Deploys are triggered locally (`./gradlew deployAzure` / `deployAws` /
`deployLocal`), not from CI or the web — a deliberate redesign (#151).**
`/admin` originally had a "Launch/Destroy" button calling a Vercel
serverless function that held a GitHub PAT server-side and dispatched
`deploy.yml`. That button and its proxy were **removed outright**, not
just secured further, once it became clear `/admin` had no
authentication of its own: a public URL with no login that can trigger
real cloud spend is a bad shape regardless of how well the trigger itself
is locked down. Only someone with a shell on the deploying machine (and
real cloud credentials) can provision or destroy anything now.

```
push to main
  ├─► backend-ci.yml        build + test
  │     └─► docker-publish.yml   build images, tag ${GIT_SHA} + latest, push ghcr.io
  └─► terraform-plan.yml    plan only — paths: infrastructure/terraform/**

./gradlew deployAzure | deployAws | deployLocal   (local shell, human-run)
  └─ terraform apply → fetch cluster credentials → kubectl apply -k
       overlays/<target> → wait for rollout → (cloud only) front the
       gateway with a Cloudflare quick tunnel for HTTPS → publish the
       tunnel URL (§11.6)
```

- `terraform-plan.yml` runs on every push to `main` that touches
  `infrastructure/terraform/` — not PRs (see §11.2's Terraform rules for
  why). Auth is GitHub OIDC, no stored secret. It only ever computes and
  displays a plan; it never runs `apply`.
- `docker-publish.yml` triggers on `workflow_run` of Backend CI completing
  with `conclusion: success` on `main` — **a red build/test run never
  produces an image, which found a real gap live (#151)**: Backend CI was
  red for days on a pre-existing Spotless formatting violation unrelated
  to any actual feature work, silently blocking every image rebuild in
  that window — a cloud deploy during that time would have looked
  successful while quietly running stale code. It builds all seven backend
  services (`api-gateway`, `discovery-service`, `config-service`,
  `movie-service`, `user-service`, `actor-service`, `ai-service`) from the
  repo root as build context (every Dockerfile does `COPY backend backend`
  for the multi-module Gradle build) and pushes each to
  `ghcr.io/pehlivanu/filmpire-<service>` tagged both `${GIT_SHA}` and
  `latest`. Check `gh run list --workflow="Backend CI"` before assuming a
  fresh deploy actually has your latest changes.
- `deploy.yml`/`destroy.yml` (`workflow_dispatch`, cloud picker) still
  exist as an alternate CI-driven path — useful if deploys ever need to
  run somewhere other than the operator's own machine — but the Gradle
  tasks above are primary now. Both paths converge on the same
  `kubectl apply -k overlays/<cloud>`. The secrets this path needs
  (`DUCKDNS_TOKEN`, `AWS_K3S_SSH_PRIVATE_KEY`) are configured as of #151 —
  they weren't when this doc originally scoped this workflow as
  out-of-scope/#27.
- Rollback = `kubectl rollout undo` (images are SHA-tagged and kept in the
  registry).

### 11.5 What's Actually Deployed, and What It Costs

Azure AKS is the primary demo cloud as of 2026-08-11 (cluster `filmpire-aks`,
resource group `filmpire-demo`, `eastus`, `Standard_D4ls_v7`). AWS k3s is
provided as an alternative overlay and has been live-verified but is not the
primary target. When either cloud is up, all 9 workloads are deployed:
`api-gateway`, `movie-service`, `actor-service`, `user-service`, `ai-service`,
`postgres`, `mongodb`, `redis`, `ollama`.

**Cost model — ADR-018: stop-not-destroy between demo sessions:**

| State | What's billed | Rate | Daily cost |
|---|---|---|---|
| Running (`az aks start`) | VM + disks + IP | ~$0.21/hr | ~**$5.06/day** |
| Stopped (`az aks stop`) | Disks (~16 GiB) + public IP only | ~$0.01/hr | ~**$0.25/day** |
| Destroyed (`terraform destroy`) | Nothing | $0 | $0 |

`az aks stop` de-allocates the VM while preserving all 5 PVCs (Postgres,
MongoDB ×2, Redis, Ollama). Credits last ~10–20× longer than always-on.
Terraform destroy is reserved for end-of-semester or region migration.

**PVC reclaim policy:** All PVCs use the AKS `default` StorageClass (Azure
Disk, `reclaimPolicy: Delete`). Data survives `az aks stop` / `az aks start`
cycles. Data is **permanently lost** on `terraform destroy` or `kubectl delete
pvc`. This is intentional for a demo environment — no backup strategy is
implemented.

**What leaving one running would actually cost** (Azure Retail Pricing,
`eastus`):

| Node | Spec | Rate | If left running 24/7 for a month |
|---|---|---|---|
| `Standard_D2ls_v7` (movie-only slice, retired) | 2vCPU/4GB | $0.117/hr | ~$85 |
| `Standard_D4ls_v7` (full parity, current) | 4vCPU/8GB | $0.192/hr | ~$139 |

A realistic demo session costs cents. The budget risk is leaving the cluster
running unattended, which `auto-stop-watchdog.sh` and the stop scripts
(§11.7) are designed to prevent.

**Two real production-shaped bugs were found and fixed live** while
standing this up, both worth noting as they're the kind of thing that
would otherwise ship silently:
- MongoDB's original 384Mi memory limit OOM-killed it *during first-boot
  initialization* — before the root user got created — leaving the
  database with authorization enabled and no valid user at all. Recovering
  required a full PVC wipe (a plain restart replays the same corrupted
  state), not just restarting the pod. Fixed by raising the limit to 768Mi
  on both cloud overlays.
- `movie-service`'s Kafka analytics consumer retried a broker that will
  never exist on either cloud overlay (Kafka is local-profile-only,
  ADR-006) forever in the background, wasting CPU/threads and
  contributing to the same OOM pressure. Disabled via
  `SPRING_KAFKA_LISTENER_AUTO_STARTUP=false` on those overlays specifically
  — `overlays/local`, which *does* run Kafka, is unaffected.

### 11.6 Dynamic Backend Resolution: One Frontend Deploy, Any Live Backend

The frontend is deployed to Vercel exactly once and never redeployed just
to change which backend it talks to. Instead,
[`frontend/filmpire/src/utils/apiUrl.js`](../../frontend/filmpire/src/utils/apiUrl.js)
resolves the backend URL **per request**, in priority order:

1. A manual `localStorage` override (devtools-only escape hatch; nothing
   sets this automatically).
2. `VITE_API_URL`, if fixed at build time (intentionally unset in Vercel —
   setting it would disable everything below).
3. `http://localhost:8080`, if the code itself is running on `localhost`.
4. The default cloud target (`filmpire-api.duckdns.org`), **only if it
   passes a live health check** — not assumed reachable just because it's
   configured.
5. Whichever URL is currently published in `infrastructure/tunnel-url.txt`
   — **also only if it passes a health check.**
6. The cloud default anyway, as a last resort, so failure is visible
   rather than silent.

Resolved results are cached 30s per browser tab so this isn't a network
round-trip on every single request, and re-checked automatically once that
expires — so bringing a backend up or down doesn't require any frontend
action, just time for the next health check to notice.

**Why a tunnel, not just the cloud node's IP:** a Kubernetes `Service` of
type `NodePort` on the raw node IP is plain HTTP, with no load balancer
(§11.1's cost rules rule that out). The Vercel frontend is HTTPS; browsers
block "mixed content" (an HTTPS page fetching a plain `http://` resource)
outright, no override available to the user. A Cloudflare quick tunnel
(`cloudflared`, `docker run ... tunnel --url http://<node-ip>:30080`) gives
a real HTTPS endpoint with no certificate to provision and no Cloudflare
account needed — the same mechanism fronts whichever backend is currently
live, local machine or either cloud.

**Why a published pointer file, not a fixed hostname:** a quick tunnel's
hostname is randomly regenerated on every restart — there is no stable
address to hardcode. `infrastructure/scripts/start-tunnel.sh` (local) and
the equivalent manual step for a cloud target both write the current URL
to `infrastructure/tunnel-url.txt` and `git push` it. The frontend reads
that file from `raw.githubusercontent.com` (a plain public GET, no auth,
effectively free) rather than the repo's own API, so a fresh tunnel is
discoverable by *any* visitor within moments of the push — this is,
functionally, git-backed service discovery for a demo environment that
can't justify a real service registry.

**CORS is a separate concern from routing, and was found live to be the
actual root cause of an outage that looked like a routing problem:** the
gateway's `SecurityConfig` allow-list has to include the frontend's real
origin (`https://filmpire-microservices-tan.vercel.app`, plus origin
*patterns* for `*.vercel.app`/`*.trycloudflare.com`/`*.duckdns.org`) or
every request — including the health check in step 4/5 above — gets a
403 invisible to a plain `curl` test that omits the `Origin` header. A
successful `curl` against a backend is not proof a browser can use it;
verifying this properly means sending the real `Origin` header and
checking for `access-control-allow-origin` in the response.

### 11.7 Cloud Lifecycle Management

Lifecycle is managed via scripts in `infrastructure/scripts/`, Gradle tasks,
and GitHub Actions workflows — no manual cloud portal clicks required.

| Tool / Gradle Task | What it does |
|---|---|
| `./gradlew startAzure` (`start-azure.sh`) | Resumes stopped AKS cluster, waits for all 9 workloads Ready, auto-updates DuckDNS (~2m) |
| `./gradlew stopAzure` (`stop-azure.sh`) | Stops AKS compute nodes ($0 compute spend, preserves disk data), waits for full de-allocation |
| `./gradlew startAws` (`start-aws.sh`) | Resumes stopped AWS EC2 k3s instance (~1m), updates DuckDNS |
| `./gradlew stopAws` (`stop-aws.sh`) | Stops AWS EC2 k3s instance ($0 compute spend, preserves EBS volume data) |
| `./gradlew stopAllClouds` (`stop-all-clouds.sh`) | Detects and stops whichever cloud(s) are running (Azure, AWS, Minikube) |
| `stop-all-clouds.sh --dry-run` | Prints what would be stopped without acting |
| `./gradlew autoStopWatchdog` (`auto-stop-watchdog.sh`) | Checks inactivity via `/actuator/activity`; stops compute if idle > 1h |
| `./gradlew statusInfra` (`status-infra.sh`) | Health check across Local, Tunnel, Azure, and AWS endpoints |
| `.github/workflows/deploy.yml` | **Smart Deploy**: Password-gated, auto-wakes stopped clusters or auto-provisions if destroyed, verifies 9 workloads |
| `.github/workflows/cluster-stop.yml` | Remote start/stop from GitHub Actions UI (protected by `DEPLOY_PASSPHRASE`) |
| `.github/workflows/destroy.yml` | Full `terraform destroy` — password-gated, requires confirmation `DESTROY` |

**Typical day lifecycle:**

```bash
# Morning — resume Azure AKS or AWS
./gradlew startAzure          # ~2-3 min, data intact
# OR:
./gradlew startAws            # ~1-2 min, data intact

# Optional: start HTTPS tunnel (bypasses DNS TTL)
./gradlew startTunnel

# Evening — stop to save compute spend
./gradlew stopAzure           # wait for full stop ($0 compute, preserves disks)
# OR: stop everything across all clouds and local
./gradlew stopAllClouds

# Check status anytime:
./gradlew statusInfra
```

See [ADR-018](adr/018-cloud-lifecycle-stop-not-destroy.md) for the
full rationale.

---

## 12. Monitoring & Observability

**Current deployment status (as of #151):** everything below is real,
built, and applies cleanly — but it's a separate, manual apply step
(`kubectl apply -f infrastructure/kubernetes/monitoring/service-monitors/`
after the kube-prometheus-stack Helm release), not wired into any
overlay's `kustomization.yaml`. Neither cloud target currently running
(Azure/AWS, §11) has monitoring or ELK deployed — the free-tier node
budget goes entirely to the application services (§11.5). ELK/Zipkin are
local-profile-only (`overlays/local` includes `zipkin.yaml`; ELK via
`docker-compose.elk.yml`) by design, not an oversight — see the
"Free-tier reality" table in §12.2.

### 12.1 Metrics — Prometheus + Grafana

**Stack:** kube-prometheus-stack Helm chart (Prometheus, Grafana,
Alertmanager, node-exporter, kube-state-metrics).

**Service instrumentation (every Spring Boot service):**
```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

Prometheus discovers services via a `ServiceMonitor` per service (label
selector `monitoring: enabled` on the Kubernetes Service).

**Dashboards (provisioned as ConfigMaps, versioned in git):**
1. JVM per service (heap, GC, threads — critical on 1 GB nodes)
2. HTTP server metrics (rate, errors, duration per endpoint)
3. Gateway dashboard (route latency, rate-limit rejections, circuit-breaker state)
4. Infrastructure (node CPU/memory, pod restarts)

**Alerting rules (Alertmanager):**
- Service down > 2 min (`up == 0`)
- P95 latency > 500 ms for 5 min
- JVM heap > 85% for 5 min
- Pod restart loop (> 3 restarts / 10 min)

**Free-tier sizing:** Prometheus 15-day retention, 10s scrape interval
relaxed to 30s in cloud overlays; Grafana single replica, no persistence in
cloud (dashboards are provisioned from git anyway).

### 12.2 Logging — ELK Stack

**Stack:** Elasticsearch + Logstash + Kibana, with Filebeat as the
per-node log shipper.

```
pods stdout (JSON) ─► Filebeat (DaemonSet) ─► Logstash ─► Elasticsearch ─► Kibana
```

**Service log format** — all services log JSON to stdout via
logstash-logback-encoder:
```groovy
implementation 'net.logstash.logback:logstash-logback-encoder:8.0'
```
```xml
<!-- logback-spring.xml -->
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
  <encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <customFields>{"application":"${APP_NAME}"}</customFields>
  </encoder>
</appender>
```

**Index strategy:** `filmpire-logs-%{+yyyy.MM.dd}`, ILM policy: delete after
7 days (local) / 3 days (cloud) to bound disk usage.

**Free-tier reality:** Elasticsearch needs ≥1 GB heap — it does NOT fit on a
free-tier node alongside the services. Deployment profiles:

| Profile | Logging deployment |
|---------|--------------------|
| Local (minikube/k3d, docker-compose) | Full ELK + Filebeat, single-node ES |
| Cloud free tier | Filebeat only, shipping to a **local** or external ES endpoint; alternatively `kubectl logs` + Kibana omitted |
| Cloud (paid, future) | ECK operator, 3-node ES |

The compose file `infrastructure/docker/docker-compose.elk.yml` runs the full
stack locally so the pipeline (JSON logs → Logstash grok/filters → index
templates → Kibana dashboards) is fully demonstrable without cloud cost.

### 12.3 Distributed Tracing (in scope — ADR-007)

- **Micrometer Tracing (Brave) + Zipkin** across gateway and all services.
- W3C trace-context propagation; trace/span IDs injected into the JSON logs
  so ELK entries correlate with Zipkin traces.
- Zipkin runs as a container in the local profiles; sampling 100% locally,
  configurable (`management.tracing.sampling.probability`) for cloud.
- Demo artifact: one trace showing the same facade request as a cache hit
  (~ms, no TMDB span) vs a cold miss (TMDB client span visible).

### 12.4 Service-Level Objectives

Alerts in §12.1 derive from these SLOs (measured at the gateway, 30-day
window):

| SLO | Target | Measured (Gatling #45) | Error budget consequence |
|-----|--------|------------------------|--------------------------|
| Availability (non-5xx) | 99.0% | 100.0% | budget burn >2×: freeze feature work, fix reliability |
| Latency, cache-served reads | P95 < 200 ms | **P95: 18 ms** (P50: 4 ms) | sustained breach: investigate Redis/Mongo before adding features |
| Latency, TMDB-fallback reads | P95 < 800 ms | **P95: 279 ms** (P50: 252 ms) | breach without TMDB degradation: profile the read-through chain |
| Facade shape fidelity | 100% (byte-identical) | 100% | any regression is a release blocker, caught by fixture tests |

### 12.5 Rollout Order

1. Instrument all services (actuator + Prometheus registry + JSON logging) —
   no infra needed, verifiable with curl.
2. Local: docker-compose.elk.yml + kube-prometheus-stack on minikube.
3. Terraform: Azure AKS first (free control plane), then AWS k3s.
4. Cloud deploy of the full application service set (§11.1) — as of #151,
   monitoring/ELK have not been extended to either cloud target; the
   free-tier node budget goes to the app services themselves.

---

## 13. Success Criteria

### 13.1 Technical Metrics

- [ ] All TMDB endpoints replicated and functional
- [ ] 85%+ test coverage across all services
- [ ] Sub-200ms average API response time
- [ ] Zero critical security vulnerabilities (Snyk/OWASP)
- [ ] SonarQube quality gate: A rating
- [ ] **Filmpire React app runs fully against this backend with only a
      base-URL change** (browse, search, details, actor pages, login,
      favorites via TMDB proxy)
- [ ] Complete API documentation (OpenAPI/Swagger)
- [ ] CI/CD pipeline with <10 minute build time
- [ ] 99% uptime over 30 days

### 13.2 Documentation Completeness

- [ ] Architecture decision records (ADRs) for major decisions
- [ ] README per service with setup instructions
- [ ] API documentation with examples
- [ ] Postman collections for all endpoints
- [ ] Sequence diagrams for critical flows
- [ ] Deployment guide
- [ ] Troubleshooting guide

### 13.3 Portfolio Presentation

**Demonstrated Skills:**
- Enterprise microservices architecture
- Spring Boot 4.1.0 + Spring Cloud 2025.1.2
- Java 25 latest features (records, pattern matching, virtual threads)
- REST + gRPC APIs
- PostgreSQL + MongoDB hybrid strategy
- Spring AI integration
- API-compatible facade design (drop-in TMDB v3 clone)
- Docker + Kubernetes orchestration
- Terraform IaC on AWS & Azure free tiers
- TDD with 85%+ coverage
- CI/CD automation
- Clean Code + SOLID principles
- Comprehensive documentation

---

## Appendix A: Project Structure

```
filmpire-microservices/
├── backend/
│   ├── api-gateway/
│   │   ├── src/
│   │   ├── build.gradle.kts
│   │   ├── Dockerfile
│   │   └── README.md
│   ├── discovery-service/
│   ├── config-service/
│   ├── movie-service/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/filmpire/movie/
│   │   │   │   │   ├── controller/
│   │   │   │   │   ├── service/
│   │   │   │   │   ├── repository/
│   │   │   │   │   ├── model/
│   │   │   │   │   ├── dto/
│   │   │   │   │   ├── mapper/
│   │   │   │   │   ├── config/
│   │   │   │   │   └── exception/
│   │   │   │   └── resources/
│   │   │   │       ├── application.yml
│   │   │   │       └── application-prod.yml
│   │   │   └── test/
│   │   │       ├── java/com/filmpire/movie/
│   │   │       │   ├── service/
│   │   │       │   ├── controller/
│   │   │       │   └── integration/
│   │   │       └── resources/
│   │   ├── build.gradle.kts
│   │   ├── Dockerfile
│   │   └── README.md
│   ├── user-service/
│   ├── actor-service/
│   ├── ai-service/
│   ├── media-service/
│   ├── shared-library/
│   │   ├── src/
│   │   └── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle.properties
├── frontend/
│   └── filmpire/            # Vite app (migrated from CRA, #125-127), merged
│       │                    # in as a monorepo 2026-07-30, full history preserved
│       ├── src/
│       │   ├── components/  # App shell, NavBar (+LoginDialog), Movies,
│       │   │                # MovieInformation, Actors, Profile, Search,
│       │   │                # Sidebar, VoiceControl, Admin (StatusCard)
│       │   ├── services/    # RTK Query: TMDB.js, user.js, media.js
│       │   ├── utils/
│       │   │   └── apiUrl.js  # dynamic backend resolution — ADR-016
│       │   └── features/    # Redux slices (auth, genre/category)
│       ├── public/
│       ├── package.json
│       └── README.md
├── infrastructure/
│   ├── docker/
│   │   ├── docker-compose.yml       # full local stack, all 8 app services
│   │   ├── docker-compose.elk.yml   # local-only ELK stack — §12.2
│   │   └── docker-compose.prod.yml
│   ├── terraform/            # see §11.2 for the real module layout
│   ├── kubernetes/
│   │   ├── base/              # cloud-agnostic manifests, one dir per service
│   │   ├── overlays/          # local/ (everything), azure/, aws/ — §11.3
│   │   └── monitoring/        # kube-prometheus-stack values + ServiceMonitors, §12
│   ├── tunnel-url.txt         # published live-tunnel pointer — ADR-016
│   └── scripts/               # deployAzure/deployAws/deployLocal + destroy*,
│                               # startTunnel/stopTunnel, statusInfra — §11.4
├── docs/
│   ├── architecture/
│   │   ├── ARCHITECTURE.md
│   │   ├── adr/                 # 001-017, see §2.3 for the full index
│   │   └── PORT_MAPPING.md
│   ├── process/                 # Scrum artifacts — DoR/DoD/NFRs, product goal, methodology
│   ├── api/                     # Postman collection
│   └── guides/
│       ├── RUN_WITH_FILMPIRE_APP.md  # pointing the frontend at this backend
│       └── DEPLOYMENT_GUIDE.md       # local/Azure/AWS deploy + FE-binding runbook
├── .github/
│   ├── workflows/
│   │   ├── backend-ci-cd.yml
│   │   ├── frontend-ci-cd.yml
│   │   └── mobile-ci-cd.yml
│   ├── ISSUE_TEMPLATE/
│   └── PULL_REQUEST_TEMPLATE.md
├── .gitignore
├── VERSIONS.md
├── CHANGELOG.md
├── CONTRIBUTING.md
└── README.md
```

---

## Appendix B: Spring Boot 4.1.x + Java 25 Best Practices

### Critical "Antigravity" Rules (MUST FOLLOW)

**❌ FORBIDDEN:**
- `RestTemplate` - use `RestClient` or `@HttpExchange` interfaces
- Field injection (`@Autowired` on fields) - use constructor injection
- Mutable DTOs - use Java `record` for all DTOs, Events, Config Props
- H2 for integration tests - use Testcontainers with `@ServiceConnection`
- `synchronized` blocks - use `ReentrantLock` to avoid pinning Virtual Threads
- `@MockBean` - use `@MockitoBean` (Spring Boot 3.4+)
- JUnit 4 - use JUnit 5 (Jupiter) exclusively

**✅ REQUIRED:**
- Constructor injection (manual or via explicit constructor)
- Java records for immutability
- Testcontainers with `@ServiceConnection`
- `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` in build.gradle
- Tests run via Cursor IDE Test Runner

### Records (Immutable DTOs - Java 25)
```java
// All DTOs MUST be records - NO mutable classes
public record MovieDTO(
    String id,
    String title,
    String overview,
    LocalDate releaseDate,
    Double voteAverage
) {
    // Compact constructor with validation
    public MovieDTO {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
        if (voteAverage != null && (voteAverage < 0 || voteAverage > 10)) {
            throw new IllegalArgumentException("Vote average must be between 0 and 10");
        }
    }
}
```

### Pattern Matching
```java
public String formatResponse(Object response) {
    return switch (response) {
        case MovieDTO movie -> "Movie: " + movie.title();
        case ActorDTO actor -> "Actor: " + actor.name();
        case ErrorResponse error -> "Error: " + error.message();
        case null -> "No response";
        default -> "Unknown response type";
    };
}
```

### Sealed Classes
```java
public sealed interface ApiResponse 
    permits SuccessResponse, ErrorResponse, EmptyResponse {}

public record SuccessResponse<T>(T data) implements ApiResponse {}
public record ErrorResponse(String message, int code) implements ApiResponse {}
public record EmptyResponse() implements ApiResponse {}
```

### Virtual Threads (Java 25 - Project Loom)
```java
@Configuration
public class AsyncConfig {
    
    @Bean
    public Executor taskExecutor() {
        // Virtual threads - lightweight, scalable concurrency
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

@Service
public class MovieService {
    
    private final MovieRepository movieRepository;
    private final ReentrantLock cacheLock = new ReentrantLock();
    
    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }
    
    @Async
    public CompletableFuture<List<Movie>> fetchMoviesAsync() {
        // Runs on virtual thread - extremely lightweight
        // NEVER use synchronized blocks with virtual threads
        return CompletableFuture.completedFuture(
            movieRepository.findAll()
        );
    }
    
    public void updateCache() {
        // Use ReentrantLock instead of synchronized for virtual threads
        cacheLock.lock();
        try {
            // Cache update logic
        } finally {
            cacheLock.unlock();
        }
    }
}
```

### RestClient (NO RestTemplate)
```java
@Configuration
public class RestClientConfig {
    
    @Bean
    public RestClient tmdbRestClient(@Value("${tmdb.base-url}") String baseUrl) {
        // Use RestClient instead of deprecated RestTemplate
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

@Service
public class TmdbClient {
    
    private final RestClient restClient;
    private final String apiKey;
    
    public TmdbClient(RestClient restClient, @Value("${tmdb.api-key}") String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }
    
    public TmdbMovieResponse getMovie(Long id) {
        return restClient.get()
                .uri("/movie/{id}?api_key={key}", id, apiKey)
                .retrieve()
                .body(TmdbMovieResponse.class);
    }
}
```

---

**Document Version:** 1.7.3  
**Last Updated:** August 1, 2026  
**Status:** Living Document — Discovery/Config/Gateway/Movie/Actor/User/AI services implemented and running on Spring Boot 4.1; Media service remaining stub (see §2.3 ADRs and per-service sections for current status)

