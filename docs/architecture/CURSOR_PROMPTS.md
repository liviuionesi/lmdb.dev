# Cursor IDE Prompts Guide - Filmpire Microservices

**Version:** 1.0.0  
**Purpose:** Comprehensive prompts for building each component of the Filmpire microservices platform  
**Target:** Cursor AI IDE for efficient development

---

## Table of Contents

1. [Project Setup Phase](#project-setup-phase)
2. [Infrastructure Services](#infrastructure-services)
3. [Core Microservices](#core-microservices)
4. [Advanced Services](#advanced-services)
5. [Frontend Development](#frontend-development)
6. [Mobile Development](#mobile-development)
7. [Testing & Quality](#testing--quality)
8. [Deployment](#deployment)

---

## Project Setup Phase

### Prompt 1: Initialize Project Structure

```
Create a multi-module microservices project structure for Filmpire with the following requirements:

**Backend Structure:**
- Create a root directory `filmpire-microservices/backend/`
- 8 microservices: api-gateway, discovery-service, config-service, movie-service, user-service, actor-service, ai-service, media-service
- Shared library module for common utilities
- Each service should have:
  - Gradle build files (build.gradle using Groovy DSL)
  - src/main/java with package structure: com.filmpire.{service-name}
  - src/main/resources with application.yml
  - src/test/java for tests
  - Dockerfile for containerization
  - README.md with service description

**Frontend Structure:**
- frontend/web-nextjs/ for Next.js 16 application
- frontend/mobile-react-native/ for React Native 0.82 application

**Infrastructure:**
- infrastructure/docker/ with docker-compose.yml
- infrastructure/kubernetes/ with deployment manifests
- infrastructure/scripts/ for automation

**Documentation:**
- docs/architecture/ for ADRs and diagrams
- docs/api/ for OpenAPI specs
- docs/guides/ for setup and deployment

**Versions:**
- Java 25
- Spring Boot 3.5.8
- Gradle 9.2.0
- Spring Cloud 2025.0.0
- Spring AI 1.0.0-SNAPSHOT
- Lombok 1.18.42 (Java 25 support)
- Node.js 24.11.1 LTS
- Next.js 16.0.0
- React 19.0.2
- React Native 0.76.3
- Docker/Podman for containers (docker commands are aliased)

Use the project structure from ARCHITECTURE.md as reference.
```

### Prompt 2: Setup Gradle Multi-Module Build

```
Create a Gradle multi-module build configuration for the backend microservices:

**Root settings.gradle:**
- Must have pluginManagement and dependencyResolutionManagement FIRST (before rootProject.name)
- Include all 8 microservices
- Include shared-library module
- Configure plugin repositories

**Root build.gradle:**
- Configure plugins with 'apply false' for subprojects
- Define common dependencies and versions in gradle.properties
- Configure Java 25 toolchain for all subprojects
- Set up Spring Boot 3.5.8 and Spring Cloud 2025.0.0
- Configure common plugins: spring-boot, spring-dependency-management
- Set up code quality plugins: spotless, jacoco, sonarqube

**gradle.properties:**
```
javaVersion=25
springBootVersion=3.5.8-SNAPSHOT
springCloudVersion=2025.0.0
springAiVersion=1.0.0-SNAPSHOT
lombokVersion=1.18.42
mapstructVersion=1.6.3
jjwtVersion=0.13.0
grpcVersion=1.76.0
springdocVersion=2.8.14
minioVersion=8.5.7
mockitoVersion=5.19.0
testcontainersVersion=1.21.2
jacocoVersion=0.8.14
```

**Shared Library:**
- Common DTOs
- Exception classes
- Utility functions
- Constants

Ensure all services can be built with `./gradlew build`.
```

### Prompt 3: GitHub Project Setup

```
Create a complete GitHub repository setup with enterprise best practices:

**Issue Templates (.github/ISSUE_TEMPLATE/):**
1. bug_report.md - Bug report template
2. feature_request.md - Feature request template
3. task.md - Task template
4. question.md - Question template

**Pull Request Template (.github/PULL_REQUEST_TEMPLATE.md):**
- Description checklist
- Type of change (feat/fix/docs/test)
- Testing checklist
- Documentation checklist
- Code review checklist

**GitHub Actions Workflows (.github/workflows/):**
1. backend-ci.yml - Backend CI pipeline
2. frontend-ci.yml - Frontend CI pipeline
3. mobile-ci.yml - Mobile CI pipeline
4. deploy.yml - Deployment pipeline

**Labels:**
Create labels for: priority (P0-P3), type (bug/feature/docs), status (todo/in-progress/review/done), services (movie/user/actor/ai/media)

**Branch Protection:**
- Require PR reviews (2 approvals)
- Require status checks to pass
- Require branches to be up to date
- No force pushes
```

---

## Infrastructure Services

### Prompt 4: Discovery Service (Eureka Server)

```
Create a Spring Cloud Eureka Server for service discovery:

**Requirements:**
- Port: 8761
- Spring Boot 3.5.8
- Spring Cloud Netflix Eureka Server
- Standalone mode (not registering itself)
- Health checks enabled
- Dashboard UI accessible

**Dependencies (build.gradle):**
```groovy
plugins {
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}

bootJar {
    archiveBaseName = 'discovery-service'
    archiveVersion = project.version
}
```

**Application Configuration (application.yml):**
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

**Main Class:**
- Add @EnableEurekaServer annotation
- Configure Spring Boot application
- No @EnableEurekaClient needed (auto-configured in Spring Cloud 2024+)

**Dockerfile:**
- Multi-stage build
- Use Java 25 base image
- Expose port 8761

**Tests:**
- Test Eureka server starts successfully
- Test dashboard is accessible
- Test health endpoint
```

### Prompt 5: Config Service (Spring Cloud Config)

```
Create a Spring Cloud Config Server for centralized configuration:

**Requirements:**
- Port: 8888
- Git-backed configuration repository
- Support for multiple environments (dev, prod)
- Eureka client registration
- Encryption support for sensitive data

**Dependencies (build.gradle):**
```groovy
plugins {
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencies {
    implementation 'org.springframework.cloud:spring-cloud-config-server'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}

bootJar {
    archiveBaseName = 'config-service'
    archiveVersion = project.version
}
```

**Application Configuration:**
```yaml
server:
  port: 8888

spring:
  application:
    name: config-service
  cloud:
    config:
      server:
        git:
          uri: https://github.com/{org}/filmpire-config-repo
          default-label: main
          clone-on-start: true

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

**Configuration Repository Structure:**
Create a separate config repository with:
- application.yml (shared config)
- application-dev.yml
- application-prod.yml
- movie-service.yml
- user-service.yml
- actor-service.yml
- ai-service.yml
- media-service.yml

**Encryption:**
- Configure encryption key
- Encrypt database passwords and API keys

**Tests:**
- Test configuration retrieval
- Test environment-specific configs
- Test Eureka registration
```

### Prompt 6: API Gateway (Spring Cloud Gateway)

```
Create a Spring Cloud Gateway as the single entry point for all client requests:

**Requirements:**
- Port: 8080
- Route all requests to microservices
- JWT authentication filter
- Rate limiting (100 requests/minute per IP)
- CORS configuration
- Circuit breaker pattern
- Request/response logging
- Eureka integration for service discovery

**Dependencies:**
```kotlin
dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("io.jsonwebtoken:jjwt-impl:0.13.0")
    implementation("io.jsonwebtoken:jjwt-jackson:0.13.0")
}
```

**Routes Configuration:**
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
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
        
        - id: user-service
          uri: lb://USER-SERVICE
          predicates:
            - Path=/api/v1/users/**, /api/v1/auth/**
          filters:
            - RewritePath=/api/v1/(?<segment>.*), /${segment}
        
        - id: actor-service
          uri: lb://ACTOR-SERVICE
          predicates:
            - Path=/api/v1/actors/**
        
        - id: ai-service
          uri: lb://AI-SERVICE
          predicates:
            - Path=/api/v1/ai/**, /api/v1/recommendations/**
```

**Security Configuration:**
- JwtAuthenticationFilter to validate JWT tokens
- Exclude /api/v1/auth/** from authentication
- CORS configuration for frontend origins

**Tests:**
- Test routing to each service
- Test JWT authentication
- Test rate limiting
- Test CORS headers
```

---

## Core Microservices

### Prompt 7: Movie Service - Domain Model & Repository

```
Create the Movie Service domain model and repository layer using MongoDB:

**Requirements:**
- MongoDB for storing movie data (complex nested structures)
- Domain-Driven Design (DDD) approach
- Use Java 25 records for immutable DTOs
- MapStruct for DTO mapping
- Repository pattern with Spring Data MongoDB

**Domain Model (records):**

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
    Double popularity,
    String posterPath,
    String backdropPath,
    List<Genre> genres,
    List<CastMember> cast,
    List<CrewMember> crew,
    List<Video> videos,
    List<ProductionCompany> productionCompanies,
    List<String> spokenLanguages,
    MovieStatus status,
    Instant createdAt,
    Instant updatedAt
) {}

public record Genre(String id, String name) {}

public record CastMember(
    String actorId,
    String name,
    String character,
    Integer order,
    String profilePath
) {}

public record CrewMember(
    String personId,
    String name,
    String job,
    String department
) {}

public record Video(
    String key,
    String name,
    String site,
    String type,
    Boolean official
) {}

public enum MovieStatus {
    RUMORED, PLANNED, IN_PRODUCTION, POST_PRODUCTION, RELEASED, CANCELED
}
```

**Repository:**
```java
@Repository
public interface MovieRepository extends MongoRepository<Movie, String> {
    Page<Movie> findByStatusOrderByPopularityDesc(MovieStatus status, Pageable pageable);
    Page<Movie> findByGenresContaining(Genre genre, Pageable pageable);
    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    Optional<Movie> findByTmdbId(String tmdbId);
}
```

**DTOs:**
- MovieDTO (for API responses)
- CreateMovieRequest
- UpdateMovieRequest
- MovieSearchResponse

**Mapper (MapStruct):**
- Movie <-> MovieDTO conversion

**Tests:**
- Repository tests with @DataMongoTest
- Test CRUD operations
- Test custom queries
- Use TestContainers for MongoDB
```

### Prompt 8: Movie Service - Business Logic & REST API

```
Create the Movie Service business logic and REST API with TDD approach:

**Service Layer:**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {
    
    private final MovieRepository movieRepository;
    private final TmdbClient tmdbClient;
    private final CacheManager cacheManager;
    
    /**
     * Retrieves movies by category with caching and TMDB fallback.
     * 
     * @param category Movie category (POPULAR, TOP_RATED, UPCOMING)
     * @param page Page number (0-indexed)
     * @return Paginated list of movies
     * @throws MovieServiceException if retrieval fails
     */
    public Page<MovieDTO> getMoviesByCategory(MovieCategory category, int page);
    
    /**
     * Searches movies by title.
     */
    public Page<MovieDTO> searchMovies(String query, int page);
    
    /**
     * Gets movie details by ID.
     */
    public MovieDTO getMovieById(String id);
    
    /**
     * Discovers movies by genre.
     */
    public Page<MovieDTO> discoverByGenre(String genreId, int page);
    
    /**
     * Gets all genres.
     */
    public List<GenreDTO> getAllGenres();
}
```

**REST Controller:**
```java
@RestController
@RequestMapping("/api/v1/movies")
@RequiredArgsConstructor
@Validated
public class MovieController {
    
    @GetMapping("/popular")
    public ResponseEntity<Page<MovieDTO>> getPopularMovies(@RequestParam(defaultValue = "0") int page);
    
    @GetMapping("/top-rated")
    public ResponseEntity<Page<MovieDTO>> getTopRatedMovies(@RequestParam(defaultValue = "0") int page);
    
    @GetMapping("/upcoming")
    public ResponseEntity<Page<MovieDTO>> getUpcomingMovies(@RequestParam(defaultValue = "0") int page);
    
    @GetMapping("/search")
    public ResponseEntity<Page<MovieDTO>> searchMovies(
        @RequestParam String query,
        @RequestParam(defaultValue = "0") int page
    );
    
    @GetMapping("/{id}")
    public ResponseEntity<MovieDTO> getMovieById(@PathVariable String id);
    
    @GetMapping("/discover")
    public ResponseEntity<Page<MovieDTO>> discoverByGenre(
        @RequestParam String genreId,
        @RequestParam(defaultValue = "0") int page
    );
}
```

**TMDB Client (Feign):**
- Interface for calling TMDB API
- Fallback mechanism for errors
- Rate limiting

**Exception Handling:**
- Global exception handler with @RestControllerAdvice
- Custom exceptions: MovieNotFoundException, TmdbApiException

**Caching:**
- Redis caching with @Cacheable
- Different TTL for different endpoints

**OpenAPI Documentation:**
- Add @OpenAPIDefinition
- Document all endpoints with @Operation
- Add example responses

**Tests (TDD - Write First!):**
1. Unit tests for MovieService (Mockito)
2. Integration tests for MovieController (MockMvc)
3. Integration tests with TestContainers (MongoDB + Redis)
4. Test caching behavior
5. Test TMDB fallback
6. 85%+ coverage
```

### Prompt 9: User Service - Authentication & Security

```
Create the User Service with JWT authentication and PostgreSQL:

**Requirements:**
- PostgreSQL for user data (ACID compliance)
- JWT authentication with refresh tokens
- BCrypt password hashing (strength 12)
- User roles and permissions
- Spring Security configuration
- Eureka client registration

**Domain Model:**
```java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    
    private Instant createdAt;
    private Instant lastLogin;
    private boolean enabled;
    private boolean accountNonLocked;
}

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    private UUID id;
    private UUID userId;
    private String token;
    private Instant expiryDate;
}
```

**Security Configuration:**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // JWT-based stateless security
        // Permit /auth/** endpoints
        // Require authentication for all other endpoints
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    
    @Bean
    public JwtTokenProvider jwtTokenProvider() {
        // Generate and validate JWT tokens
    }
}
```

**REST API:**
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request);
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request);
    
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody RefreshTokenRequest request);
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token);
}

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDTO> getProfile();
    
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDTO> updateProfile(@RequestBody UpdateProfileRequest request);
    
    @GetMapping("/favorites")
    public ResponseEntity<Page<MovieDTO>> getFavorites(@RequestParam(defaultValue = "0") int page);
    
    @PostMapping("/favorites/{movieId}")
    public ResponseEntity<Void> addToFavorites(@PathVariable String movieId);
    
    @DeleteMapping("/favorites/{movieId}")
    public ResponseEntity<Void> removeFromFavorites(@PathVariable String movieId);
}
```

**JWT Structure:**
- Access token: 1-hour expiration
- Refresh token: 7-day expiration
- Claims: userId, username, email, roles

**Database Migration (Flyway):**
- V1__create_users_table.sql
- V2__create_favorites_table.sql
- V3__create_watchlist_table.sql
- V4__create_refresh_tokens_table.sql

**Tests:**
- Test user registration
- Test login with correct/incorrect credentials
- Test JWT token generation and validation
- Test refresh token flow
- Test favorites/watchlist operations
- Security tests with @WithMockUser
```

