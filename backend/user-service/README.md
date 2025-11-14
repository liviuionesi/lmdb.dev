# User Service

User authentication, authorization, and profile management service.

**Port:** 8082  
**Database:** PostgreSQL

## Responsibilities

- User authentication (JWT)
- User registration and login
- Profile management
- Favorites and watchlist management
- Password encryption (BCrypt)
- Session management

## Technology Stack

- Spring Boot 3.5.8
- Spring Security
- Spring Data JPA
- PostgreSQL
- JWT (jjwt)
- Flyway (database migrations)
- Eureka Client

## Running Locally

```bash
# Start PostgreSQL
docker-compose up -d postgres

# Run service
./gradlew :backend:user-service:bootRun
```

## Docker

```bash
docker build -t filmpire/user-service:latest .
docker run -p 8082:8082 filmpire/user-service:latest
```

## API Endpoints

### Authentication
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/auth/register` | POST | Register new user |
| `/api/v1/auth/login` | POST | User login (returns JWT) |
| `/api/v1/auth/refresh` | POST | Refresh access token |
| `/api/v1/auth/logout` | POST | User logout |

### User Management
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/users/profile` | GET | Get user profile |
| `/api/v1/users/profile` | PUT | Update profile |
| `/api/v1/users/favorites` | GET | Get favorite movies |
| `/api/v1/users/favorites/{movieId}` | POST | Add to favorites |
| `/api/v1/users/favorites/{movieId}` | DELETE | Remove from favorites |
| `/api/v1/users/watchlist` | GET | Get watchlist |
| `/api/v1/users/watchlist/{movieId}` | POST | Add to watchlist |

## Database Schema

```sql
-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    account_non_locked BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- Favorites table
CREATE TABLE favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    movie_id VARCHAR(50) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Watchlist table
CREATE TABLE watchlist (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    movie_id VARCHAR(50) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Security

- **Password Hashing:** BCrypt (strength 12)
- **JWT Tokens:** 
  - Access token: 1-hour expiration
  - Refresh token: 7-day expiration
- **HTTPS:** Enforced in production
- **Rate Limiting:** Handled by API Gateway

## Testing

```bash
./gradlew :backend:user-service:test
./gradlew :backend:user-service:jacocoTestReport
```

## OpenAPI Documentation

- Swagger UI: http://localhost:8082/swagger-ui.html
- OpenAPI Spec: http://localhost:8082/v3/api-docs

