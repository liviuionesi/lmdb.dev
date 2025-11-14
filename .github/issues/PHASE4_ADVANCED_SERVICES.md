# Phase 4: Advanced Services - GitHub Issues

**Sprint:** 6-7 (2 weeks)  
**Focus:** AI Service, Media Service  
**Status:** Pending (After Phase 3)

---

## Epic

### Issue #16: [EPIC] Advanced Microservices

**Labels:** `epic`, `P1-high`, `sprint-6`, `backend`

**Description:**
Implement advanced AI-powered features (recommendations, chat, voice) and media management service (image/video storage and processing).

**Business Value:**
Delivers intelligent movie recommendations, conversational AI assistant, voice search capabilities, and robust media file management.

**User Stories:**
- #17 - Implement AI Service (Spring AI + OpenAI)
- #18 - Implement Media Service (MinIO + Image Processing)
- #19 - Implement gRPC Communication
- #20 - Implement Advanced Service Integration

**Technical Stack:**
- Spring AI 1.1.0
- OpenAI API / Ollama
- MinIO Object Storage
- gRPC 1.76.0
- Image Processing (Thumbnails)

**Story Points:** 26  
**Target Sprint:** Sprint 6-7  
**Estimated Time:** 18-22 hours

---

## User Stories / Tasks

### Issue #17: [TASK] Implement AI Service

**Labels:** `task`, `P1-high`, `sprint-6`, `backend`, `ai-service`

**Description:**
Implement AI Service with Spring AI framework for movie recommendations, conversational chat, and voice transcription using OpenAI or Ollama.

**Implementation Checklist:**
- [ ] Create domain models (Conversation, Recommendation, UserPreference)
- [ ] Implement MongoDB repositories
- [ ] Configure Spring AI with OpenAI/Ollama
- [ ] Implement movie recommendation engine
- [ ] Implement chat assistant for movie queries
- [ ] Implement voice transcription (Whisper API)
- [ ] Create semantic search with embeddings
- [ ] Implement conversation history management
- [ ] Add rate limiting for AI API calls
- [ ] Configure Eureka client
- [ ] Add health checks
- [ ] Implement OpenAPI documentation
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Create Dockerfile

**API Endpoints:**
```
POST /api/v1/ai/recommendations      # Get AI movie recommendations
POST /api/v1/ai/chat                 # Chat with AI assistant
POST /api/v1/ai/transcribe           # Voice to text transcription
GET  /api/v1/ai/conversations        # Get conversation history
POST /api/v1/ai/preferences          # Update user preferences
GET  /api/v1/ai/similar              # Find similar movies (embeddings)
```

**Domain Model:**
```java
@Document(collection = "conversations")
@Data
@Builder
public class Conversation {
    @Id
    private String id;
    private String userId;
    private ConversationType type; // RECOMMENDATION, CHAT, VOICE
    private List<Message> messages;
    private Map<String, Object> context;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

@Data
public class Message {
    private String role; // user, assistant, system
    private String content;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
}
```

**Spring AI Configuration:**
```java
@Configuration
public class AIConfig {
    @Bean
    public OpenAiChatClient chatClient(OpenAiApi openAiApi) {
        return new OpenAiChatClient(openAiApi);
    }
    
    @Bean
    public VectorStore vectorStore(MongoDBVectorStoreConfig config) {
        return new MongoDBVectorStore(config);
    }
}
```

**Recommendation Algorithm:**
```
1. Analyze user's watch history
2. Extract genre preferences
3. Calculate feature weights
4. Generate embedding vector
5. Query vector store for similar movies
6. Apply collaborative filtering
7. Rank and return top recommendations
```

**Dependencies:**
```groovy
dependencies {
    implementation project(':backend:shared-library')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
    implementation "org.springframework.ai:spring-ai-openai-spring-boot-starter:${springAiVersion}"
    implementation "org.springframework.ai:spring-ai-mongodb-store:${springAiVersion}"
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation "org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdocVersion}"
}
```

**Acceptance Criteria:**
- [ ] Recommendations API working
- [ ] Chat assistant functional
- [ ] Voice transcription working
- [ ] Conversation history stored
- [ ] Embeddings and vector search working
- [ ] Rate limiting implemented
- [ ] Registered with Eureka
- [ ] OpenAPI documentation complete
- [ ] All tests passing (80%+ coverage)
- [ ] Performance: < 2s for recommendations

