# syntax=docker/dockerfile:1

# ---- Build stage: bundles the bun-built frontend into the backend shadow jar ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Bun is required by the frontend Gradle build (frontend/build.gradle.kts).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL https://bun.sh/install | bash
ENV BUN_INSTALL=/root/.bun
ENV PATH=/root/.bun/bin:${PATH}

# Copy the whole project (see .dockerignore for exclusions) and build the fat jar.
# The Ktor plugin names the fat jar `backend-all.jar`; normalize it to app.jar.
COPY . .
RUN chmod +x gradlew \
    && ./gradlew :backend:buildFatJar --no-daemon \
    && cp backend/build/libs/backend-all.jar app.jar

# ---- Runtime stage: slim JRE with just the application jar ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/app.jar app.jar
RUN mkdir -p /app/data && chown -R app:app /app

USER app
EXPOSE 8080

# Persisted state: H2 database file and the repository artifact tree both live here
# (see application.yaml defaults: database.h2.file and repository.storagePath).
VOLUME ["/app/data"]

ENTRYPOINT ["java", "-jar", "app.jar"]
