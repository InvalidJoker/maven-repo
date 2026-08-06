# Artifact Forge

> [!IMPORTANT]
> This is self-hosted software. There is **no public/hosted instance** — you must run your own (see [Install](#install)).
> The `repo.koder.wtf` links in the docs are a personal demo you can read from, but not publish to.

- Maven repositories, Docker/OCI registries **and** npm registries in one instance, sharing users, permissions and tokens
- Public and private repositories with per-user read/write grants
- Access tokens for Gradle/Maven, `docker login` and `.npmrc`, scoped to specific repositories
- Web UI to browse artifacts, search packages, inspect images, tags and package versions, and manage users, repos and tokens
- H2 (embedded) or PostgreSQL database
- Local filesystem or S3-compatible storage

> Live demo: https://repo.koder.wtf/#/ (example repository: https://repo.koder.wtf/#/repo/releases)

## Install

Docker:

```sh
docker run -d -p 8080:8080 \
  -v artifact-forge-data:/app/data \
  -e SESSION_SECRET=change-me-to-a-long-random-value \
  ghcr.io/invalidjoker/artifact-forge:latest
```

Or Docker Compose:

```yaml
services:
  artifact-forge:
    image: ghcr.io/invalidjoker/artifact-forge:latest
    ports:
      - "8080:8080"
    environment:
      SESSION_SECRET: change-me-to-a-long-random-value
    volumes:
      - forge-data:/app/data
    restart: unless-stopped

volumes:
  forge-data:
```

The initial admin password is generated and printed once to the logs on first boot:

```sh
docker logs <container> | grep -A6 "admin account"
```

Open http://localhost:8080 and sign in as `admin`.

## Upgrading from Maven Repo

The project was renamed to Artifact Forge, and the image and two defaults moved with it:

- **Image**: `ghcr.io/invalidjoker/maven-repo` → `ghcr.io/invalidjoker/artifact-forge`.
- **H2 database**: the default file is now `./data/artifact-forge` (was `./data/maven-repo`). Either rename
  `maven-repo.mv.db` to `artifact-forge.mv.db` in your data volume, or keep the old path by setting
  `DATABASE_H2_FILE=./data/maven-repo`. Without one of the two, the instance starts with an empty database.
- **PostgreSQL**: the compose sample now uses the database `artifact_forge` and the user `forge`. An existing
  database needs no migration — keep pointing at it with `DATABASE_NAME` / `DATABASE_USER` / `DATABASE_PASSWORD`.

Artifacts, images and instance branding under `/app/data` are untouched, and an instance that already has a
display name keeps it — the new default only applies to fresh installs.

## Configuration

All settings are optional environment variables.

### General

| Variable | Default | Description |
| --- | --- | --- |
| `SESSION_SECRET` | (insecure default) | Key used to sign session cookies. Set this in production. |
| `SESSION_MAX_AGE` | `604800` | Session cookie lifetime in seconds (default 7 days). |
| `DATA_PATH` | `./data/instance` | Folder for instance settings (name, icon), stored as files. |
| `DEMO` | `false` | Set to `true` to show a "self-host required, no public instance" banner in the UI. |

### Admin

| Variable | Default | Description |
| --- | --- | --- |
| `ADMIN_USERNAME` | `admin` | Username of the seeded admin. |
| `ADMIN_PASSWORD` | (generated) | Password for the seeded admin. If unset, a random one is generated and printed to the logs once. |
| `ADMIN_RESET_PASSWORD` | `false` | Set to `true` for one boot to reset (and reprint) the admin password. |

### Database

| Variable | Default | Description |
| --- | --- | --- |
| `DATABASE_TYPE` | `h2` | `h2` (embedded file) or `postgres`. |
| `DATABASE_H2_FILE` | `./data/artifact-forge` | H2 database file path (used when `DATABASE_TYPE=h2`). |
| `DATABASE_HOST` / `DATABASE_PORT` / `DATABASE_NAME` / `DATABASE_USER` / `DATABASE_PASSWORD` | | PostgreSQL connection settings. |

### Storage

| Variable | Default | Description |
| --- | --- | --- |
| `STORAGE_TYPE` | `local` | `local` (data volume) or `s3`. |
| `STORAGE_PATH` | `./data/repositories` | Artifact directory (used when `STORAGE_TYPE=local`). |
| `UPLOAD_PATH` | `./data/uploads` | Scratch space for in-flight Docker layer uploads. Cleared on boot. |
| `S3_BUCKET` / `S3_REGION` / `S3_ACCESS_KEY_ID` / `S3_SECRET_ACCESS_KEY` | | S3 storage settings. |
| `S3_ENDPOINT` | (AWS) | Custom endpoint for S3-compatible stores (MinIO, R2, Backblaze). |

### Single sign-on (OIDC)

Optional OpenID Connect login for providers like Authentik, Pocket ID or Keycloak. Leave `OIDC_CLIENT_ID` blank to disable. Uses the authorization-code flow with PKCE; endpoints are discovered from the issuer.

| Variable | Default | Description |
| --- | --- | --- |
| `OIDC_ISSUER` | | Issuer URL (must be reachable from the container; not `localhost` if dockerized). |
| `OIDC_CLIENT_ID` | | OAuth client id. Setting this enables SSO. |
| `OIDC_CLIENT_SECRET` | | OAuth client secret. |
| `OIDC_SCOPES` | `openid profile email` | Space-separated scopes. |
| `OIDC_BUTTON_LABEL` | `Sign in with SSO` | Label of the login button. |

Register the redirect URI `<base-url>/auth/oidc/callback` with your provider. On first login a local user is created (matched by the `preferred_username` claim) with no password; promote users to admin under **Admin > Users**.

Data (H2 database, local artifacts, instance branding) is stored under `/app/data` — keep it on a volume. Behind a reverse proxy, forward `X-Forwarded-Proto` and `X-Forwarded-Host` so redirects and OIDC callbacks use the correct public URL.

## Usage

See [USAGE.md](USAGE.md) for setting up repositories, consuming artifacts, and publishing.

## Build from source

Requires JDK 21 and [bun](https://bun.sh).

```sh
./gradlew :backend:run          # run locally on :8080
./gradlew :backend:shadowJar    # fat jar at backend/build/libs/backend-all.jar
docker build -t artifact-forge . # container image
```