**Testing Commands:**
```bash
./gradlew :backend:ai-service:bootRun
curl -X POST http://localhost:8084/api/v1/ai/recommendations -d '{"userId":"123","genres":["action","sci-fi"]}'
curl -X POST http://localhost:8084/api/v1/ai/chat -d '{"message":"Recommend me a thriller movie"}'
curl http://localhost:8084/swagger-ui.html
```

**Story Points:** 13  
**Estimated Time:** 10-12 hours

---

### Issue #18: [TASK] Implement Media Service

**Labels:** `task`, `P1-high`, `sprint-7`, `backend`, `media-service`

**Description:**
Implement Media Service for storing and serving movie posters, backdrops, actor images, and user-uploaded content using MinIO object storage.

**Implementation Checklist:**
- [ ] Create domain models (MediaFile, ImageMetadata)
- [ ] Implement MongoDB repositories for metadata
- [ ] Configure MinIO client
- [ ] Implement file upload endpoints
- [ ] Implement file retrieval endpoints
- [ ] Implement thumbnail generation
- [ ] Add image optimization (WebP conversion)
- [ ] Implement presigned URL generation
- [ ] Add file type validation
- [ ] Add virus scanning (ClamAV integration)
- [ ] Configure Eureka client
- [ ] Add health checks
- [ ] Implement OpenAPI documentation
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Create Dockerfile

**API Endpoints:**
```
POST   /api/v1/media/upload           # Upload media file
GET    /api/v1/media/{id}              # Get media file
GET    /api/v1/media/{id}/thumbnail    # Get thumbnail
DELETE /api/v1/media/{id}              # Delete media file
GET    /api/v1/media/presigned-url     # Get presigned upload URL
GET    /api/v1/media/metadata/{id}     # Get file metadata
```

**Domain Model:**
```java
@Document(collection = "media_files")
@Data
@Builder
public class MediaFile {
    @Id
    private String id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String bucket;
    private String objectKey;
    private MediaType type; // POSTER, BACKDROP, PROFILE, USER_UPLOAD
    private String entityId; // Movie ID, Actor ID, etc.
    private ImageMetadata metadata;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime lastAccessedAt;
}

@Data
public class ImageMetadata {
    private Integer width;
    private Integer height;
    private String format;
    private Boolean hasTransparency;
    private Long thumbnailSize;
}
```

**MinIO Configuration:**
```java
@Configuration
public class MinIOConfig {
    @Bean
    public MinioClient minioClient(
        @Value("${minio.url}") String url,
        @Value("${minio.access-key}") String accessKey,
        @Value("${minio.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
            .endpoint(url)
            .credentials(accessKey, secretKey)
            .build();
    }
}
```

**Image Processing:**
- Generate thumbnails (200x300)
- Convert to WebP format
- Optimize file size
- Extract metadata

**Dependencies:**
```groovy
dependencies {
    implementation project(':backend:shared-library')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'
    implementation 'io.minio:minio:8.5.7'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'net.coobird:thumbnailator:0.4.20'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

**Acceptance Criteria:**
- [ ] File upload working
- [ ] File retrieval working
- [ ] Thumbnail generation functional
- [ ] Image optimization working
- [ ] Presigned URLs working
- [ ] File validation implemented
- [ ] Virus scanning configured
- [ ] Registered with Eureka
- [ ] OpenAPI documentation complete
- [ ] All tests passing (85%+ coverage)
- [ ] Performance: < 500ms for upload

**Testing Commands:**
```bash
./gradlew :backend:media-service:bootRun
curl -F "file=@poster.jpg" http://localhost:8085/api/v1/media/upload
curl http://localhost:8085/api/v1/media/abc123
curl http://localhost:8085/swagger-ui.html
```

**Story Points:** 8  
**Estimated Time:** 6-8 hours

---

### Issue #19: [TASK] Implement gRPC Communication

**Labels:** `task`, `P2-medium`, `sprint-7`, `backend`, `grpc`

**Description:**
Implement gRPC endpoints for high-performance service-to-service communication between Movie, AI, and Media services.

**Implementation Checklist:**
- [ ] Define .proto files for each service
- [ ] Generate Java code from .proto files
- [ ] Implement gRPC server in Movie Service
- [ ] Implement gRPC server in AI Service
- [ ] Implement gRPC server in Media Service
- [ ] Implement gRPC clients
- [ ] Add interceptors for auth and logging
- [ ] Configure load balancing
- [ ] Add health checks for gRPC
- [ ] Write gRPC integration tests
- [ ] Document gRPC contracts
- [ ] Add performance benchmarks

**Proto Definitions:**

**movie-service.proto:**
```protobuf
syntax = "proto3";
package com.filmpire.movie;