### Prompt 10: Actor Service - Relationships & Queries

```
Create the Actor Service with PostgreSQL for structured data and relationships:

**Requirements:**
- PostgreSQL for actor data and movie-actor relationships
- Many-to-many relationship with movies
- Complex queries for filmography
- Eureka client registration

**Domain Model:**
```java
@Entity
@Table(name = "actors")
@Data
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
    
    private LocalDate birthDate;
    private String birthPlace;
    private LocalDate deathDate;
    private String profilePath;
    private Double popularity;
    private String knownForDepartment;
    
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

**Repository:**
```java
@Repository
public interface ActorRepository extends JpaRepository<Actor, UUID> {
    Optional<Actor> findByTmdbId(String tmdbId);
    Page<Actor> findByNameContainingIgnoreCase(String name, Pageable pageable);
    
    @Query("SELECT a FROM Actor a JOIN a.movies m WHERE m.movieId = :movieId ORDER BY m.order")
    List<Actor> findByMovieId(@Param("movieId") String movieId);
}
```

**Service Layer:**
```java
@Service
public class ActorService {
    
    /**
     * Gets actor details by ID.
     */
    public ActorDTO getActorById(UUID id);
    
    /**
     * Gets actor's filmography.
     */
    public Page<MovieDTO> getActorFilmography(UUID id, int page);
    
    /**
     * Searches actors by name.
     */
    public Page<ActorDTO> searchActors(String query, int page);
    
    /**
     * Gets movies featuring a specific actor.
     */
    public Page<MovieDTO> getMoviesByActor(UUID actorId, int page);
}
```

