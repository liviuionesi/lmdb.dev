# Filmpire Microservices - Enterprise Software Architecture Document

**Version:** 1.0.0  
**Date:** November 14, 2025  
**Author:** Software Architecture Team  
**Purpose:** Portfolio project demonstrating enterprise-grade full-stack development

---

## Executive Summary

This document outlines the complete architecture for Filmpire, a production-ready microservices-based movie platform. The system replicates TMDB API functionality with a hybrid caching strategy, demonstrates modern Java 25 and Spring Boot 3.5 capabilities, and includes Next.js 16 web application and React Native 0.82 mobile apps. The project emphasizes enterprise best practices, comprehensive testing (TDD), and deployment readiness.

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
11. [Deployment Architecture](#deployment-architecture)
12. [Monitoring & Observability](#monitoring--observability)

---

## 1. Technology Stack

### 1.1 Backend (Exact Versions)

| Technology | Version | Installation Method | Purpose |
|------------|---------|---------------------|---------|
| Java | 25 | SDKMAN | Programming language |
| Spring Boot | 3.5.8-SNAPSHOT | Gradle | Framework |
| Gradle | 9.2.0 | SDKMAN | Build tool |
| Spring Cloud | 2025.0.0 | Gradle | Microservices infrastructure |
| Spring AI | 1.0.0-SNAPSHOT | Gradle | AI/ML integration |
| PostgreSQL | 18.1 | Docker/Podman | Relational database |
| MongoDB | 8.2 | Docker/Podman | Document database |
| Redis | 8.2 | Docker/Podman | Caching layer |
| gRPC | 1.76.0 | Gradle | Service communication |
| JWT (jjwt) | 0.13.0 | Gradle | Authentication |
| MapStruct | 1.6.3 | Gradle | DTO mapping |
| Lombok | 1.18.42 | Gradle | Boilerplate reduction |
| MinIO | 8.5.7 | Gradle | Object storage client |
| JUnit | 5.11.3 | Gradle | Testing framework |
| Mockito | 5.19.0 | Gradle | Mocking framework |
| TestContainers | 1.21.2 | Gradle | Integration testing |
| Springdoc OpenAPI | 2.8.14 | Gradle | API documentation |
| JaCoCo | 0.8.11 | Gradle | Code coverage |

### 1.2 Frontend Web (Exact Versions)

| Technology | Version | Installation Method | Purpose |
|------------|---------|---------------------|---------|
| Node.js | 24.11.1 LTS | NVM | Runtime |
| npm | 11.6.2 | NVM | Package manager |
| Next.js | 16.0.0 | npm | React framework |
| React | 19.0.2 | npm | UI library |
| TypeScript | 5.7.x | npm | Type safety |
| Tailwind CSS | 3.4.x | npm | Styling |
| Material UI (MUI) | 7.3.5 | npm | UI components |
| MUI Icons | 7.3.5 | npm | Icon library |
| TanStack Query | 5.0.8 | npm | Data fetching |
| Zustand | 5.0.8 | npm | State management |
| Zod | 3.24.x | npm | Validation |
| React Hook Form | 7.51.0 | npm | Form handling |

### 1.3 Mobile (Exact Versions)

| Technology | Version | Installation Method | Purpose |
|------------|---------|---------------------|---------|
| React Native | 0.76.3 | npm | Mobile framework |
| Expo SDK | 52.0.0 | npm | Development platform |
| TypeScript | 5.7.x | npm | Type safety |
| React Navigation | 7.x | npm | Navigation |
| Expo Router | 4.x | npm | File-based routing |

### 1.4 DevOps & Infrastructure

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
│                     Client Applications                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  Next.js 16  │  │   iOS App    │  │  Android App │       │
│  │  (React 19)  │  │   (Expo)     │  │    (Expo)    │       │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘       │
└─────────┼──────────────────┼──────────────────┼─────────────┘
          │                  │                  │
          └──────────────────┴──────────────────┘
                             │
                             ▼
          ┌──────────────────────────────────┐
          │   API Gateway (Spring Cloud)     │
          │         Port: 8080               │
          │  - Rate Limiting                 │
          │  - Authentication                │
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
- **Event-Driven**: Future consideration for Kafka/RabbitMQ
- **Caching**: Redis for frequently accessed data

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

**Domain Model:**
```java
@Document(collection = "movies")
public record Movie(
    @Id String id,
    String tmdbId,
    String title,
    String originalTitle,
    String overview,
    LocalDate releaseDate,
    Integer runtime,
    Double voteAverage,
    Integer voteCount,
    String posterPath,
    String backdropPath,
    List<Genre> genres,
    List<CastMember> cast,
    List<CrewMember> crew,
    List<Video> videos,
    List<ProductionCompany> productionCompanies,
    List<String> spokenLanguages,
    MovieStatus status
) {}
```

**TMDB Endpoints Replicated:**
- `GET /api/v1/movies/popular` - Popular movies
- `GET /api/v1/movies/top-rated` - Top rated movies
- `GET /api/v1/movies/upcoming` - Upcoming movies
- `GET /api/v1/movies/search?query={query}` - Search movies
- `GET /api/v1/movies/{id}` - Get movie details
- `GET /api/v1/movies/discover?genre={id}` - Discover by genre
- `GET /api/v1/genres` - Get all genres

**Service Layer (TDD Example):**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {
    
    private final MovieRepository movieRepository;
    private final TmdbClient tmdbClient;
    private final CacheManager cacheManager;
    
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

**Test Example (TDD):**
```java
@ExtendWith(MockitoExtension.class)
class MovieServiceTest {
    
    @Mock
    private MovieRepository movieRepository;
    
    @Mock
    private TmdbClient tmdbClient;
    
    @Mock
    private CacheManager cacheManager;
    
    @InjectMocks
    private MovieService movieService;
    
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
- Strong many-to-many relationships (actors ↔ movies)
- Structured actor profiles
- Complex join queries for filmography
- Referential integrity

**Domain Model:**
```java
@Entity
@Table(name = "actors")
public class Actor {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "tmdb_id", unique = true)
    private String tmdbId;
    
    @Column(nullable = false)
    private String name;
    
    @Column(length = 2000)
    private String biography;
    
    @Column(name = "birth_date")
    private LocalDate birthDate;
    
    @Column(name = "birth_place")
    private String birthPlace;
    
    @Column(name = "profile_path")
    private String profilePath;
    
    @Column
    private Double popularity;
    
    @ManyToMany
    @JoinTable(
        name = "movie_cast",
        joinColumns = @JoinColumn(name = "actor_id"),
        inverseJoinColumns = @JoinColumn(name = "movie_id")
    )
    private Set<MovieReference> movies = new HashSet<>();
}

@Entity
@Table(name = "movie_cast")
public class MovieCast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "actor_id")
    private UUID actorId;
    
    @Column(name = "movie_id")
    private String movieId;
    
    @Column(name = "character_name")
    private String characterName;
    
    @Column(name = "cast_order")
    private Integer order;
}
```

**API Endpoints:**
- `GET /api/v1/actors/{id}` - Get actor details
- `GET /api/v1/actors/{id}/movies` - Get actor's filmography
- `GET /api/v1/actors/search?query={query}` - Search actors

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

**Domain Model:**
```java
@Document(collection = "conversations")
public record Conversation(
    @Id String id,
    String userId,
    ConversationType type,
    List<Message> messages,
    Map<String, Object> context,
    Instant createdAt,
    Instant updatedAt
) {}

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

