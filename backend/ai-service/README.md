# AI Service

AI-powered features: catalog-grounded movie recommendations, a chat assistant, and semantic search over user taste profiles (#36, ARCHITECTURE.md §3.7, ADR-012).

**Port:** 8084 (REST), 9084 (gRPC)
**Database:** PostgreSQL + pgvector (`filmpire_ai`)
**Protocols:** REST + gRPC
**Model provider:** Ollama only (local, $0 — ADR-004). No OpenAI/paid API key anywhere in this service.

## Responsibilities

- Movie recommendations computed from Filmpire's own catalog (movie-service), never proxied from TMDB's own recommendation endpoint
- Chat assistant with persisted conversation history
- Semantic search: ANN query over user taste embeddings (pgvector `<=>` operator, HNSW index)
- Offline Speech-to-Text Voice Recognition powered by self-hosted Vosk engine and embedded small English model (replacing cloud Whisper for $0 cost) (#68, #151)

## Why PostgreSQL, not MongoDB

See [ADR-012](../../docs/architecture/adr/012-ai-service-postgresql-pgvector.md). In short: conversation history is user-owned and not re-derivable (unlike the movie/actor catalog), so it needs Flyway + `ddl-auto: validate` the same way user-service protects accounts and favorites — MongoDB's schemaless drift tolerance (ADR-011) would be unsafe here.

## Running Locally

```bash
# Start infra (Postgres must be pgvector/pgvector:pg17 — see docker-compose.yml)
docker-compose up -d postgres redis ollama

# Pull the models this service is configured for (one-time; ~2.3GB total)
docker exec -it filmpire-ollama ollama pull llama3.2
docker exec -it filmpire-ollama ollama pull nomic-embed-text

./gradlew :backend:ai-service:bootRun
```

## Docker

```bash
docker build -f backend/ai-service/Dockerfile -t filmpire/ai-service:local .
docker run -p 8084:8084 -p 9084:9084 filmpire/ai-service:local
```

## API

### REST — `/api/v1/ai`
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/recommendations` | POST | Ranked, explained recommendations from Filmpire's catalog |
| `/chat` | POST | Continue (or start) a conversation with the assistant |
| `/search/semantic` | GET | Nearest taste-profile neighbours to a free-text query |
| `/speech-to-text` | POST | Transcribe WAV audio using self-hosted Vosk engine (#68) |

All recommendations/chat/semantic search features are scoped to a `userId`. Voice control uses `/speech-to-text` (public endpoint at gateway).

### 🎙️ Supported Voice Commands
Users can speak into the microphone icon in the UI:
- **Genre / Category**: `"Popular"`, `"Top rated"`, `"Upcoming"`, or any genre (`"Action"`, `"Comedy"`, `"Drama"`, etc.)
- **Search**: `"Search [Movie Title]"` (e.g. `"Search Inception"`)
- **Theme**: `"Dark mode"` / `"Light mode"`
- **Auth**: `"Log out"` / `"Sign out"`

### gRPC — `ai_service.proto`
- `GetRecommendations` — same logic as the REST endpoint
- `ChatWithAssistant` — same logic as the REST endpoint

## Database Schema

Flyway-managed (`src/main/resources/db/migration`), relational — see ADR-012 for why this isn't MongoDB:

```sql
conversations(id, user_id, type, created_at, updated_at)
messages(id, conversation_id, role, content, timestamp, metadata jsonb)
user_taste_profiles(user_id, embedding vector(768), feature_weights jsonb, last_updated)
```

`user_id` has no foreign key — ADR-002 forbids cross-service joins; user-service owns that identity.

## Testing

```bash
./gradlew :backend:ai-service:test
./gradlew :backend:ai-service:jacocoTestReport
```

`AiServiceIntegrationTest` runs against a real `pgvector/pgvector:pg17` Testcontainer (proving the Flyway schema and `ddl-auto: validate` actually work) with `ChatModel`/`EmbeddingModel` replaced by Mockito mocks (no Ollama in CI) and movie-service stubbed with WireMock.

## OpenAPI Documentation

- Swagger UI: http://localhost:8084/swagger-ui.html
- OpenAPI Spec: http://localhost:8084/v3/api-docs