**REST API:**
```java
@RestController
@RequestMapping("/api/v1/actors")
public class ActorController {
    
    @GetMapping("/{id}")
    public ResponseEntity<ActorDTO> getActorById(@PathVariable UUID id);
    
    @GetMapping("/{id}/movies")
    public ResponseEntity<Page<MovieDTO>> getActorFilmography(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page
    );
    
    @GetMapping("/search")
    public ResponseEntity<Page<ActorDTO>> searchActors(
        @RequestParam String query,
        @RequestParam(defaultValue = "0") int page
    );
}
```

**Database Migration (Flyway):**
- V1__create_actors_table.sql
- V2__create_movie_cast_table.sql
- Indexes on frequently queried columns

**Tests:**
- Test actor CRUD operations
- Test filmography queries
- Test many-to-many relationships
- Test search functionality
- Integration tests with TestContainers PostgreSQL
```

---

## Advanced Services

### Prompt 11: AI Service - Spring AI Integration

```
Create the AI Service with Spring AI, gRPC, and MongoDB:

**Requirements:**
- Spring AI for LLM integration (OpenAI/Ollama)
- Voice recognition using Whisper API
- Movie recommendations with embeddings
- Chat assistant
- gRPC for internal service communication
- MongoDB for conversation history and embeddings

