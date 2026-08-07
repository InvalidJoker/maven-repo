# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

A self-hosted artifact registry (inspired by Reposilite): a Ktor/Kotlin backend serving **Maven repositories**, **Docker/OCI registries** and **npm registries** from one instance, with a React single-page frontend bundled into the same jar.

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

One trap: `GET /api/repositories` is registered twice, and the browse route wins over the admin one, so even the admin UI receives `UserRepositoryDto` — a field the admin pages need has to exist on that DTO too, not only on `RepositoryDto`.

### Storage abstraction
Artifacts are stored behind the `StorageBackend` interface (`service/storage/StorageBackend.kt`): `LocalStorageBackend` (filesystem) and `S3StorageBackend` (AWS SDK v2, supports a custom `endpoint` for S3-compatible stores). The active one is selected from `StorageConfig` in `AppModule`. All paths are repository-relative; do not reintroduce `java.io.File` into callers — go through the interface. The one deliberate exception is `BlobUploadSessions`, which buffers in-flight Docker layer uploads on local disk because the interface has no append operation; only finished blobs reach the backend.

### Repository types
Every repository is `MAVEN`, `DOCKER` or `NPM` (`RepositoryType` on `RepositoryTable`). The type decides which protocol serves it; routes pass the expected type to `RepositoryAccess.check`, so a Maven URL on a Docker repository (or an npm URL on either) is a 404. Everything else — users, grants, token scopes — is shared across all three.

### Mirrors (proxy repositories)
A repository is `HOSTED` or `PROXY` (`RepositoryMode`). A proxy repository holds nothing of its own: it answers from `remoteUrls` — an ordered list of upstream registries, stored newline-separated in one column — and keeps everything it hands out, so the second request is served locally. Publishing to one is a `405` in each protocol's own error format (checked in the route-level `authorize` helpers, which is why every write path is covered by one branch).

`service/proxy/` holds the whole feature: `ProxyCache` (freshness, negative cache, upstream requests) plus one service per protocol. Things worth knowing before changing it:

