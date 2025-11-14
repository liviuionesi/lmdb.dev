# Movie Service

Core service for movie catalog, search, and discovery operations.

**Port:** 8081  
**Database:** MongoDB

## Responsibilities

- Movie catalog management
- Search and discovery
- Genre management
- TMDB API integration
- Caching with Redis
- Movie recommendations

## Technology Stack

- Spring Boot 3.5.8
- Spring Data MongoDB
- Spring Cache (Redis)
- Eureka Client
- MapStruct (DTO mapping)
- Lombok

## Running Locally

```bash
# Start MongoDB
docker-compose up -d mongodb redis

# Run service
./gradlew :backend:movie-service:bootRun
```

## Docker

```bash
docker build -t filmpire/movie-service:latest .
docker run -p 8081:8081 filmpire/movie-service:latest
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/movies/popular` | GET | Get popular movies |
| `/api/v1/movies/top-rated` | GET | Get top-rated movies |
| `/api/v1/movies/upcoming` | GET | Get upcoming movies |
| `/api/v1/movies/search` | GET | Search movies |
| `/api/v1/movies/{id}` | GET | Get movie details |
| `/api/v1/movies/discover` | GET | Discover movies by genre |
| `/api/v1/genres` | GET | Get all genres |

## Database Schema

```javascript
{
  _id: ObjectId,
  tmdbId: String,
  title: String,
  originalTitle: String,
  overview: String,
  releaseDate: Date,
  runtime: Number,
  voteAverage: Number,
  voteCount: Number,
  popularity: Number,
  posterPath: String,
  backdropPath: String,
  genres: Array<{id: String, name: String}>,
  cast: Array<CastMember>,
  crew: Array<CrewMember>,
  videos: Array<Video>,
  productionCompanies: Array<Company>,
  spokenLanguages: Array<String>,
  status: String,
  createdAt: Date,
  updatedAt: Date
}
```

## Testing

```bash
./gradlew :backend:movie-service:test
./gradlew :backend:movie-service:jacocoTestReport
```

## OpenAPI Documentation

- Swagger UI: http://localhost:8081/swagger-ui.html
- OpenAPI Spec: http://localhost:8081/v3/api-docs

