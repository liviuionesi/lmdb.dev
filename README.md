# Filmpire Microservices Platform

Enterprise-grade microservices platform for movie discovery and management, demonstrating best practices in software architecture, development, and deployment.

## 🏗️ Architecture

This project implements a comprehensive microservices architecture with:

- **8 Backend Microservices** (Spring Boot 3.5.8-SNAPSHOT, Java 25, Gradle Groovy DSL)
- **2 Frontend Applications** (Next.js 16, React Native 0.76.3)
- **Hybrid Database Strategy** (PostgreSQL + MongoDB)
- **Spring Cloud Infrastructure** (Eureka, Config Server, API Gateway)
- **Spring AI Integration** (Voice recognition, recommendations)
- **Complete CI/CD Pipeline** (GitHub Actions)

### Development Standards (Spring Boot 3.5.x + Java 25)

**Core Principles:**
- ✅ **Constructor Injection ONLY** - NO field injection
- ✅ **Java Records for DTOs** - NO mutable classes
- ✅ **RestClient or @HttpExchange** - NO RestTemplate
- ✅ **JUnit 5 (Jupiter) exclusively** - JUnit 4 FORBIDDEN
- ✅ **Testcontainers with @ServiceConnection** - NO H2
- ✅ **ReentrantLock** - NO synchronized blocks (Virtual Threads)
- ✅ **Version Management via gradle.properties** - Single source of truth

## 📋 Prerequisites

- **Java 25** (via SDKMAN)
- **Gradle 9.2.0** (via Gradle Wrapper)
- **Node.js 24.11.1 LTS** (via NVM)
- **Docker/Podman** (for containerization)
- **PostgreSQL 17** (via Docker)
- **MongoDB 8.0** (via Docker)
- **Redis 7.4** (via Docker)

## 🚀 Quick Start

### 1. Clone Repository

```bash
git clone https://github.com/yourusername/filmpire-microservices.git
cd filmpire-microservices
```

### 2. Start Infrastructure

```bash
cd infrastructure/docker
docker-compose up -d
```

### 3. Build Backend Services

```bash
./gradlew clean build
```

### 4. Run Services

```bash
# Start Discovery Service
cd backend/discovery-service
./gradlew bootRun

# Start Config Service (in new terminal)
cd backend/config-service
./gradlew bootRun

# Start API Gateway (in new terminal)
cd backend/api-gateway
./gradlew bootRun

# Start other services similarly...
```

### 5. Start Frontend

```bash
# Web Application
cd frontend/web-nextjs
npm install
npm run dev

# Mobile Application
cd frontend/mobile-react-native
npm install
npm start
```

## 📁 Project Structure

```
filmpire-microservices/
├── backend/              # Spring Boot microservices
│   ├── api-gateway/      # API Gateway (Port 8080)
│   ├── discovery-service/ # Eureka Server (Port 8761)
│   ├── config-service/   # Config Server (Port 8888)
│   ├── movie-service/    # Movie Service (Port 8081)
│   ├── user-service/     # User Service (Port 8082)
│   ├── actor-service/    # Actor Service (Port 8083)
│   ├── ai-service/       # AI Service (Port 8084)
│   ├── media-service/    # Media Service (Port 8085)
│   └── shared-library/   # Shared utilities
├── frontend/
│   ├── web-nextjs/       # Next.js 16 web app
│   └── mobile-react-native/ # React Native mobile app
├── infrastructure/        # Docker, Kubernetes configs
├── docs/                 # Documentation
└── tools/                # Utility scripts
```

## 🛠️ Technology Stack

### Backend (Versions in gradle.properties)
- **Java** 25 (via SDKMAN)
- **Gradle** 9.2.0 (Groovy DSL via wrapper)
- **Spring Boot** 3.5.8-SNAPSHOT
- **Spring Cloud** 2025.0.0
- **Spring AI** 1.0.0-SNAPSHOT
- **PostgreSQL** 17-alpine
- **MongoDB** 8.0
- **Redis** 7.4-alpine

### Testing Stack
- **JUnit** 5.11.3 (Jupiter ONLY - JUnit 4 FORBIDDEN)
- **Mockito** 5.19.0
- **Testcontainers** 1.21.2 (with @ServiceConnection)
- **AssertJ** (fluent assertions)
- **JaCoCo** 0.8.14 (85% coverage minimum)

### Frontend
- **Next.js** 16.0.0
- **React** 19.0.2
- **Material UI** 7.3.5
- **TypeScript** 5.7.2
- **React Native** 0.76.3
- **Expo SDK** 52.0.0

## 📚 Documentation

- [Architecture Document](./docs/architecture/ARCHITECTURE.md) - Complete system architecture
- [Gradle Build Setup](./docs/architecture/GRADLE_BUILD_SETUP.md) - Build configuration & version management
- [Spring Boot Development Rules](./.cursorrules/spring-boot-development.mdc) - Development standards
- [API Documentation](./docs/api/) - OpenAPI specifications
- [Port Mapping](./docs/architecture/PORT_MAPPING.md) - Service ports reference

## 🧪 Testing

**All tests run via Cursor IDE Test Runner (CodeLens "Run Test" buttons)**

```bash
# Run all backend tests (via terminal - for CI/CD)
./gradlew test

# Run specific service tests
cd backend/movie-service
./gradlew test

# Run with coverage report
./gradlew test jacocoTestReport

# Run frontend tests
cd frontend/web-nextjs
npm test
```

**Testing Requirements:**
- ✅ JUnit 5 (Jupiter) exclusively - NO JUnit 4
- ✅ Minimum 85% code coverage
- ✅ Testcontainers with `@ServiceConnection` for integration tests
- ✅ NO H2 database - use real databases via Testcontainers
- ✅ `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` in all services

## 🚢 Deployment

See [Deployment Guide](./docs/guides/DEPLOYMENT.md) for detailed deployment instructions.

- **Backend**: Render/Railway
- **Frontend**: Vercel
- **Mobile**: Expo EAS

## 📝 License

This project is created for portfolio demonstration purposes.

## 👤 Author

Filmpire Development Team

---

**Status**: 🚧 In Development  
**Version**: 1.0.0-SNAPSHOT