- **Ordering.** Upstreams are tried in order and the first that has the artifact wins; there is no merging of `maven-metadata.xml` or of tag lists across upstreams. A registry that does not hold something answers `401`/`403` as readily as `404` (JitPack and ghcr.io both do), so `UNAVAILABLE_UPSTREAM` treats all three as "ask the next one" — only real failures make a request `502`.
- **What expires.** Released artifacts, layers and tarballs are immutable and cached forever. Only `maven-metadata.xml`, snapshot paths, npm packuments and Docker tag → digest mappings expire, after `cacheTtlSeconds`. Freshness comes from `StorageBackend.stat`, so no extra bookkeeping is stored.
- **Resilience.** When every upstream fails, an expired cached copy is served rather than breaking a build; a genuine upstream `404` is cached in memory for five minutes, because Maven clients ask for `.sha256`, `.asc` and `.module` files that mostly do not exist.
- **Streaming.** `ProxyCache.stream` tees the upstream body to the client and into the cache at once. Because a failure mid-transfer cannot be turned into an error response, it reports `ProxyOutcome.ABORTED`, which routes must treat as "already answered". `LocalStorageBackend.write` moves a temp file into place so a concurrent reader never sees a half-filled artifact.
- **Redirects and upstream auth.** The proxy `HttpClient` (named `proxy` in Koin) has `followRedirects = false`; `ProxyCache` follows them itself and drops the `Authorization` header when the host changes, since registries hand blobs to a CDN that rejects (and should not see) our credential. `UpstreamRegistryAuth` plays the Docker token handshake against the upstream, which Docker Hub, ghcr.io and quay.io all require even for public images. Upstreams that need credentials are not supported yet.
- **Caching layout.** Mirrored content is stored exactly like published content — Maven paths as they are, npm as a `StoredPackument` (with `remote` remembering each tarball's real upstream URL, since `dist.tarball` is rewritten to point at us), Docker as `blobs/…` + `images/<image>/…`. The browse UI therefore works on cached content unchanged, and `DELETE /api/repositories/{repo}/cache` simply wipes the repository directory.
- **Docker Hub.** Official images live under `library/`, which clients never spell out, so a Docker Hub upstream gets the prefix added.

### Auth & permissions
Three ways to authenticate: a **session cookie** (browser), an **access token** — via HTTP Basic for Gradle/Maven and `docker login` (username = the user's username, password = the token), or as a plain `Bearer` for npm's `_authToken` — or a **registry bearer token** (Docker, see below). The bearer branch of `RepositoryAccess.authenticate` tries the signed registry token first and falls back to looking the value up as an access token, which is what makes one token work everywhere. Sessions are server-side and DB-persisted via `DatabaseSessionStorage` (DB + in-memory cache, hydrated on boot). Two Authentication providers: `AUTH_SESSION` and `AUTH_ADMIN` (constants in `Auth.kt`) — these cover the `/api` routes; the Maven and Docker endpoints authenticate through `RepositoryAccess` instead, because they must challenge in their own protocol's format.

`auth/RepositoryAccess.kt` is the single entry point for "may this caller do X to repository Y": it resolves the principal from any of the three credential types and returns `Granted`/`Denied(reason)`. Maven, Docker and the browser API each render that denial their own way (Basic challenge, OCI error JSON + Bearer challenge, plain 404/403). Permission itself still comes from `AccessControlService.effectivePermission(principal, repoId)`.

Permission model: repositories are public or private. Admins bypass all checks. Non-admins get access via `RepositoryPermissionTable` grants (`READ`/`WRITE`, `WRITE` ⊇ `READ`); access tokens can be further scoped to specific repos. Public repos allow anonymous reads; writes always require auth.

There are **no signups** — admins create users (`/api/users`). The initial admin password is randomly generated and logged once on first boot; `ADMIN_RESET_PASSWORD=true` regenerates it on a boot (recovery).

### Maven & browsing
The Maven endpoints live at `/maven/<repo>/<path...>` (standard Maven layout `group/parts/artifactId/version/files`). The browser API (`/api/repositories/{repo}/tree/...` and `/search`) and `MavenBrowserService` infer Maven coordinates **heuristically from the path shape** — a directory holding version subdirectories is an "artifact"; a version directory is any segment starting with a digit. There is no `maven-metadata.xml` parsing.

### Docker registry
`routes/DockerRegistryRoutes.kt` implements the OCI distribution API at `/v2`. The spec puts the verb *after* a variable-length image name (`/v2/<name…>/blobs/<digest>`), which Ktor routing cannot express, so every request goes through one tailcard route and `parseTarget` splits it from the end. An image name is `<repository>/<image>`: the first segment selects a Docker repository (created by an admin), the rest is the image path, created implicitly on push.

Auth uses the standard Docker handshake: `/v2/` answers 401 with a `Bearer` realm, the client exchanges its access token at `/v2/token` for a short-lived HMAC-signed token (`RegistryTokenService`, no DB row — permissions are re-checked per request), and anonymous tokens let public repositories be pulled. Basic auth and session cookies also work directly, for curl and the UI.

Storage layout inside a repository (`service/docker/Oci.kt`): blobs are content-addressed and shared repository-wide at `blobs/<algo>/<hex>`; manifests and tags are per image at `images/<image>/manifests/<algo>/<hex>` (plus a `.mediatype` sidecar, since the pushed media type cannot be recovered from the bytes) and `images/<image>/tags/<tag>` holding the digest. Deleting a tag or image leaves its blobs behind — there is no GC yet.

Two protocol details that are easy to regress: registry clients send an `Accept` header listing only manifest media types, so registry JSON is written with `respondOci`/`respondJson` (explicit serialization) instead of content negotiation, which would answer `406` — including for error bodies, hiding the real failure. And `Application.stripHeadResponseBodies` removes bodies from HEAD responses; without it clients report "unsolicited response" and keep-alive connections desynchronize.

### npm registry
`routes/NpmRegistryRoutes.kt` serves the npm protocol at `/npm/<repo>`. Same shape of problem as OCI — the verb follows a variable-length package name (`/<name>/-/<file>.tgz`) and names may be scoped (`@scope/pkg`, sent as two segments or with the slash percent-encoded) — so one tailcard route feeds `parseNpmTarget`. Implemented: packument, single-version document, tarball download, publish, `dist-tags` (list/add/remove), `whoami` and `ping`.

Per package, `packages/<name>/packument.json` holds the dist-tags, timestamps and every published version manifest **verbatim** (npm puts arbitrary fields in them), with only `dist` rewritten to what was actually stored; `packages/<name>/-/<file>.tgz` holds the tarballs. `dist.tarball` is persisted as a bare file name and expanded to an absolute URL per request, so the registry survives a host change. `shasum`/`integrity` are always recomputed from the uploaded bytes rather than trusted from the client. Publishing is read-modify-write on one document, so `NpmRegistryService` serializes writes per package with an in-process mutex, and republishing an existing version is a `409`.

### Conventions
Don't write boilerplate or ceremony for self-explanatory code. DTOs/data classes, simple mappers, and obvious one-liners should not get doc comments, factory functions, builders, or wrapper helpers — keep them plain. Only add a comment or a dedicated function when it carries non-obvious intent (a tricky invariant, a heuristic, a security/permission rule). Match the surrounding terseness.

### Frontend
Hash-based routing (`/#/...`) implemented by hand in `src/router.ts` — no router library — so Ktor only needs `staticResources("/", "web")` and API/Maven/`/v2` routes win by path specificity. `src/auth.tsx` holds the auth context; `src/api/` is the typed client. Icons are `lucide-react`.

`/#/repo/<name>/…` does not say which format a repository holds, so `pages/Repository.tsx` fetches the metadata first and hands over to `MavenBrowser` or `DockerBrowser`. For Docker the trailing segments are `<image>` and `<tag>`; the image is **one URI-encoded segment**, so nested names (`team/api`) survive `router.segments()` intact.

TypeScript is strict in ways that bite (`frontend/tsconfig.app.json`): `erasableSyntaxOnly` (no TS `enum`s or constructor parameter properties — use string-literal unions and explicit field assignment), `noUnusedLocals`/`noUnusedParameters`, and `verbatimModuleSyntax` (type-only imports must use `import type`). The Gradle frontend build runs `tsc -b`, so these fail the build, not just the editor.
