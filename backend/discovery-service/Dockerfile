# Multi-stage build for Discovery Service
FROM docker.io/library/eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace/app

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
RUN ./gradlew :backend:discovery-service:bootJar --no-daemon -x test

# Production stage
FROM docker.io/library/eclipse-temurin:25-jre-alpine
WORKDIR /app

# Install wget for healthcheck and add non-root user
RUN apk add --no-cache wget && \
    addgroup -S spring && \
    adduser -S spring -G spring
USER spring:spring

# Copy the built JAR
COPY --from=build /workspace/app/backend/discovery-service/build/libs/*.jar app.jar

# Expose Eureka port
EXPOSE 8761

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