**Dependencies:**
```kotlin
dependencies {
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
    implementation("io.grpc:grpc-spring-boot-starter:${grpcVersion}")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
}
```

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

**Spring AI Service:**
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
        String context = buildUserContext(userId, recentMovies);
        
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
    }
    
    /**
     * Chat assistant for movie queries.
     */
    public String chatWithAssistant(String userId, String message) {
        // Implement contextual chat
    }
}
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

**gRPC Server Implementation:**
```java
@GrpcService
public class AIGrpcService extends AIServiceGrpc.AIServiceImplBase {
    
    @Override
    public void getRecommendations(
            RecommendationRequest request,
            StreamObserver<RecommendationResponse> responseObserver) {
        // Implement
    }
}
```

**REST API:**
```java
@RestController
@RequestMapping("/api/v1/ai")
public class AIController {
    
    @PostMapping("/recommendations")
    public ResponseEntity<List<MovieRecommendation>> getRecommendations(
        @RequestBody RecommendationRequest request
    );
    
    @PostMapping("/transcribe")
    public ResponseEntity<TranscriptionResponse> transcribeVoice(
        @RequestParam("audio") MultipartFile audioFile
    );
    
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request);
}
```

**Tests:**
- Test recommendation generation
- Test voice transcription
- Test chat functionality
- Test gRPC service
- Mock OpenAI API calls
```

### Prompt 12: Media Service - File Storage

```
Create the Media Service for handling image and video uploads with MongoDB:

