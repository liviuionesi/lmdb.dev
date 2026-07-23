# Filmpire Microservices - Enterprise Software Architecture Document

**Version:** 1.4.0  
**Date:** July 22, 2026 (Spring Boot 3.5 → 4.1 upgrade synced throughout — ADR-009 recorded the 4.0 migration decision, gradle.properties has since moved to 4.1.0; ADR-010 TMDB facade pivot to mapped/persisted data recorded)  
**Author:** Liviu Ionesi  
**Purpose:** Portfolio project demonstrating enterprise-grade full-stack development for a movie platform

---

## Executive Summary

This document outlines the complete architecture for Filmpire, a production-ready microservices-based movie platform.

**Core product goal:** clone the TMDB v3 API in Spring so that the existing
**Filmpire React application** (`~/Desktop/filmpire`, CRA + Redux Toolkit
Query + MUI + Alan AI) can consume this backend as a **drop-in replacement**
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

1. [Technology Stack](#technology-stack)
2. [System Architecture](#system-architecture)
3. [Microservices Design](#microservices-design)
4. [Database Strategy](#database-strategy)
5. [API Specifications](#api-specifications)
6. [Security Architecture](#security-architecture)
7. [Development Environment Setup](#development-environment-setup)
8. [Version Management](#version-management)
9. [Enterprise Development Process](#enterprise-development-process)
10. [Testing Strategy](#testing-strategy)
11. [Deployment Architecture](#11-deployment-architecture) — Terraform, Kubernetes, AWS & Azure free tier
12. [Monitoring & Observability](#12-monitoring--observability) — Prometheus/Grafana, ELK stack
13. [Success Criteria](#13-success-criteria)

---

## 1. Technology Stack

### 1.1 Backend (Exact Versions)

| Technology | Version | Installation Method | Purpose |
|------------|---------|---------------------|---------|
| Java | 25 | SDKMAN | Programming language |
| Spring Boot | 4.1.0 | Gradle | Framework (Framework 7, Jackson 3, Jakarta EE 11 — see ADR-009) |
| Gradle | 9.2.0 | SDKMAN | Build tool |
| Spring Cloud | 2025.1.2 | Gradle | Microservices infrastructure |
| Spring AI | 1.0.0-SNAPSHOT | Gradle | AI/ML integration (not yet enabled — see §3.7) |
| PostgreSQL | 17-alpine | Docker/Podman | Relational database |
| MongoDB | 8.0 | Docker/Podman | Document database |
| Redis | 7.4-alpine | Docker/Podman | Caching layer |
| gRPC | 1.76.0 | Gradle | Service communication |
| JWT (jjwt) | 0.13.0 | Gradle | Authentication |
| MapStruct | 1.6.3 | Gradle | DTO mapping (available on the classpath; not yet adopted by any service — hand-written mapping so far) |
| Lombok | 1.18.46 | Gradle | Boilerplate reduction |
| MinIO | 8.5.7 | Gradle | Object storage client |
| JUnit | 5.11.3 | Gradle | Testing framework |
| Mockito | 5.19.0 | Gradle | Mocking framework |
| TestContainers | 2.0.5 | Gradle | Integration testing (Postgres/Redis stable; MongoDB saw transient flakiness under podman — see ADR-009) |
| WireMock | 3.9.1 | Gradle | Fake-TMDB HTTP stubbing in tests |
| Bucket4j | 8.10.1 | Gradle | Gateway rate limiting (`bucket4j-core`, not the deprecated Spring starter — see §11) |
| Springdoc OpenAPI | 3.0.3 | Gradle | API documentation |
| JaCoCo | 0.8.14 | Gradle | Code coverage |
| OpenRewrite | 7.37.0 | Gradle | Standing framework-migration tool (see ADR-009) |

### 1.2 Frontend — Existing Filmpire React App (consumer, not built here)

The frontend is the pre-existing Filmpire application at `~/Desktop/filmpire`
(separate project, not part of this repo). This backend must serve it without
frontend changes beyond configuration:

| Technology | Version | Notes |
|------------|---------|-------|
| React (CRA) | 17.x | `react-scripts` 5 |
| Redux Toolkit Query | 1.6.x | All TMDB calls in `src/services/TMDB.js` |
| axios | 1.6.x | Auth calls in `src/utils/index.js` |
| Material UI | 5.x | |
| Alan AI SDK | 1.8.x | Voice control (calls TMDB via the same services) |
| TMDB API contract | v3 | Base URL `https://api.themoviedb.org/3` → becomes this backend's gateway |

> A dedicated Next.js web app and React Native mobile apps were part of
> earlier drafts and are **descoped** as of v1.2.0. The empty
> `frontend/web-nextjs` and `frontend/mobile-react-native` directories are
> legacy placeholders, out of scope.

### 1.3 DevOps & Infrastructure

| Technology | Version | Purpose |
|------------|---------|---------|
| Podman | 5.x | Container runtime (Fedora native) |
| Docker Compose | Latest | Multi-container orchestration |
| Minikube | 1.34.x | Local Kubernetes |
| kubectl | 1.31.x | Kubernetes CLI |
| k9s | Latest | Kubernetes TUI |
| GitHub Actions | Latest | CI/CD |
| SonarQube | Latest | Code quality |

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              Filmpire React App (existing, CRA)             │
│     RTK Query + axios, TMDB v3 contract, Alan AI voice      │
│     baseURL: http://localhost:8080  (was api.themoviedb.org)│
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
- **Asynchronous**: gRPC for AI Service internal communication
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

**TMDB Endpoints Replicated:**
- `GET /api/v1/movies/popular` - Popular movies
- `GET /api/v1/movies/top-rated` - Top rated movies
- `GET /api/v1/movies/upcoming` - Upcoming movies
- `GET /api/v1/movies/search?query={query}` - Search movies
- `GET /api/v1/movies/{id}` - Get movie details
- `GET /api/v1/movies/discover?genre={id}` - Discover by genre
- `GET /api/v1/genres` - Get all genres

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

**Port:** 8084  
**Database:** MongoDB  
**Protocols:** REST + gRPC  
**Dependencies:** Spring AI, OpenAI/Ollama

**Why MongoDB?**
- Flexible schema for AI conversation history
- Embedding vectors for semantic search
- Unstructured recommendation data
- High write throughput for logs

**Features:**
1. **Voice Recognition** (Whisper API)
2. **Movie Recommendations** (OpenAI/Ollama)
3. **Chat Assistant**
4. **Semantic Search**

**Domain Model (Immutable Java Records - Spring Boot 4.x):**
```java
// All DTOs, Events, and domain objects use Java records for immutability
@Document(collection = "conversations")
public record Conversation(
    @Id String id,
    String userId,
    ConversationType type,
    List<Message> messages,
    Map<String, Object> context,
    Instant createdAt,
    Instant updatedAt
) {
    // Compact constructor for validation
    public Conversation {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }
    }
}

public record Message(
    String role,  // user, assistant, system
    String content,
    Instant timestamp,
    Map<String, Object> metadata
) {}

@Document(collection = "recommendations")
public record RecommendationModel(
    @Id String id,
    String userId,
    List<String> preferredGenres,
    List<String> favoriteMovies,
    Map<String, Double> featureWeights,
    double[] embeddingVector,
    Instant lastUpdated
) {}
```

**gRPC Service Definition (ai-service.proto):**
```protobuf
syntax = "proto3";

package com.filmpire.ai;

service AIService {
  rpc GetRecommendations(RecommendationRequest) returns (RecommendationResponse);
  rpc TranscribeVoice(VoiceRequest) returns (TranscriptionResponse);
  rpc ChatWithAssistant(ChatRequest) returns (ChatResponse);
}

message RecommendationRequest {
  string user_id = 1;
  repeated string recent_movies = 2;
  int32 count = 3;
}

message RecommendationResponse {
  repeated MovieRecommendation recommendations = 1;
}

message MovieRecommendation {
  string movie_id = 1;
  double score = 2;
  string reason = 3;
}
```

**Spring AI Integration (Constructor Injection):**
```java
@Service
@Slf4j
public class AIRecommendationService {
    
    private final ChatClient chatClient;
    private final EmbeddingClient embeddingClient;
    
    // Constructor injection - NO @Autowired on fields
    public AIRecommendationService(ChatClient chatClient, EmbeddingClient embeddingClient) {
        this.chatClient = chatClient;
        this.embeddingClient = embeddingClient;
    }
    
    /**
     * Generates personalized movie recommendations using AI.
     */
    public List<MovieRecommendation> generateRecommendations(
            String userId, 
            List<String> recentMovies
    ) {
        // Build context from user's movie history
        String context = buildUserContext(userId, recentMovies);
        
        // Create prompt
        String prompt = """
            Based on the user's movie watching history:
            %s
            
            Recommend 10 similar movies they might enjoy.
            For each recommendation, provide:
            1. Movie title
            2. Similarity score (0-1)
            3. Brief explanation why they'd like it
            
            Format as JSON.
            """.formatted(context);
        
        // Call AI model
        ChatResponse response = chatClient.call(
            new Prompt(prompt, 
                OpenAiChatOptions.builder()
                    .withModel("gpt-4")
                    .withTemperature(0.7)
                    .build()
            )
        );
        
        return parseRecommendations(response);
    }
    
    /**
     * Transcribes voice input to text using Whisper API.
     */
    public String transcribeVoice(byte[] audioData) {
        // Implementation using Spring AI + Whisper
        return whisperClient.transcribe(audioData);
    }
}
```

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

| Service | Database | Reason |
|---------|----------|--------|
| Movie | MongoDB | Complex nested objects (cast, crew, videos), flexible schema |
| User | PostgreSQL | ACID compliance, relational integrity, authentication |
| Actor | PostgreSQL | Strong relationships, structured data, complex queries |
| AI | MongoDB | Flexible schema for ML data, embedding vectors |
| Media | MongoDB | Document-oriented metadata, nested file info |

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
| 10 | Login | Gateway → user-service | **planned: Filmpire JWT, not TMDB session proxy — see below** |
| 11 | Register | Gateway → user-service | **planned: Filmpire JWT, not TMDB session proxy — see below** |
| 12 | Profile | Gateway → user-service | **planned: Filmpire JWT, not TMDB session proxy — see below** |
| 13 | Favorites / watchlist lists | Gateway → user-service | **planned: Filmpire JWT, not TMDB session proxy — see below** |
| 14 | Favorites / watchlist toggle | Gateway → user-service | **planned: Filmpire JWT, not TMDB session proxy — see below** |

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

**Auth/account (endpoints 10–14) — planned change, not yet implemented:**
these were originally a transparent pass-through to `api.themoviedb.org/3`
(TMDB's own request-token/session-id flow). The product decision is now to
retarget them at Filmpire's own user-service JWT auth (register/login,
favorites, watchlist — already implemented and tested, see §3.5) instead,
so the account features the showcase highlights are backed by Filmpire's own
data, not TMDB's. This requires editing the React app's auth code
(`src/services/TMDB.js`, `src/utils/index.js`, `NavBar`, `Profile`), not
just its base URL — tracked as a follow-up to issue #33/#34.

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

# Gradle is managed via wrapper (gradle-9.2.0)
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

# Frontend (existing Filmpire React app — separate project)
cd ~/Desktop/filmpire
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
      POSTGRES_PASSWORD: admin123
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
      MONGO_INITDB_ROOT_PASSWORD: admin123
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
      SPRING_DATA_MONGODB_URI: mongodb://admin:admin123@mongodb:27017/filmpire?authSource=admin
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
      SPRING_DATASOURCE_PASSWORD: admin123
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
      SPRING_DATASOURCE_PASSWORD: admin123
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

# Spring AI
springAiVersion=1.0.0-SNAPSHOT

# Dependencies
lombokVersion=1.18.46
mapstructVersion=1.6.3
jjwtVersion=0.13.0
grpcVersion=1.76.0
springdocVersion=3.0.3
minioVersion=8.5.7

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
```

**Frontend: consumer app, not built in this repo.** The real Filmpire
frontend lives in the separate `~/Desktop/filmpire` repo (CRA + Redux
Toolkit Query, not the Next.js stack this section originally sketched) —
see §1.2 for its actual dependency versions. There is no frontend
`package.json` to lock here; the descoped `frontend/web-nextjs` and
`frontend/mobile-react-native` placeholders in this repo carry no real
dependencies.

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

**Daily Workflow:**
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

### 10.4 End-to-End Testing (10% of tests)

**Tools:** Playwright, run against the existing Filmpire React app pointed at
the local backend stack (the true acceptance test for the TMDB facade)

**Example (Playwright):**
```typescript
import { test, expect } from '@playwright/test';

test.describe('Movie Discovery Flow', () => {
  test('User can search and view movie details', async ({ page }) => {
    // Given - User is on home page
    await page.goto('http://localhost:3000');
    
    // When - User searches for a movie
    await page.fill('[data-testid="search-input"]', 'Inception');
    await page.click('[data-testid="search-button"]');
    
    // Then - Search results are displayed
    await expect(page.locator('[data-testid="movie-card"]')).toHaveCount(1, { timeout: 5000 });
    await expect(page.locator('text=Inception')).toBeVisible();
    
    // When - User clicks on movie
    await page.click('[data-testid="movie-card"]:first-child');
    
    // Then - Movie details page is shown
    await expect(page).toHaveURL(/\/movie\/\d+/);
    await expect(page.locator('h1')).toContainText('Inception');
    await expect(page.locator('[data-testid="movie-rating"]')).toBeVisible();
    await expect(page.locator('[data-testid="movie-overview"]')).toBeVisible();
  });
  
  test('User can add movie to favorites', async ({ page }) => {
    // Given - User is logged in and on movie details page
    await page.goto('http://localhost:3000/login');
    await page.fill('[name="email"]', 'test@example.com');
    await page.fill('[name="password"]', 'password123');
    await page.click('button[type="submit"]');
    
    await page.goto('http://localhost:3000/movie/123');
    
    // When - User clicks favorite button
    await page.click('[data-testid="favorite-button"]');
    
    // Then - Success message is shown
    await expect(page.locator('text=Added to favorites')).toBeVisible();
    
    // When - User navigates to profile
    await page.click('[data-testid="user-menu"]');
    await page.click('text=My Favorites');
    
    // Then - Movie appears in favorites
    await expect(page.locator('[data-testid="movie-card"]')).toContainText('Inception');
  });
});
```

### 10.5 Performance Testing

**Tools:** Gatling, JMeter

**Example (Gatling):**
```scala
class MovieServiceSimulation extends Simulation {
  
  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
  
  val scn = scenario("Movie Service Load Test")
    .exec(http("Get Popular Movies")
      .get("/api/v1/movies/popular?page=1")
      .check(status.is(200))
      .check(jsonPath("$.content").exists)
    )
    .pause(1)
    .exec(http("Search Movies")
      .get("/api/v1/movies/search?query=inception")
      .check(status.is(200))
    )
    .pause(1)
    .exec(http("Get Movie Details")
      .get("/api/v1/movies/123")
      .check(status.is(200))
      .check(jsonPath("$.title").exists)
    )
  
  setUp(
    scn.inject(
      rampUsers(100) during (30 seconds),
      constantUsersPerSec(50) during (2 minutes)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.max.lt(2000),
     global.successfulRequests.percent.gt(95)
   )
}
```

### 10.6 Contract Testing (ADR-008)

- **Spring Cloud Contract** protects internal service boundaries: producer
  contracts live in movie-service (and later actor-service) for the facade
  endpoints; the build publishes stub jars; api-gateway tests consume them
  via StubRunner instead of hand-written mocks.
- The TMDB-side contract stays fixture-based (recorded real responses) — we
  cannot impose contracts on a third party; that split is deliberate.

### 10.7 Test Coverage Requirements

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
| **Azure** (primary) | AKS (managed) | AKS control plane is free; 750 h/month B1s/B2ats VM (12 months); $200 credit (30 days) | Preferred: managed control plane at $0 |
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

**Free-tier sizing reality (drives all sizing decisions):**
- Free-tier nodes have 1 vCPU / 1–2 GB RAM. A full 8-service Spring Boot
  deployment does not fit. The cloud profile deploys the **core slice** only:
  discovery, config, gateway, movie-service + MongoDB + Redis, with JVM flags
  `-XX:MaxRAMPercentage=60 -Xss256k` and single replicas.
- Everything else (user/actor/ai/media services, full ELK) runs in the
  **local** profile; the manifests are identical, only Kustomize overlays and
  replica counts differ.
- Infrastructure MUST be destroyable with a single `terraform destroy` and
  rebuildable with a single `terraform apply` (no manual console changes,
  ever) — this is what makes the ephemeral-cluster model workable.

### 11.2 Terraform Layout

```
infrastructure/terraform/
├── modules/
│   ├── network/          # VPC/VNet, subnets, security groups/NSGs
│   ├── cluster-aks/      # AKS cluster (free control plane, 1 node pool)
│   ├── cluster-k3s/      # EC2 t3.micro + k3s bootstrap (user_data)
│   └── budget-guard/     # zero-spend budget + alert (FIRST resource applied)
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
  never commit state files.
- All resources tagged/labeled `project=filmpire`, `managed-by=terraform`.
- Credentials come from environment (`ARM_*`, `AWS_*`) or OIDC in CI —
  never from `.tf` files or committed `tfvars`.
- `terraform plan` runs in CI on every PR touching `infrastructure/terraform/`;
  `terraform apply` is manual (workflow_dispatch) only.

**Example — AKS free-tier cluster (modules/cluster-aks):**
```hcl
resource "azurerm_kubernetes_cluster" "filmpire" {
  name                = "filmpire-aks"
  location            = var.location
  resource_group_name = var.resource_group_name
  dns_prefix          = "filmpire"
  sku_tier            = "Free"          # free control plane

  default_node_pool {
    name       = "default"
    node_count = 1
    vm_size    = "Standard_B2ats_v2"    # free-tier eligible burstable
  }

  identity { type = "SystemAssigned" }
}
```

**Example — AWS k3s node (modules/cluster-k3s):**
```hcl
resource "aws_instance" "k3s_server" {
  ami           = data.aws_ami.al2023.id
  instance_type = "t3.micro"            # free tier: 750 h/month
  user_data     = <<-EOF
    #!/bin/bash
    curl -sfL https://get.k3s.io | sh -s - \
      --disable traefik --write-kubeconfig-mode 644
  EOF
  root_block_device { volume_size = 20 }  # within 30 GB free EBS
  tags = { Name = "filmpire-k3s", project = "filmpire" }
}
```

### 11.3 Kubernetes Layout

Kustomize base + overlays (no Helm for own services; Helm only for
third-party charts):

```
infrastructure/kubernetes/
├── base/                      # cloud-agnostic manifests
│   ├── discovery-service/     # Deployment, Service, probes
│   ├── config-service/
│   ├── api-gateway/           # + Ingress
│   ├── movie-service/
│   ├── user-service/
│   ├── actor-service/
│   ├── mongodb/               # StatefulSet + PVC
│   ├── redis/
│   └── kustomization.yaml
├── overlays/
│   ├── local/                 # all services, generous resources
│   ├── azure/                 # core slice, B-series sizing, ghcr.io images
│   └── aws/                   # core slice, t3.micro sizing, ghcr.io images
├── monitoring/                # see section 12
└── logging/                   # see section 12
```

**Deployment conventions:**
- Every service: readiness probe on `/actuator/health/readiness`, liveness on
  `/actuator/health/liveness`, resource requests/limits mandatory.
- Config via ConfigMaps generated from the config-service's native config
  files; secrets via Kubernetes Secrets (SOPS-encrypted in git, or created
  out-of-band by Terraform — never plaintext in the repo).
- Images built by CI, tagged with the git SHA, pushed to ghcr.io (free for public repos).

### 11.4 CI/CD Pipeline (GitHub Actions)

```
push to main
  └─► backend-ci.yml        build + test (existing)
        └─► docker-publish.yml   build images, tag ${GIT_SHA}, push registry
              └─► deploy.yml (workflow_dispatch / on-tag)
                    ├─ terraform plan/apply (manual gate)
                    └─ kubectl apply -k overlays/<cloud>
```

- `terraform plan` posted on PRs that touch `infrastructure/terraform/`.
- Deploys are explicit (`workflow_dispatch` with cloud choice) — never
  automatic on merge, to protect the free-tier hour budget.
- Rollback = `kubectl rollout undo` (images are SHA-tagged and kept in the
  registry).

---

## 12. Monitoring & Observability

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

| SLO | Target | Error budget consequence |
|-----|--------|--------------------------|
| Availability (non-5xx) | 99.0% | budget burn >2×: freeze feature work, fix reliability |
| Latency, cache-served reads | P95 < 200 ms | sustained breach: investigate Redis/Mongo before adding features |
| Latency, TMDB-fallback reads | P95 < 800 ms | breach without TMDB degradation: profile the read-through chain |
| Facade shape fidelity | 100% (byte-identical) | any regression is a release blocker, caught by fixture tests |

### 12.5 Rollout Order

1. Instrument all services (actuator + Prometheus registry + JSON logging) —
   no infra needed, verifiable with curl.
2. Local: docker-compose.elk.yml + kube-prometheus-stack on minikube.
3. Terraform: Azure AKS first (free control plane), then AWS k3s.
4. Cloud deploy of core slice + monitoring; ELK stays local.

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
│   ├── web-nextjs/
│   │   ├── app/
│   │   │   ├── (auth)/
│   │   │   ├── (dashboard)/
│   │   │   ├── movies/
│   │   │   ├── actors/
│   │   │   └── layout.tsx
│   │   ├── components/
│   │   ├── lib/
│   │   ├── public/
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   ├── next.config.js
│   │   └── README.md
│   └── mobile-react-native/
│       ├── app/
│       ├── components/
│       ├── services/
│       ├── package.json
│       ├── app.json
│       └── README.md
├── infrastructure/
│   ├── docker/
│   │   ├── docker-compose.yml
│   │   └── docker-compose.prod.yml
│   ├── kubernetes/
│   │   ├── deployments/
│   │   ├── services/
│   │   ├── configmaps/
│   │   └── secrets/
│   └── scripts/
│       ├── setup-dev-env.sh
│       ├── deploy.sh
│       └── rollback.sh
├── docs/
│   ├── architecture/
│   │   ├── ARCHITECTURE.md
│   │   ├── adr/
│   │   │   ├── 001-microservices-architecture.md
│   │   │   ├── 002-database-choices.md
│   │   │   └── ...
│   │   └── diagrams/
│   ├── api/
│   │   ├── openapi.yml
│   │   └── postman/
│   └── guides/
│       ├── SETUP.md
│       ├── DEPLOYMENT.md
│       └── TROUBLESHOOTING.md
├── tools/
│   └── tmdb-importer/
│       ├── src/
│       ├── build.gradle.kts
│       └── README.md
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

**Document Version:** 1.4.0  
**Last Updated:** July 22, 2026  
**Status:** Living Document — Discovery/Config/Gateway/Movie/Actor/User services implemented and running on Spring Boot 4.1; AI/Media services still stubs (see §2.3 ADRs and per-service sections for current status)