**Spring AI Integration:**
```java
@Service
@RequiredArgsConstructor
public class AIRecommendationService {
    
    private final ChatClient chatClient;
    private final EmbeddingClient embeddingClient;
    
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
**Storage:** MinIO (S3-compatible) or local filesystem

**Why MongoDB?**
- Document-oriented metadata storage
- Nested file information (thumbnails, sizes, formats)
- Flexible schema for different media types

**Domain Model:**
```java
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
) {}

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

**Initial TMDB Import:**
```java
@Component
@RequiredArgsConstructor
public class TmdbDataImporter {
    
    private final TmdbClient tmdbClient;
    private final MovieRepository movieRepository;
    private final ActorRepository actorRepository;
    
    @Scheduled(cron = "0 0 2 * * ?")  // Daily at 2 AM
    public void importPopularMovies() {
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
    }
}
```

### 4.3 Caching Strategy

**Redis Cache Configuration:**
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
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

### 5.1 TMDB Endpoints Mapping

| TMDB Endpoint | Filmpire Endpoint | Service | Method |
|---------------|-------------------|---------|--------|
| `/genre/movie/list` | `/api/v1/genres` | Movie | GET |
| `/search/movie` | `/api/v1/movies/search` | Movie | GET |
| `/movie/{category}` | `/api/v1/movies/{category}` | Movie | GET |
| `/discover/movie?with_genres={id}` | `/api/v1/movies/discover` | Movie | GET |
| `/movie/{id}` | `/api/v1/movies/{id}` | Movie | GET |
| `/movie/{id}/recommendations` | `/api/v1/movies/{id}/recommendations` | AI | GET |
| `/account/{id}/favorite` | `/api/v1/users/favorites` | User | GET |
| `/account/{id}/watchlist` | `/api/v1/users/watchlist` | User | GET |
| `/person/{id}` | `/api/v1/actors/{id}` | Actor | GET |
| `/discover/movie?with_cast={id}` | `/api/v1/actors/{id}/movies` | Actor | GET |

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

