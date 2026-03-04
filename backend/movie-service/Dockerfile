# Multi-stage Dockerfile for Movie Service
# Stage 1: Build
FROM docker.io/library/eclipse-temurin:25-jdk-alpine AS build

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
RUN ./gradlew :backend:movie-service:bootJar --no-daemon -x test

# Stage 2: Runtime
FROM docker.io/library/eclipse-temurin:25-jre-alpine

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/backend/movie-service/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose movie service port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# Environment variables with defaults
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE=prod \
    EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-service:8761/eureka/ \
    MONGODB_URI=mongodb://admin:admin123@mongodb:27017/filmpire?authSource=admin \
    REDIS_HOST=redis \
    REDIS_PORT=6379 \
    REDIS_PASSWORD=redis123

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

