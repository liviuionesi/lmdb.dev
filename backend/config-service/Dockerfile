# Multi-stage Dockerfile for Config Service
# Stage 1: Build
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace/app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Copy all backend services (needed for multi-module build)
COPY backend backend

# Build the config-service
RUN chmod +x gradlew
RUN ./gradlew :backend:config-service:bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Install wget for healthcheck
RUN apk add --no-cache wget && \
    addgroup -S spring && \
    adduser -S spring -G spring

USER spring:spring

# Copy the built JAR from build stage
COPY --from=build /workspace/app/backend/config-service/build/libs/*.jar app.jar

# Expose port
EXPOSE 8888

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

