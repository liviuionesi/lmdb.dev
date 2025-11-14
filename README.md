# Filmpire Microservices Platform

Enterprise-grade microservices platform for movie discovery and management, demonstrating best practices in software architecture, development, and deployment.

## 🏗️ Architecture

This project implements a comprehensive microservices architecture with:

- **8 Backend Microservices** (Spring Boot 3.5.7, Java 25)
- **2 Frontend Applications** (Next.js 16, React Native 0.76.3)
- **Hybrid Database Strategy** (PostgreSQL + MongoDB)
- **Spring Cloud Infrastructure** (Eureka, Config Server, API Gateway)
- **Spring AI Integration** (Voice recognition, recommendations)
- **Complete CI/CD Pipeline** (GitHub Actions)

## 📋 Prerequisites

- **Java 25** (via SDKMAN)
- **Gradle 8.11.1** (via SDKMAN)
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

### Backend
- Java 25
- Spring Boot 3.5.7
- Spring Cloud 2024.0.0
- Spring AI 1.0.0-M6
- PostgreSQL 17
- MongoDB 8.0
- Redis 7.4

### Frontend
- Next.js 16.0.0
- React 19.0.2
- Material UI 7.3.5
- TypeScript 5.7.2
- React Native 0.76.3
- Expo SDK 52.0.0

## 📚 Documentation

- [Architecture Document](./ARCHITECTURE.md) - Complete system architecture
- [Cursor Prompts](./CURSOR_PROMPTS.md) - Development prompts guide
- [API Documentation](./docs/api/) - OpenAPI specifications
- [Setup Guide](./docs/guides/SETUP.md) - Detailed setup instructions

## 🧪 Testing

```bash
# Run all backend tests
./gradlew test

# Run specific service tests
cd backend/movie-service
./gradlew test

# Run frontend tests
cd frontend/web-nextjs
npm test
```

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