**Requirements:**
- File upload/download functionality
- MinIO or local filesystem storage
- Image resizing and thumbnail generation
- MongoDB for file metadata
- Support for multiple file types

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

public enum EntityType {
    MOVIE, ACTOR, USER
}

public enum MediaType {
    POSTER, BACKDROP, PROFILE, VIDEO, THUMBNAIL
}
```

**Service Layer:**
```java
@Service
public class MediaService {
    
    private final MinioClient minioClient;
    private final MediaRepository mediaRepository;
    
    /**
     * Uploads a media file.
     */
    public MediaFile uploadFile(
            MultipartFile file,
            String entityId,
            EntityType entityType,
            MediaType mediaType
    );
    
    /**
     * Downloads a media file.
     */
    public byte[] downloadFile(String mediaId);
    
    /**
     * Generates thumbnails for images.
     */
    public Map<String, String> generateThumbnails(String mediaId);
    
    /**
     * Deletes a media file.
     */
    public void deleteFile(String mediaId);
}
```

**REST API:**
```java
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    
    @PostMapping("/upload")
    public ResponseEntity<MediaFile> uploadFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam String entityId,
        @RequestParam EntityType entityType,
        @RequestParam MediaType mediaType
    );
    
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getFile(@PathVariable String id);
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable String id);
    
    @GetMapping("/entity/{entityId}")
    public ResponseEntity<List<MediaFile>> getMediaByEntity(@PathVariable String entityId);
}
```

**MinIO Configuration:**
```java
@Configuration
public class MinioConfig {
    
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
            .endpoint("http://localhost:9000")
            .credentials("minioadmin", "minioadmin")
            .build();
    }
}
```

**Tests:**
- Test file upload
- Test file download
- Test thumbnail generation
- Test file deletion
- Integration tests with MinIO TestContainer
```

---

## Frontend Development

### Prompt 13: Next.js 16 Project Setup

```
Create a Next.js 16 application with React 19 and TypeScript:

**Requirements:**
- Next.js 16.0.0
- React 19.0.2
- TypeScript 5.7.2
- Tailwind CSS 3.4.x
- Material UI (MUI) 7.3.5
- MUI Icons 7.3.5
- App Router
- Server Components
- Server Actions

**Project Structure:**
```
web-nextjs/
├── app/
│   ├── (auth)/
│   │   ├── login/
│   │   └── register/
│   ├── (dashboard)/
│   │   ├── layout.tsx
│   │   └── page.tsx
│   ├── movies/
│   │   ├── [id]/
│   │   ├── popular/
│   │   ├── top-rated/
│   │   └── upcoming/
│   ├── actors/
│   │   └── [id]/
│   ├── profile/
│   ├── search/
│   ├── layout.tsx
│   └── page.tsx
├── components/
│   ├── ui/
│   ├── movie/
│   ├── actor/
│   └── layout/
├── lib/
│   ├── api.ts
│   ├── auth.ts
│   └── utils.ts
├── hooks/
├── types/
└── public/
```

**package.json:**
```json
{
  "name": "filmpire-web",
  "version": "1.0.0",
  "engines": {
    "node": ">=22.0.0"
  },
  "dependencies": {
    "next": "16.0.0",
    "react": "19.0.0",
    "react-dom": "19.0.0",
    "@tanstack/react-query": "^5.0.0",
    "zustand": "^5.0.0",
    "zod": "^3.24.0",
    "react-hook-form": "^7.0.0",
    "@hookform/resolvers": "^3.0.0",
    "tailwindcss": "^3.4.0",
    "class-variance-authority": "^0.7.0",
    "clsx": "^2.0.0",
    "tailwind-merge": "^2.0.0",
    "lucide-react": "^0.400.0"
  },
  "devDependencies": {
    "typescript": "5.7.2",
    "@types/node": "^22.0.0",
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0"
  }
}
```

**API Client:**
```typescript
// lib/api.ts
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export async function fetchMovies(category: string, page: number = 0) {
  const response = await fetch(`${API_BASE_URL}/api/v1/movies/${category}?page=${page}`);
  if (!response.ok) throw new Error('Failed to fetch movies');
  return response.json();
}

export async function searchMovies(query: string, page: number = 0) {
  const response = await fetch(`${API_BASE_URL}/api/v1/movies/search?query=${query}&page=${page}`);
  return response.json();
}
```

**Material UI Setup:**
```typescript
// app/theme.ts
'use client';
import { createTheme } from '@mui/material/styles';

export const theme = createTheme({
  palette: {
    mode: 'dark', // or 'light'
    primary: {
      main: '#1976d2',
    },
    secondary: {
      main: '#dc004e',
    },
  },
  typography: {
    fontFamily: 'var(--font-inter)',
  },
});
```

**Components:**
- MovieCard component (MUI Card)
- MovieGrid component (MUI Grid)
- ActorCard component (MUI Card)
- SearchBar component (MUI TextField, Autocomplete)
- Navigation component (MUI AppBar, Drawer)
- Authentication forms (MUI TextField, Button)

**Features:**
- Server-side rendering for movie pages
- Client-side search with debouncing
- Infinite scrolling for movie lists
- Image optimization with next/image
- Dark mode support with MUI theming
- Responsive design with MUI Grid
```

