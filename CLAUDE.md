# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

A self-hosted Maven repository (inspired by Reposilite): a Ktor/Kotlin backend that serves & stores artifacts, with a React single-page frontend bundled into the same jar.

## Commands

Uses **bun**, not npm, for the frontend. The frontend is built through Gradle (which shells out to bun), so most work goes through `./gradlew`.

- `./gradlew build` — compile backend, build+type-check frontend, run tests.
- `./gradlew :backend:run` — run the server on `http://localhost:8080` (triggers the frontend build and bundles it; admin password is printed to the logs on first boot).
- `./gradlew :backend:compileKotlin` — fast backend type-check.
- `./gradlew :frontend:buildFrontend` — `bun install` + `tsc -b && vite build` (use this to type-check the frontend).
- `./gradlew :backend:shadowJar` — fat jar at `backend/build/libs/backend-all.jar` (note the `-all` suffix, set by the Ktor plugin; `Main-Class` is `io.ktor.server.cio.EngineMain`).
- `docker build -t maven-repo .` — multi-stage image (bun-built frontend + shadow jar → slim JRE).

Frontend-only, from `frontend/`: `bun run dev` (Vite dev server; proxies `/api`, `/auth`, `/maven` to `:8080`), `bun run lint` (oxlint).

There are currently **no tests**; `./gradlew test` passes vacuously.

## Architecture

Two Gradle modules under `de.joker`: `:backend` (Ktor + Kotlin) and `:frontend` (React + Vite + Tailwind v4). The root `build.gradle.kts` applies the Kotlin/Shadow setup to JVM subprojects but **skips `:frontend`** (it's a Node project driven by `Exec` tasks calling bun). `:backend:processResources` depends on `:frontend:buildFrontend` and copies `frontend/dist` into the backend classpath under `web/`, which Ktor serves.

### Backend wiring
Ktor modules are listed and ordered in `backend/src/main/resources/application.yaml` (`de.joker.*Kt.configureX`). **Order matters**: Koin is installed before anything that injects; Auth (Sessions + Authentication) before Routing. DI lives in `di/AppModule.kt` — config-derived singletons are `single {}` lambdas, everything else is `singleOf(::...)` autowired.

Configuration is parsed into sealed `config/*Config.kt` classes from `application.yaml`, which uses Ktor's `"$ENV_VAR:default"` placeholder syntax. `DatabaseConfig` (h2/postgres), `StorageConfig` (local/s3), `AuthConfig`.

### Persistence
Exposed **R2DBC** (reactive/coroutine), not JDBC. All DB access goes through `DatabaseService.query { ... }` (a `suspendTransaction`). Tables are `object`s in `database/`. Schema is created with `SchemaUtils.create` in `configureDatabases` — there are **no migrations**, so altering an existing table won't change an already-created database (delete the H2 file / drop tables in dev).

### Storage abstraction
Artifacts are stored behind the `StorageBackend` interface (`service/StorageBackend.kt`): `LocalStorageBackend` (filesystem) and `S3StorageBackend` (AWS SDK v2, supports a custom `endpoint` for S3-compatible stores). The active one is selected from `StorageConfig` in `AppModule`. All paths are repository-relative; do not reintroduce `java.io.File` into callers — go through the interface.

### Auth & permissions
Two ways to authenticate: a **session cookie** (browser) or an **access token** via HTTP Basic (Gradle/Maven; username = the user's username, password = the token). Sessions are server-side and DB-persisted via `DatabaseSessionStorage` (DB + in-memory cache, hydrated on boot). Two Authentication providers: `AUTH_SESSION` and `AUTH_ADMIN` (constants in `Auth.kt`).

Permission model: repositories are public or private. Admins bypass all checks. Non-admins get access via `RepositoryPermissionTable` grants (`READ`/`WRITE`, `WRITE` ⊇ `READ`); access tokens can be further scoped to specific repos. The single source of truth is `AccessControlService.effectivePermission(principal, repoId)`. Public repos allow anonymous reads; writes always require auth.

There are **no signups** — admins create users (`/api/users`). The initial admin password is randomly generated and logged once on first boot; `ADMIN_RESET_PASSWORD=true` regenerates it on a boot (recovery).

### Maven & browsing
The Maven endpoints live at `/maven/<repo>/<path...>` (standard Maven layout `group/parts/artifactId/version/files`). The browser API (`/api/repositories/{repo}/tree/...` and `/search`) and `RepositoryBrowserService` infer Maven coordinates **heuristically from the path shape** — a directory holding version subdirectories is an "artifact"; a version directory is any segment starting with a digit. There is no `maven-metadata.xml` parsing.

### Conventions
Don't write boilerplate or ceremony for self-explanatory code. DTOs/data classes, simple mappers, and obvious one-liners should not get doc comments, factory functions, builders, or wrapper helpers — keep them plain. Only add a comment or a dedicated function when it carries non-obvious intent (a tricky invariant, a heuristic, a security/permission rule). Match the surrounding terseness.

### Frontend
Hash-based routing (`/#/...`) implemented by hand in `src/router.ts` — no router library — so Ktor only needs `staticResources("/", "web")` and API/Maven routes win by path specificity. `src/auth.tsx` holds the auth context; `src/api.ts` is the typed client. Icons are `lucide-react`.

TypeScript is strict in ways that bite (`frontend/tsconfig.app.json`): `erasableSyntaxOnly` (no TS `enum`s or constructor parameter properties — use string-literal unions and explicit field assignment), `noUnusedLocals`/`noUnusedParameters`, and `verbatimModuleSyntax` (type-only imports must use `import type`). The Gradle frontend build runs `tsc -b`, so these fail the build, not just the editor.
