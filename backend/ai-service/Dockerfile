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
# Also not -alpine (#68): Vosk's speech-to-text native library (JNA/JNI, C++)
# is a precompiled glibc/libstdc++ binary, same class of problem as the
# protoc-gen-grpc-java issue above — Alpine's musl libc can't dlopen it
# ("Error loading shared library libstdc++.so.6"), confirmed live.
FROM docker.io/library/eclipse-temurin:25-jre

# wget for the HEALTHCHECK below — unlike -alpine's busybox, this base image
# doesn't bundle it. curl/unzip are for the Vosk model download below.
# libstdc++6 (Vosk's own native-library dependency, see above) IS already
# present, so nothing to add for that. Non-root user in the same layer
# (Debian-based image — no addgroup/adduser -S flags).
RUN apt-get update && apt-get install -y --no-install-recommends wget curl unzip \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

# Bake in the offline Vosk speech-to-text model (#151) - previously only a
# manual local-dev step (infrastructure/scripts/download-vosk-model.sh),
# meaning voice control silently had no model to load at all on either
# cloud deploy (SpeechToTextService loads it lazily, so ai-service itself
# starts fine and only fails once a request actually needs it). Baked in
# here so it's present identically everywhere, matching VOSK_MODEL_PATH's
# own default (./models/vosk-model-small-en-us-0.15, relative to this
# WORKDIR) — same URL infrastructure/scripts/download-vosk-model.sh uses.
#
# The archive is pinned by SHA-256 and verified before it is unpacked. Vosk
# loads native code out of this directory via JNI, so unverified content here
# executes inside the service process — a compromised upstream, a hijacked
# mirror, or a MITM on the build network would otherwise be baked into the
# image and pushed to ghcr.io as a normal release. `--fail` matters for the
# same reason: without it curl writes an HTTP error page to the output file
# and the build carries on treating it as an archive.
ARG VOSK_MODEL_SHA256=30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498
RUN curl -fsSL -o /tmp/vosk-model.zip https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip \
    && echo "${VOSK_MODEL_SHA256}  /tmp/vosk-model.zip" | sha256sum -c - \
    && unzip -q /tmp/vosk-model.zip -d models \
    && rm /tmp/vosk-model.zip

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

ENV JAVA_OPTS="${JAVA_OPTS}" \
    SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE}" \
    EUREKA_CLIENT_SERVICEURL_DEFAULTZONE="${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE}" \
    SPRING_CONFIG_IMPORT="${SPRING_CONFIG_IMPORT}"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
