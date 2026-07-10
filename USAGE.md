# Usage

> Heads up: `repo.koder.wtf` is my personal instance, used here only as a live example. You can
> browse and read from it, but you can't publish to it. Deploy your own instance (see the
> [README](README.md)) and replace the URL in the snippets below with yours.

## Set up (web UI)

1. Sign in as `admin`.
2. Under **Admin > Repositories**, create a repository (e.g. `releases`) and choose public or private.
3. Under **Admin > Users**, create accounts for your team.
4. On a repository, grant users `READ` or `WRITE` access (admins always have full access).
5. Each user creates an **access token** under **Tokens** — that token is the password for Gradle/Maven. Tokens can be scoped to specific repositories.

Repositories are served at `<base-url>/maven/<repository>`. Public repositories can be read without credentials; publishing always requires a token. The repository page in the UI shows ready-to-copy snippets for the exact coordinates.

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
    <id>maven-repo</id>
    <username>admin</username>
    <password>&lt;access-token&gt;</password>
  </server>
</servers>
```

and the repository in `pom.xml`:

```xml
<repositories>
  <repository>
    <id>maven-repo</id>
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