### Prompt 14: Next.js Authentication & State Management

```
Implement authentication and state management in the Next.js app:

**Auth Context with Zustand:**
```typescript
// store/auth.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  user: User | null;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  register: (data: RegisterData) => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      login: async (email, password) => {
        const response = await fetch('/api/v1/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        });
        const { user, token } = await response.json();
        set({ user, token });
      },
      logout: () => set({ user: null, token: null }),
      register: async (data) => {
        const response = await fetch('/api/v1/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(data),
        });
        const { user, token } = await response.json();
        set({ user, token });
      },
    }),
    {
      name: 'auth-storage',
    }
  )
);
```

**React Query Setup:**
```typescript
// app/providers.tsx
'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { useState } from 'react';

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
        refetchOnWindowFocus: false,
      },
    },
  }));

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
```

**Protected Routes:**
```typescript
// middleware.ts
import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  const token = request.cookies.get('token')?.value;
  const isAuthPage = request.nextUrl.pathname.startsWith('/login') || 
                     request.nextUrl.pathname.startsWith('/register');
  const isProtectedRoute = request.nextUrl.pathname.startsWith('/profile') ||
                           request.nextUrl.pathname.startsWith('/favorites');

  if (isProtectedRoute && !token) {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  if (isAuthPage && token) {
    return NextResponse.redirect(new URL('/', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
};
```
```

---

## Mobile Development

### Prompt 15: React Native Expo Setup

```
Create a React Native 0.82 mobile application with Expo:

**Requirements:**
- React Native 0.76.3
- Expo SDK 52.0.0
- TypeScript 5.7.2
- Expo Router for navigation
- React Native Paper for UI components

**Project Initialization:**
```bash
npx create-expo-app@latest filmpire-mobile --template tabs
cd filmpire-mobile
npm install react-native-paper
npm install @react-navigation/native
npm install zustand
npm install @tanstack/react-query
```

**app.json:**
```json
{
  "expo": {
    "name": "Filmpire",
    "slug": "filmpire",
    "version": "1.0.0",
    "orientation": "portrait",
    "icon": "./assets/icon.png",
    "userInterfaceStyle": "automatic",
    "splash": {
      "image": "./assets/splash.png",
      "resizeMode": "contain",
      "backgroundColor": "#ffffff"
    },
    "ios": {
      "supportsTablet": true,
      "bundleIdentifier": "com.filmpire.app"
    },
    "android": {
      "adaptiveIcon": {
        "foregroundImage": "./assets/adaptive-icon.png",
        "backgroundColor": "#ffffff"
      },
      "package": "com.filmpire.app"
    },
    "plugins": [
      "expo-router"
    ]
  }
}
```

**Project Structure:**
```
mobile-react-native/
├── app/
│   ├── (tabs)/
│   │   ├── index.tsx
│   │   ├── search.tsx
│   │   ├── favorites.tsx
│   │   └── profile.tsx
│   ├── movie/
│   │   └── [id].tsx
│   ├── actor/
│   │   └── [id].tsx
│   ├── login.tsx
│   └── _layout.tsx
├── components/
│   ├── MovieCard.tsx
│   ├── ActorCard.tsx
│   ├── SearchBar.tsx
│   └── Loading.tsx
├── services/
│   └── api.ts
├── store/
│   └── auth.ts
├── types/
│   └── index.ts
└── constants/
    └── config.ts
```

**API Service:**
```typescript
// services/api.ts
const API_BASE_URL = 'http://localhost:8080/api/v1';

export const api = {
  async getMovies(category: string, page: number = 0) {
    const response = await fetch(`${API_BASE_URL}/movies/${category}?page=${page}`);
    return response.json();
  },
  
  async searchMovies(query: string) {
    const response = await fetch(`${API_BASE_URL}/movies/search?query=${query}`);
    return response.json();
  },
  
  async getMovieDetails(id: string) {
    const response = await fetch(`${API_BASE_URL}/movies/${id}`);
    return response.json();
  },
};
```

**Components:**
- MovieCard with image, title, rating
- MovieList with FlatList
- ActorCard component
- SearchBar with debouncing
- Loading spinner
- Error boundary

**Features:**
- Tab navigation
- Movie browsing
- Search functionality
- Movie details screen
- Actor details screen
- Favorites management
- Profile screen
- Authentication
```

---

## Testing & Quality

### Prompt 16: Comprehensive Testing Suite

```
Create a comprehensive testing suite for all microservices:

**Unit Testing (60% of tests):**

For each service, create unit tests using JUnit 5 and Mockito:

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
    
    @Nested
    @DisplayName("Get Movies By Category")
    class GetMoviesByCategory {
        
        @Test
        @DisplayName("Should return cached movies when cache hit")
        void shouldReturnCachedMovies() {
            // Given
            MovieCategory category = MovieCategory.POPULAR;
            Page<MovieDTO> cachedMovies = createMockMoviePage();
            when(cacheManager.get(anyString())).thenReturn(cachedMovies);
            
            // When
            Page<MovieDTO> result = movieService.getMoviesByCategory(category, 0);
            
            // Then
            assertThat(result).isEqualTo(cachedMovies);
            verify(movieRepository, never()).findByCategory(any(), any());
        }
        
        @Test
        @DisplayName("Should fetch from TMDB when database is empty")
        void shouldFetchFromTmdbWhenDatabaseEmpty() {
            // Test implementation
        }
        
        @Test
        @DisplayName("Should throw exception when TMDB fails")
        void shouldThrowExceptionWhenTmdbFails() {
            // Test implementation
        }
    }
}
```

**Integration Testing (30%):**

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class MovieServiceIntegrationTest {
    
    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:8.0");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldCreateAndRetrieveMovie() {
        // Test implementation
    }
}
```

**E2E Testing (10%):**

Create Playwright tests for the web application:

```typescript
// tests/e2e/movie-discovery.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Movie Discovery Flow', () => {
  test('User can search and view movie details', async ({ page }) => {
    await page.goto('http://localhost:3000');
    
    await page.fill('[data-testid="search-input"]', 'Inception');
    await page.click('[data-testid="search-button"]');
    
    await expect(page.locator('[data-testid="movie-card"]')).toHaveCount(1);
    await page.click('[data-testid="movie-card"]:first-child');
    
    await expect(page).toHaveURL(/\/movie\/\d+/);
    await expect(page.locator('h1')).toContainText('Inception');
  });
});
```

**Test Coverage Requirements:**
- Overall: 85%
- Service layer: 90%
- Controller layer: 80%
- Repository layer: 70%

**Jacoco Configuration:**
```kotlin
tasks.jacocoTestReport {
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
    }
}
```
```

