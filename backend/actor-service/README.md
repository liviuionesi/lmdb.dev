# Actor Service

Actor information and filmography management service.

**Port:** 8083  
**Database:** PostgreSQL

## Responsibilities

- Actor profile management
- Filmography tracking
- Actor-movie relationships
- Actor search
- Cast and crew information

## Technology Stack

- Spring Boot 3.5.8
- Spring Data JPA
- PostgreSQL
- Flyway (database migrations)
- Eureka Client
- MapStruct (DTO mapping)

## Running Locally

```bash
# Start PostgreSQL
docker-compose up -d postgres

# Run service
./gradlew :backend:actor-service:bootRun
```

## Docker

```bash
docker build -t filmpire/actor-service:latest .
docker run -p 8083:8083 filmpire/actor-service:latest
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/actors/{id}` | GET | Get actor details |
| `/api/v1/actors/{id}/movies` | GET | Get actor's filmography |
| `/api/v1/actors/search` | GET | Search actors by name |

## Database Schema

```sql
-- Actors table
CREATE TABLE actors (
    id UUID PRIMARY KEY,
    tmdb_id VARCHAR(50) UNIQUE,
    name VARCHAR(255) NOT NULL,
    biography TEXT,
    birth_date DATE,
    birth_place VARCHAR(255),
    death_date DATE,
    profile_path VARCHAR(255),
    popularity DECIMAL(10,2),
    known_for_department VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Movie cast relationships
CREATE TABLE movie_cast (
    id BIGSERIAL PRIMARY KEY,
    actor_id UUID REFERENCES actors(id),
    movie_id VARCHAR(50) NOT NULL,
    character_name VARCHAR(255),
    cast_order INTEGER,
    UNIQUE(actor_id, movie_id)
);
```

## Testing

```bash
./gradlew :backend:actor-service:test
./gradlew :backend:actor-service:jacocoTestReport
```

## OpenAPI Documentation

- Swagger UI: http://localhost:8083/swagger-ui.html
- OpenAPI Spec: http://localhost:8083/v3/api-docs

