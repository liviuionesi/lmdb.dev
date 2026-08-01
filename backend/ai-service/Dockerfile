# Multi-stage Dockerfile for AI Service
# Stage 1: Build
# Not -alpine here (unlike the other services): protoc-gen-grpc-java ships a
# precompiled native binary linked against glibc, which Alpine's musl libc
# can't run — generateProto fails with "Plugin failed with status code 1".
# The runtime stage below stays on -jre-alpine; only the build stage that
# actually invokes protoc needs glibc.
FROM docker.io/library/eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copy Gradle wrapper
COPY gradlew .
COPY gradle gradle

# Download Gradle distribution to cache it as a distinct Docker layer
RUN chmod +x gradlew && ./gradlew --version --no-daemon

# Copy Gradle configuration files
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Copy all backend modules (needed for multi-module Gradle build)
COPY backend backend

# Build the application
RUN ./gradlew :backend:ai-service:bootJar --no-daemon -x test

# Stage 2: Runtime
FROM docker.io/library/eclipse-temurin:25-jre-alpine

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/backend/ai-service/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose REST and gRPC ports
EXPOSE 8084 9084

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1

# Environment variables with defaults
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE=prod \
    EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-service:8761/eureka/ \
    POSTGRES_URL=jdbc:postgresql://postgres:5432/filmpire_ai \
    POSTGRES_USERNAME=admin \
    POSTGRES_PASSWORD=admin123 \
    REDIS_HOST=redis \
    REDIS_PORT=6379 \
    REDIS_PASSWORD=redis123 \
    OLLAMA_BASE_URL=http://ollama:11434 \
    MOVIE_SERVICE_BASE_URL=lb://movie-service

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