### Prompt 17: CI/CD Pipeline Setup

```
Create GitHub Actions CI/CD pipelines for all components:

**Backend CI/CD (.github/workflows/backend-ci-cd.yml):**

```yaml
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
        service: [movie-service, user-service, actor-service, ai-service, media-service]
    
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

**Frontend CI/CD (.github/workflows/frontend-ci-cd.yml):**

```yaml
name: Frontend CI/CD

on:
  push:
    branches: [main, develop]
    paths:
      - 'frontend/web-nextjs/**'
  pull_request:
    branches: [main, develop]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node.js 22
        uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: frontend/web-nextjs/package-lock.json
      
      - name: Install Dependencies
        working-directory: frontend/web-nextjs
        run: npm ci
      
      - name: Run Linter
        working-directory: frontend/web-nextjs
        run: npm run lint
      
      - name: Run Tests
        working-directory: frontend/web-nextjs
        run: npm test
      
      - name: Build
        working-directory: frontend/web-nextjs
        run: npm run build
  
  deploy:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
      - uses: actions/checkout@v4
      
      - name: Deploy to Vercel
        uses: amondnet/vercel-action@v25
        with:
          vercel-token: ${{ secrets.VERCEL_TOKEN }}
          vercel-org-id: ${{ secrets.VERCEL_ORG_ID }}
          vercel-project-id: ${{ secrets.VERCEL_PROJECT_ID }}
          working-directory: frontend/web-nextjs
```
```

---

## Deployment

### Prompt 18: Docker Compose for Local Development

```
Create Docker Compose configuration for running all services locally:

**infrastructure/docker/docker-compose.yml:**

```yaml
version: '3.9'

services:
  # Databases
  postgres:
    image: postgres:16-alpine
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
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin"]
      interval: 10s
      timeout: 5s
      retries: 5

  mongodb:
    image: mongo:8.0
    container_name: filmpire-mongo
    environment:
      MONGO_INITDB_ROOT_USERNAME: admin
      MONGO_INITDB_ROOT_PASSWORD: admin123
    ports:
      - "27017:27017"
    volumes:
      - mongo_data:/data/db
    networks:
      - filmpire-network

  redis:
    image: redis:7.4-alpine
    container_name: filmpire-redis
    ports:
      - "6379:6379"
    networks:
      - filmpire-network

  # Spring Cloud Services
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

  config-server:
    build: ../../backend/config-service
    container_name: filmpire-config
    ports:
      - "8888:8888"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
    depends_on:
      eureka-server:
        condition: service_healthy
    networks:
      - filmpire-network

  api-gateway:
    build: ../../backend/api-gateway
    container_name: filmpire-gateway
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_CLOUD_CONFIG_URI: http://config-server:8888
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
    depends_on:
      - eureka-server
      - config-server
      - redis
    networks:
      - filmpire-network

  # Microservices
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
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    networks:
      - filmpire-network

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
      - postgres
      - eureka-server
    networks:
      - filmpire-network

  ai-service:
    build: ../../backend/ai-service
    container_name: filmpire-ai-service
    ports:
      - "8084:8084"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATA_MONGODB_URI: mongodb://admin:admin123@mongodb:27017/filmpire?authSource=admin
      SPRING_AI_OPENAI_API_KEY: ${OPENAI_API_KEY}
    depends_on:
      - eureka-server
      - mongodb
    networks:
      - filmpire-network

  media-service:
    build: ../../backend/media-service
    container_name: filmpire-media-service
    ports:
      - "8085:8085"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      SPRING_DATA_MONGODB_URI: mongodb://admin:admin123@mongodb:27017/filmpire?authSource=admin
    depends_on:
      - eureka-server
      - mongodb
    networks:
      - filmpire-network

volumes:
  postgres_data:
  mongo_data:

networks:
  filmpire-network:
    driver: bridge
```

