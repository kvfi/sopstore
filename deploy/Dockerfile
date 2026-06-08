# sopstore — multi-stage build.
# Stage 1: Gradle build under JDK 25.
# Stage 2: slim JRE 25 runtime; pinned by digest.
#
# Build:    docker build -t sopstore:0.1.0 -f deploy/Dockerfile .
# Sign:     cosign sign --key cosign.key sopstore:0.1.0
# Air-gap:  docker save sopstore:0.1.0 -o sopstore-0.1.0.tar
#
# Digest refresh: docker pull eclipse-temurin:25-jdk
#                 docker inspect --format='{{index .RepoDigests 0}}' eclipse-temurin:25-jdk

FROM eclipse-temurin:25-jdk@sha256:PLACEHOLDER_JDK_DIGEST_REFRESH_BEFORE_RELEASE AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle ./gradle
COPY config ./config
# Pre-fetch deps for layer caching.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies || true
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:25-jre@sha256:PLACEHOLDER_JRE_DIGEST_REFRESH_BEFORE_RELEASE
LABEL org.opencontainers.image.title="sopstore" \
      org.opencontainers.image.description="Enterprise SOP management platform" \
      org.opencontainers.image.vendor="RightCrowd" \
      org.opencontainers.image.source="https://github.com/rightcrowd/sopstore"

ENV JAVA_TOOL_OPTIONS="--enable-preview -XX:MaxRAMPercentage=75 -XX:+UseZGC"
RUN useradd --system --uid 10001 --home /app sopstore
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
