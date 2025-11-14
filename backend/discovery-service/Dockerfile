FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY gradle/ ../gradle/
COPY build.gradle.kts .
COPY src/ src/
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]