sdk install gradle 8.12
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

# Frontend setup
cd ../../frontend/web-nextjs
npm install
npm run dev

# Mobile setup
cd ../mobile-react-native
npm install
npx expo start
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

**Backend: `gradle.properties`**
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
jacocoVersion=0.8.11
```

**Frontend: `package.json`**
```json
{
  "name": "filmpire-web",
  "version": "1.0.0",
  "engines": {
    "node": ">=24.11.1",
    "npm": ">=11.6.2"
  },
  "dependencies": {
    "next": "16.0.0",
    "react": "19.0.0",
    "react-dom": "19.0.0",
    "typescript": "5.7.2",
    "@mui/material": "7.3.5",
    "@mui/icons-material": "7.3.5",
    "@emotion/react": "^11.13.5",
    "@emotion/styled": "^11.13.5",
    "@tanstack/react-query": "5.0.8",
    "zustand": "5.0.8",
    "zod": "3.24.1",
    "react-hook-form": "7.51.0",
    "tailwindcss": "3.4.17"
  }
}
```

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
| 6-7 | 2 weeks | Advanced | AI Service, Media Service |
| 8-9 | 2 weeks | Web | Next.js 16 application |
| 10-11 | 2 weeks | Mobile | React Native apps |
| 12 | 1 week | Testing | E2E, performance, security |
| 13 | 1 week | Deployment | Production deploy, docs |

**Total Timeline:** 13 weeks (3.25 months)

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

**Tools:** JUnit 5, Mockito, AssertJ

**Example:**
```java
@ExtendWith(MockitoExtension.class)
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

**Tools:** Spring Boot Test, TestContainers, RestAssured