**Usage:**
```bash
# Start all services
cd infrastructure/docker
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```
```

### Prompt 19: Production Deployment Configuration

```
Create production deployment configurations for Render and Vercel:

**Backend Deployment (render.yaml):**

```yaml
services:
  # Eureka Server
  - type: web
    name: filmpire-eureka
    env: docker
    dockerfilePath: ./backend/discovery-service/Dockerfile
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
    healthCheckPath: /actuator/health

  # API Gateway
  - type: web
    name: filmpire-gateway
    env: docker
    dockerfilePath: ./backend/api-gateway/Dockerfile
    envVars:
      - key: SPRING_PROFILES_ACTIVE
        value: prod
      - key: EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
        value: https://filmpire-eureka.onrender.com/eureka/
      - key: SPRING_DATA_REDIS_HOST
        fromService:
          name: redis
          property: host
    healthCheckPath: /actuator/health

  # Movie Service
  - type: web
    name: filmpire-movie-service
    env: docker
    dockerfilePath: ./backend/movie-service/Dockerfile
    envVars:
      - key: SPRING_DATA_MONGODB_URI
        fromDatabase:
          name: filmpire-mongodb
          property: connectionString

  # User Service
  - type: web
    name: filmpire-user-service
    env: docker
    dockerfilePath: ./backend/user-service/Dockerfile
    envVars:
      - key: SPRING_DATASOURCE_URL
        fromDatabase:
          name: filmpire-postgres
          property: connectionString

databases:
  - name: filmpire-postgres
    databaseName: filmpire
    user: filmpire
    plan: free

  - name: filmpire-mongodb
    plan: free
```

**Frontend Deployment (vercel.json):**

```json
{
  "buildCommand": "npm run build",
  "outputDirectory": ".next",
  "framework": "nextjs",
  "env": {
    "NEXT_PUBLIC_API_URL": "https://filmpire-gateway.onrender.com"
  },
  "rewrites": [
    {
      "source": "/api/:path*",
      "destination": "https://filmpire-gateway.onrender.com/api/:path*"
    }
  ]
}
```

**Mobile Deployment (eas.json):**

```json
{
  "cli": {
    "version": ">= 5.0.0"
  },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal"
    },
    "preview": {
      "distribution": "internal",
      "android": {
        "buildType": "apk"
      }
    },
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

**Deployment Scripts:**

```bash
#!/bin/bash
# infrastructure/scripts/deploy.sh

echo "Deploying Filmpire Microservices..."

# Deploy backend services
echo "Building Docker images..."
cd backend
docker-compose -f docker-compose.prod.yml build

echo "Pushing images to registry..."
docker-compose -f docker-compose.prod.yml push

echo "Deploying to Render..."
render deploy

# Deploy frontend
echo "Deploying Next.js to Vercel..."
cd ../frontend/web-nextjs
vercel --prod

# Deploy mobile
echo "Building mobile apps..."
cd ../mobile-react-native
eas build --platform all --profile production

echo "Deployment complete!"
```
```

---

## Summary

This comprehensive prompt guide covers all aspects of building the Filmpire microservices platform:

1. **Project Setup** - Repository structure, Gradle configuration, GitHub setup
2. **Infrastructure** - Eureka, Config Server, API Gateway
3. **Core Services** - Movie, User, Actor services with TDD
4. **Advanced Services** - AI Service with Spring AI, Media Service
5. **Frontend** - Next.js 16 with React 19, authentication, state management
6. **Mobile** - React Native 0.82 with Expo
7. **Testing** - Unit, integration, E2E tests with 85%+ coverage
8. **Deployment** - Docker Compose, Render, Vercel, Expo EAS

Each prompt is detailed enough to be used directly with Cursor AI IDE to generate production-ready code following enterprise best practices.

**Usage Tips:**
- Use prompts sequentially for systematic development
- Customize prompts based on specific requirements
- Combine multiple prompts for complex features
- Reference ARCHITECTURE.md for detailed specifications
- Always follow TDD approach: test first, then implementation

---

**Document Version:** 1.0.0  
**Last Updated:** November 14, 2025  
**Compatible with:** Cursor AI IDE, GitHub Copilot

