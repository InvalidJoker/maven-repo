# Usage

> Heads up: `repo.koder.wtf` is my personal instance, used here only as a live example. You can
> browse and read from it, but you can't publish to it. Deploy your own instance (see the
> [README](README.md)) and replace the URL in the snippets below with yours.

## Set up (web UI)

1. Sign in as `admin`.
2. Under **Admin > Repositories**, create a repository (e.g. `releases`), pick its type — **Maven**, **Docker** or **npm** — and choose public or private.
3. Under **Admin > Users**, create accounts for your team.
4. On a repository, grant users `READ` or `WRITE` access (admins always have full access).
5. Each user creates an **access token** under **Tokens** — that token is the password for Gradle/Maven, for `docker login` and the `_authToken` in `.npmrc`. Tokens can be scoped to specific repositories.

Maven repositories are served at `<base-url>/maven/<repository>`, Docker repositories at `<base-url>/v2` (image names are `<repository>/<image>`), npm repositories at `<base-url>/npm/<repository>`. Public repositories can be read, pulled or installed from without credentials; publishing always requires a token. The repository page in the UI shows ready-to-copy snippets for the exact coordinates, image or package.

## Use artifacts

Gradle (Kotlin DSL, `build.gradle.kts`):

```kotlin
repositories {
    maven {
        url = uri("https://repo.koder.wtf/maven/releases")
        credentials {
            username = "admin"
            password = "<access-token>"
        }
    }
}

dependencies {
    implementation("dev.invalidjoker.glue:core:2026.7-3541d29")
}
```

Maven — credentials in `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>artifact-forge</id>
    <username>admin</username>
    <password>&lt;access-token&gt;</password>
  </server>
</servers>
```

and the repository in `pom.xml`:

```xml
<repositories>
  <repository>
    <id>artifact-forge</id>
    <url>https://repo.koder.wtf/maven/releases</url>
  </repository>
</repositories>
```

## Publish artifacts

Gradle (`maven-publish`):

```kotlin
plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("https://repo.koder.wtf/maven/releases")
            credentials {
                username = "admin"
                password = "<access-token>"
            }
        }
    }
}
```

Publish with `./gradlew publish`. For Maven, add a matching `<distributionManagement>` block and run `mvn deploy`.

## Docker images

A repository of type **Docker** is an OCI registry. Image names start with the repository: pushing
`repo.koder.wtf/images/team/api:1.0` stores the image `team/api` in the repository `images`. Images and
namespaces are created implicitly on push — only the repository itself is created by an admin.

Log in with your username and an access token as the password:

```sh
docker login repo.koder.wtf -u admin -p <access-token>
```

Push and pull:

```sh
docker tag my-app repo.koder.wtf/images/team/api:1.0
docker push repo.koder.wtf/images/team/api:1.0
docker pull repo.koder.wtf/images/team/api:1.0
```

Public repositories can be pulled without logging in. Multi-platform images pushed with
`docker buildx build --push` work as usual; `podman` and `skopeo` speak the same API.

The repository page in the UI lists images, their tags, digests, layers and platforms, and lets users with
`WRITE` access delete a tag or a whole image. Note that Docker refuses plain HTTP unless the registry is
`localhost` or listed under `insecure-registries` — put the instance behind HTTPS.

## npm packages

A repository of type **npm** is a registry at `<base-url>/npm/<repository>`. Point a scope (or the whole
registry) at it in `.npmrc`, using an access token as `_authToken`:

```ini
@acme:registry=https://repo.koder.wtf/npm/packages/
//repo.koder.wtf/npm/packages/:_authToken=<access-token>
```

Then publish and install as usual:

```sh
npm publish
npm install @acme/greeter
```

Scoped and unscoped packages both work, as do `npm view`, `npm dist-tag add/rm/ls` and `npm whoami`. `pnpm`,
`yarn` and `bun` speak the same protocol. Public repositories can be installed from without a token; publishing
always needs one, and republishing an existing version is rejected — bump the version instead.

The repository page in the UI lists packages, their versions, dist-tags, dependencies and integrity hashes, and
lets users with `WRITE` access delete a version or a whole package.
