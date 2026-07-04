# Maven Repo

- Public and private repositories with per-user read/write grants
- Access tokens for Gradle/Maven, scoped to specific repositories
- Web UI to browse artifacts, search packages, and manage users, repos and tokens
- H2 (embedded) or PostgreSQL; local filesystem or S3-compatible storage

## Install

Docker:

```sh
docker run -d -p 8080:8080 \
  -v maven-repo-data:/app/data \
  -e SESSION_SECRET=change-me-to-a-long-random-value \
  ghcr.io/invalidjoker/maven-repo:latest
```

Or Docker Compose:

```yaml
services:
  maven-repo:
    image: ghcr.io/invalidjoker/maven-repo:latest
    ports:
      - "8080:8080"
    environment:
      SESSION_SECRET: change-me-to-a-long-random-value
    volumes:
      - maven-data:/app/data
    restart: unless-stopped

volumes:
  maven-data:
```

The initial admin password is generated and printed once to the logs on first boot:

```sh
docker logs <container> | grep -A6 "admin account"
```

Open http://localhost:8080 and sign in as `admin`.

## Configuration

All settings are optional environment variables.

| Variable | Default | Description |
| --- | --- | --- |
| `SESSION_SECRET` | (insecure default) | Key used to sign session cookies. Set this in production. |
| `ADMIN_USERNAME` | `admin` | Username of the seeded admin. |
| `ADMIN_RESET_PASSWORD` | `false` | Set to `true` for one boot to regenerate and reprint the admin password. |
| `DATABASE_TYPE` | `h2` | `h2` (embedded file) or `postgres`. |
| `DATABASE_HOST` / `DATABASE_PORT` / `DATABASE_NAME` / `DATABASE_USER` / `DATABASE_PASSWORD` | | PostgreSQL connection settings. |
| `STORAGE_TYPE` | `local` | `local` (data volume) or `s3`. |
| `S3_BUCKET` / `S3_REGION` / `S3_ACCESS_KEY_ID` / `S3_SECRET_ACCESS_KEY` | | S3 storage settings. |
| `S3_ENDPOINT` | (AWS) | Custom endpoint for S3-compatible stores (MinIO, R2, Backblaze). |

Data (H2 database, local artifacts, instance branding) is stored under `/app/data` — keep it on a volume.

Behind a reverse proxy, forward `X-Forwarded-Proto` and `X-Forwarded-Host` so redirects and same-origin checks work.

## Usage

```kotlin
repositories {
    maven {
        url = uri("http://localhost:8080/maven/releases")
        credentials {
            username = "your-username"
            password = "your-access-token"
        }
    }
}
```

## Build from source

Requires JDK 21 and [bun](https://bun.sh).

```sh
./gradlew :backend:run          # run locally on :8080
./gradlew :backend:shadowJar    # fat jar at backend/build/libs/backend-all.jar
docker build -t maven-repo .    # container image
```
