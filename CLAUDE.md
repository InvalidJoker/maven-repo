# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

A self-hosted artifact registry (inspired by Reposilite): a Ktor/Kotlin backend serving **Maven repositories** and **Docker/OCI registries** from one instance, with a React single-page frontend bundled into the same jar.

## Commands

Uses **bun**, not npm, for the frontend. The frontend is built through Gradle (which shells out to bun), so most work goes through `./gradlew`.

- `./gradlew build` — compile backend, build+type-check frontend, run tests.
- `./gradlew :backend:run` — run the server on `http://localhost:8080` (triggers the frontend build and bundles it; admin password is printed to the logs on first boot).
- `./gradlew :backend:compileKotlin` — fast backend type-check.
- `./gradlew :frontend:buildFrontend` — `bun install` + `tsc -b && vite build` (use this to type-check the frontend).
- `./gradlew :backend:shadowJar` — fat jar at `backend/build/libs/backend-all.jar` (note the `-all` suffix, set by the Ktor plugin; `Main-Class` is `io.ktor.server.cio.EngineMain`).
- `docker build -t artifact-forge .` — multi-stage image (bun-built frontend + shadow jar → slim JRE).

Frontend-only, from `frontend/`: `bun run dev` (Vite dev server; proxies `/api`, `/auth`, `/maven` to `:8080`), `bun run lint` (oxlint).

The Docker endpoints are best verified against the real client: `docker login localhost:8080 -u <user> -p <token>`, then push/pull. Docker treats `localhost` as an insecure registry, so no TLS is needed.

There are currently **no tests**; `./gradlew test` passes vacuously.

## Architecture

