# Multi-stage build for Discovery Service
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace/app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Copy backend modules
COPY backend/discovery-service backend/discovery-service

# Build the application
RUN chmod +x gradlew
RUN ./gradlew :backend:discovery-service:bootJar --no-daemon -x test

# Production stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Add non-root user
RUN addgroup -S spring && adduser -S spring -G spring
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