service MovieService {
  rpc GetMovie(MovieRequest) returns (MovieResponse);
  rpc SearchMovies(SearchRequest) returns (MovieListResponse);
  rpc GetRecommendations(RecommendationRequest) returns (MovieListResponse);
}

message MovieRequest {
  int64 id = 1;
}

message MovieResponse {
  int64 id = 1;
  string title = 2;
  string overview = 3;
  double rating = 4;
}
```

**ai-service.proto:**
```protobuf
syntax = "proto3";
package com.filmpire.ai;

service AIService {
  rpc GetRecommendations(RecommendationRequest) returns (RecommendationResponse);
  rpc AnalyzePreferences(PreferenceRequest) returns (PreferenceResponse);
}
```

**media-service.proto:**
```protobuf
syntax = "proto3";
package com.filmpire.media;

service MediaService {
  rpc UploadFile(stream FileChunk) returns (UploadResponse);
  rpc GetFile(FileRequest) returns (stream FileChunk);
}
```

**Dependencies:**
```groovy
dependencies {
    implementation "io.grpc:grpc-netty-shaded:${grpcVersion}"
    implementation "io.grpc:grpc-protobuf:${grpcVersion}"
    implementation "io.grpc:grpc-stub:${grpcVersion}"
    implementation 'net.devh:grpc-spring-boot-starter:2.15.0'
}
```

**Acceptance Criteria:**
- [ ] gRPC servers running on all services
- [ ] gRPC clients can communicate
- [ ] Interceptors working
- [ ] Load balancing configured
- [ ] Health checks passing
- [ ] All tests passing
- [ ] Performance: < 50ms latency
- [ ] Documentation complete

**Testing Commands:**
```bash
./gradlew generateProto
./gradlew :backend:movie-service:bootRun
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext -d '{"id":550}' localhost:9090 com.filmpire.movie.MovieService/GetMovie
```

**Story Points:** 5  
**Estimated Time:** 4-5 hours

---

### Issue #20: [TASK] Advanced Service Integration Testing

**Labels:** `task`, `P2-medium`, `sprint-7`, `backend`, `testing`

**Description:**
Implement comprehensive integration tests for AI and Media services with other microservices.

**Implementation Checklist:**
- [ ] Movie-AI integration tests
- [ ] Movie-Media integration tests
- [ ] User-AI preference tests
- [ ] gRPC communication tests
- [ ] Performance benchmarks
- [ ] Load testing scenarios
- [ ] Error handling tests
- [ ] Document test results

**Test Scenarios:**
1. **Movie-AI Integration:**
   - Get recommendations based on movie
   - Chat about specific movie
   - Voice search for movies

2. **Movie-Media Integration:**
   - Upload movie poster
   - Retrieve movie images
   - Generate thumbnails

3. **User-AI Integration:**
   - Personalized recommendations
   - Save conversation history
   - Update preferences

**Acceptance Criteria:**
- [ ] All integration tests passing
- [ ] gRPC tests passing
- [ ] Performance benchmarks met
- [ ] Load tests successful
- [ ] Documentation complete

**Testing Commands:**
```bash
./gradlew integrationTest
./gradlew performanceTest
./gradlew loadTest
```

**Story Points:** 5  
**Estimated Time:** 4-5 hours

---

## Definition of Done

For all tasks in this phase:

✅ **Code Quality**
- Clean Code principles
- SOLID principles
- AI best practices applied
- SonarQube quality gate passed

✅ **Testing**
- Unit tests (80%+ coverage)
- Integration tests
- Performance tests
- Load tests

✅ **Documentation**
- JavaDoc complete
- OpenAPI specs
- gRPC proto docs
- AI model documentation

✅ **Deployment**
- Dockerfile created
- Environment variables documented
- Health checks passing
- Registered with Eureka

---

## Success Metrics

- [ ] AI recommendations working
- [ ] Media storage operational
- [ ] gRPC communication functional
- [ ] Performance targets met
- [ ] All integration tests passing

---

**Phase Status:** Pending  
**Dependencies:** Phase 3 (Core Services)  
**Estimated Duration:** 2 weeks  
**Total Story Points:** 31