Two Gradle modules under `de.joker`: `:backend` (Ktor + Kotlin) and `:frontend` (React + Vite + Tailwind v4). The root `build.gradle.kts` applies the Kotlin/Shadow setup to JVM subprojects but **skips `:frontend`** (it's a Node project driven by `Exec` tasks calling bun). `:backend:processResources` depends on `:frontend:buildFrontend` and copies `frontend/dist` into the backend classpath under `web/`, which Ktor serves.

### Backend wiring
Ktor modules are listed and ordered in `backend/src/main/resources/application.yaml` (`de.joker.*Kt.configureX`). **Order matters**: Koin is installed before anything that injects; Auth (Sessions + Authentication) before Routing. DI lives in `di/AppModule.kt` — config-derived singletons are `single {}` lambdas, everything else is `singleOf(::...)` autowired.

Configuration is parsed into sealed `config/*Config.kt` classes from `application.yaml`, which uses Ktor's `"$ENV_VAR:default"` placeholder syntax. `DatabaseConfig` (h2/postgres), `StorageConfig` (local/s3), `AuthConfig`.

### Persistence
Exposed **R2DBC** (reactive/coroutine), not JDBC. All DB access goes through `DatabaseService.query { ... }` (a `suspendTransaction`). Tables are `object`s in `database/`. Schema is created with `SchemaUtils.createMissingTablesAndColumns` in `configureDatabases` — new tables and columns appear automatically, but there are **no migrations**, so changing or dropping an existing column won't touch an already-created database (delete the H2 file / drop tables in dev).

### Storage abstraction
Artifacts are stored behind the `StorageBackend` interface (`service/storage/StorageBackend.kt`): `LocalStorageBackend` (filesystem) and `S3StorageBackend` (AWS SDK v2, supports a custom `endpoint` for S3-compatible stores). The active one is selected from `StorageConfig` in `AppModule`. All paths are repository-relative; do not reintroduce `java.io.File` into callers — go through the interface. The one deliberate exception is `BlobUploadSessions`, which buffers in-flight Docker layer uploads on local disk because the interface has no append operation; only finished blobs reach the backend.

### Repository types
Every repository is `MAVEN` or `DOCKER` (`RepositoryType` on `RepositoryTable`). The type decides which protocol serves it; routes pass the expected type to `RepositoryAccess.check`, so a Maven URL on a Docker repository (and vice versa) is a 404. Everything else — users, grants, token scopes — is shared between both.

### Auth & permissions
Three ways to authenticate: a **session cookie** (browser), an **access token** via HTTP Basic (Gradle/Maven and `docker login`; username = the user's username, password = the token), or a **registry bearer token** (Docker, see below). Sessions are server-side and DB-persisted via `DatabaseSessionStorage` (DB + in-memory cache, hydrated on boot). Two Authentication providers: `AUTH_SESSION` and `AUTH_ADMIN` (constants in `Auth.kt`) — these cover the `/api` routes; the Maven and Docker endpoints authenticate through `RepositoryAccess` instead, because they must challenge in their own protocol's format.

`auth/RepositoryAccess.kt` is the single entry point for "may this caller do X to repository Y": it resolves the principal from any of the three credential types and returns `Granted`/`Denied(reason)`. Maven, Docker and the browser API each render that denial their own way (Basic challenge, OCI error JSON + Bearer challenge, plain 404/403). Permission itself still comes from `AccessControlService.effectivePermission(principal, repoId)`.

Permission model: repositories are public or private. Admins bypass all checks. Non-admins get access via `RepositoryPermissionTable` grants (`READ`/`WRITE`, `WRITE` ⊇ `READ`); access tokens can be further scoped to specific repos. Public repos allow anonymous reads; writes always require auth.

There are **no signups** — admins create users (`/api/users`). The initial admin password is randomly generated and logged once on first boot; `ADMIN_RESET_PASSWORD=true` regenerates it on a boot (recovery).

### Maven & browsing
The Maven endpoints live at `/maven/<repo>/<path...>` (standard Maven layout `group/parts/artifactId/version/files`). The browser API (`/api/repositories/{repo}/tree/...` and `/search`) and `MavenBrowserService` infer Maven coordinates **heuristically from the path shape** — a directory holding version subdirectories is an "artifact"; a version directory is any segment starting with a digit. There is no `maven-metadata.xml` parsing.

### Docker registry
`routes/DockerRegistryRoutes.kt` implements the OCI distribution API at `/v2`. The spec puts the verb *after* a variable-length image name (`/v2/<name…>/blobs/<digest>`), which Ktor routing cannot express, so every request goes through one tailcard route and `parseTarget` splits it from the end. An image name is `<repository>/<image>`: the first segment selects a Docker repository (created by an admin), the rest is the image path, created implicitly on push.

Auth uses the standard Docker handshake: `/v2/` answers 401 with a `Bearer` realm, the client exchanges its access token at `/v2/token` for a short-lived HMAC-signed token (`RegistryTokenService`, no DB row — permissions are re-checked per request), and anonymous tokens let public repositories be pulled. Basic auth and session cookies also work directly, for curl and the UI.

Storage layout inside a repository (`service/docker/Oci.kt`): blobs are content-addressed and shared repository-wide at `blobs/<algo>/<hex>`; manifests and tags are per image at `images/<image>/manifests/<algo>/<hex>` (plus a `.mediatype` sidecar, since the pushed media type cannot be recovered from the bytes) and `images/<image>/tags/<tag>` holding the digest. Deleting a tag or image leaves its blobs behind — there is no GC yet.

Two protocol details that are easy to regress: registry clients send an `Accept` header listing only manifest media types, so registry JSON is written with `respondOci` (explicit serialization) instead of content negotiation, which would answer `406` — including for error bodies, hiding the real failure. And `Application.stripHeadResponseBodies` removes bodies from HEAD responses; without it clients report "unsolicited response" and keep-alive connections desynchronize.

### Conventions
Don't write boilerplate or ceremony for self-explanatory code. DTOs/data classes, simple mappers, and obvious one-liners should not get doc comments, factory functions, builders, or wrapper helpers — keep them plain. Only add a comment or a dedicated function when it carries non-obvious intent (a tricky invariant, a heuristic, a security/permission rule). Match the surrounding terseness.

### Frontend
Hash-based routing (`/#/...`) implemented by hand in `src/router.ts` — no router library — so Ktor only needs `staticResources("/", "web")` and API/Maven/`/v2` routes win by path specificity. `src/auth.tsx` holds the auth context; `src/api/` is the typed client. Icons are `lucide-react`.

`/#/repo/<name>/…` does not say which format a repository holds, so `pages/Repository.tsx` fetches the metadata first and hands over to `MavenBrowser` or `DockerBrowser`. For Docker the trailing segments are `<image>` and `<tag>`; the image is **one URI-encoded segment**, so nested names (`team/api`) survive `router.segments()` intact.

TypeScript is strict in ways that bite (`frontend/tsconfig.app.json`): `erasableSyntaxOnly` (no TS `enum`s or constructor parameter properties — use string-literal unions and explicit field assignment), `noUnusedLocals`/`noUnusedParameters`, and `verbatimModuleSyntax` (type-only imports must use `import type`). The Gradle frontend build runs `tsc -b`, so these fail the build, not just the editor.
