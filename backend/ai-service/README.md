# AI Service

AI-powered features including movie recommendations, voice recognition, and chat assistant.

**Port:** 8084  
**Database:** MongoDB  
**Protocols:** REST + gRPC

## Responsibilities

- Movie recommendations using AI
- Voice recognition (Whisper API)
- Chat assistant for movie queries
- Semantic search with embeddings
- Conversation history management

## Technology Stack

- Spring Boot 3.5.7
- Spring AI 1.0.0-M6
- OpenAI API / Ollama
- Spring Data MongoDB
- gRPC
- Eureka Client

## Running Locally

```bash
# Start MongoDB
docker-compose up -d mongodb

# Set OpenAI API key
export SPRING_AI_OPENAI_API_KEY="your-api-key"

# Run service
./gradlew :backend:ai-service:bootRun
```

## Docker

```bash
docker build -t filmpire/ai-service:latest .
docker run -p 8084:8084 -e SPRING_AI_OPENAI_API_KEY="your-key" filmpire/ai-service:latest
```

## API Endpoints

### REST API
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/ai/recommendations` | POST | Get movie recommendations |
| `/api/v1/ai/transcribe` | POST | Transcribe voice to text |
| `/api/v1/ai/chat` | POST | Chat with AI assistant |

### gRPC Service
- `GetRecommendations`: Get personalized recommendations
- `TranscribeVoice`: Voice to text transcription
- `ChatWithAssistant`: Conversational interface

## Database Schema

```javascript
// Conversations collection
{
  _id: ObjectId,
  userId: String,
  type: String, // RECOMMENDATION, CHAT, VOICE
  messages: Array<{
    role: String, // user, assistant, system
    content: String,
    timestamp: Date,
    metadata: Object
  }>,
  context: Object,
  createdAt: Date,
  updatedAt: Date
}

// Recommendations collection
{
  _id: ObjectId,
  userId: String,
  preferredGenres: Array<String>,
  favoriteMovies: Array<String>,
  featureWeights: Object,
  embeddingVector: Array<Number>,
  lastUpdated: Date
}
```

## AI Models

- **Chat:** GPT-4 (configurable)
- **Voice:** Whisper API
- **Embeddings:** text-embedding-ada-002

## Configuration

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        model: gpt-4
        temperature: 0.7
      embedding:
        model: text-embedding-ada-002
```

## Testing

```bash
./gradlew :backend:ai-service:test
./gradlew :backend:ai-service:jacocoTestReport
```

## OpenAPI Documentation

- Swagger UI: http://localhost:8084/swagger-ui.html
- OpenAPI Spec: http://localhost:8084/v3/api-docs