**Example:**
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class MovieServiceIntegrationTest {
    
    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7.0")
        .withExposedPorts(27017);
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
    
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

**Tools:** Playwright (web), Detox (mobile)

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

### 10.6 Test Coverage Requirements

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

### 11.1 Deployment Options

**Option 1: Render (Recommended for Portfolio)**
- Free tier: 750 hours/month
- Automatic deployments from GitHub
- Managed PostgreSQL (free tier)
- Easy setup, good for demos

**Option 2: Railway**
- $5 free credit/month
- Simple UI
- Good for microservices
- Auto-scaling

**Option 3: Fly.io**
- Free tier with limitations
- Global deployment
- Good performance

### 11.2 Deployment Strategy

**Backend Services (Render):**
```yaml
# render.yaml
services:
  - type: web
    name: filmpire-api-gateway
    env: docker
    dockerfilePath: ./backend/api-gateway/Dockerfile
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
        value: https://filmpire-eureka.onrender.com/eureka/
    healthCheckPath: /actuator/health
    
  - type: web
    name: filmpire-movie-service
    env: docker
    dockerfilePath: ./backend/movie-service/Dockerfile
    envVars:
      - key: SPRING_DATA_MONGODB_URI
        fromDatabase:
          name: filmpire-mongodb
          property: connectionString
```

**Frontend (Vercel):**
```json
// vercel.json
{
  "buildCommand": "npm run build",
  "outputDirectory": ".next",
  "framework": "nextjs",
  "env": {
    "NEXT_PUBLIC_API_URL": "https://filmpire-gateway.onrender.com"
  }
}
```

**Mobile (Expo EAS):**
```json
// eas.json
{
  "build": {
    "production": {
      "android": {
        "buildType": "apk"
      },
      "ios": {
        "simulator": false
      }
    }
  },
  "submit": {
    "production": {
      "android": {
        "serviceAccountKeyPath": "./google-play-key.json",
        "track": "internal"
      },
      "ios": {
        "appleId": "your@email.com",
        "ascAppId": "1234567890",
        "appleTeamId": "ABCD123456"
      }
    }
  }
}
```

### 11.3 CI/CD Pipeline

**GitHub Actions Workflow:**

```yaml
# .github/workflows/backend-ci-cd.yml
name: Backend CI/CD

on:
  push:
    branches: [main, develop]
    paths:
      - 'backend/**'
  pull_request:
    branches: [main, develop]

jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [movie-service, user-service, actor-service, ai-service]
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Java 25
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '25'
          cache: 'gradle'
      
      - name: Run Tests
        working-directory: backend/${{ matrix.service }}
        run: |
          ./gradlew clean test
          ./gradlew jacocoTestReport
      
      - name: SonarQube Scan
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        working-directory: backend/${{ matrix.service }}
        run: ./gradlew sonar
      
      - name: Upload Coverage
        uses: codecov/codecov-action@v4
        with:
          files: backend/${{ matrix.service }}/build/reports/jacoco/test/jacocoTestReport.xml
  
  build:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Build Docker Images
        run: |
          docker build -t filmpire/movie-service:latest backend/movie-service
          docker build -t filmpire/user-service:latest backend/user-service
      
      - name: Push to Docker Hub
        env:
          DOCKER_USERNAME: ${{ secrets.DOCKER_USERNAME }}
          DOCKER_PASSWORD: ${{ secrets.DOCKER_PASSWORD }}
        run: |
          echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin
          docker push filmpire/movie-service:latest
  
  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
      - name: Deploy to Render
        env:
          RENDER_API_KEY: ${{ secrets.RENDER_API_KEY }}
        run: |
          curl -X POST \
            https://api.render.com/deploy/srv-xxx \
            -H "Authorization: Bearer $RENDER_API_KEY"
```

### 11.4 Monitoring & Observability

**Spring Boot Actuator Endpoints:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

**Monitoring Stack:**
- **Metrics**: Micrometer + Prometheus
- **Logging**: Logback + ELK Stack (Elasticsearch, Logstash, Kibana)
- **Tracing**: Zipkin or Jaeger
- **Alerting**: Prometheus Alertmanager

---

## 12. Success Criteria

### 12.1 Technical Metrics

- [ ] All TMDB endpoints replicated and functional
- [ ] 85%+ test coverage across all services
- [ ] Sub-200ms average API response time
- [ ] Zero critical security vulnerabilities (Snyk/OWASP)
- [ ] SonarQube quality gate: A rating
- [ ] Mobile apps published to TestFlight and Google Play Internal Testing
- [ ] Complete API documentation (OpenAPI/Swagger)
- [ ] CI/CD pipeline with <10 minute build time
- [ ] 99% uptime over 30 days

### 12.2 Documentation Completeness

- [ ] Architecture decision records (ADRs) for major decisions
- [ ] README per service with setup instructions
- [ ] API documentation with examples
- [ ] Postman collections for all endpoints
- [ ] Sequence diagrams for critical flows
- [ ] Deployment guide
- [ ] Troubleshooting guide

### 12.3 Portfolio Presentation

**Demonstrated Skills:**
- Enterprise microservices architecture
- Spring Boot 3.5.8 + Spring Cloud 2025.0.0
- Java 25 latest features (records, pattern matching, virtual threads)
- REST + gRPC APIs
- PostgreSQL + MongoDB hybrid strategy
- Spring AI integration
- Next.js 16 + React 19
- React Native 0.82 mobile development
- Docker + Kubernetes orchestration
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

## Appendix B: Key Java 25 Features Showcased

### Records (Immutable DTOs)
```java
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

### Virtual Threads (Project Loom)
```java
@Configuration
public class AsyncConfig {
    
    @Bean
    public Executor taskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

@Service
public class MovieService {
    
    @Async
    public CompletableFuture<List<Movie>> fetchMoviesAsync() {
        // Runs on virtual thread - extremely lightweight
        return CompletableFuture.completedFuture(
            movieRepository.findAll()
        );
    }
}
```

---

**Document Version:** 1.0.0  
**Last Updated:** November 14, 2025  
**Status:** Draft - Ready for Implementation

