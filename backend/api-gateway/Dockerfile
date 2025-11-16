# Multi-stage Dockerfile for API Gateway
# Stage 1: Build
FROM gradle:8.12.0-jdk25 AS build

WORKDIR /app

# Copy Gradle configuration files
COPY settings.gradle .
COPY gradle.properties .
COPY build.gradle .

# Copy shared library first (dependency)
COPY backend/shared-library ./backend/shared-library

# Copy api-gateway source
COPY backend/api-gateway/build.gradle ./backend/api-gateway/
COPY backend/api-gateway/src ./backend/api-gateway/src

# Build the application
RUN gradle :backend:api-gateway:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/backend/api-gateway/build/libs/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose gateway port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Environment variables with defaults
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    SPRING_PROFILES_ACTIVE=prod \
    EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-service:8761/eureka/ \
    REDIS_HOST=redis \
    REDIS_PORT=6379

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

