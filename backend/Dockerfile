# syntax=docker/dockerfile:1
# Code generation needs a real schema. This database exists only during the build and
# never connects to an installed Ready2Race instance.
FROM --platform=$BUILDPLATFORM postgres:17-bookworm AS build
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend/build.gradle.kts ./backend/build.gradle.kts
COPY backend/src/main ./backend/src/main
COPY backend/docker/build.sh /usr/local/bin/build-ready2race
RUN --mount=type=cache,target=/root/.gradle sh /usr/local/bin/build-ready2race

FROM eclipse-temurin:17-jre-jammy AS runtime
LABEL org.opencontainers.image.licenses="GPL-3.0-only"
RUN apt-get update && apt-get install -y --no-install-recommends curl fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 ready2race \
    && useradd --uid 10001 --gid ready2race --no-create-home ready2race \
    && mkdir -p /app /data/static \
    && chown -R ready2race:ready2race /app /data
WORKDIR /app
COPY --from=build /out/app.jar ./app.jar
COPY LICENSE ./LICENSE
USER ready2race
ENV HTTP_HOST=0.0.0.0 HTTP_PORT=8080 STATIC_FILES_PATH=/data/static
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
