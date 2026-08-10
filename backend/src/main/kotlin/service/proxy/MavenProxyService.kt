package de.joker.service.proxy

import de.joker.model.RepositoryDto
import de.joker.routes.respondStorageObject
import io.ktor.http.*
import io.ktor.server.application.*
import java.time.Duration

/**
 * Maven proxying is a plain path passthrough: the layout upstream is the layout we cache under, so
 * `/maven/<repo>/org/foo/1.0/foo-1.0.jar` becomes `<remote>/org/foo/1.0/foo-1.0.jar`. Several upstreams are
 * tried in the configured order and the first one holding the path wins.
 *
 * Released artifacts are immutable and cached forever; `maven-metadata.xml` and snapshot builds are refetched
 * once the repository's TTL has passed.
 */
class MavenProxyService(private val cache: ProxyCache) {

    suspend fun serve(call: ApplicationCall, repo: RepositoryDto, path: String): ProxyOutcome {
        if (!isArtifactPath(path)) return ProxyOutcome.NOT_FOUND
        val contentType = ContentType.defaultForFilePath(path)

        cache.cached(repo.name, path, ttl(repo, path))?.let {
            call.respondStorageObject(it, contentType)
            return ProxyOutcome.SERVED
        }
        if (cache.missed(repo.name, path)) return ProxyOutcome.NOT_FOUND

        var failed = false
        for (remote in repo.remoteUrls) {
            when (val outcome = cache.stream(call, repo.name, path, upstreamUrl(remote, path), contentType)) {
                ProxyOutcome.NOT_FOUND -> Unit
                ProxyOutcome.UPSTREAM_ERROR -> failed = true
                else -> return outcome
            }
        }

        if (!failed) {
            cache.rememberMiss(repo.name, path)
            return ProxyOutcome.NOT_FOUND
        }

        // An upstream is down or broken: an expired copy beats failing the build.
        val stale = cache.cached(repo.name, path) ?: return ProxyOutcome.UPSTREAM_ERROR
        call.respondStorageObject(stale, contentType)
        return ProxyOutcome.SERVED
    }

    /** `HEAD` is answered without pulling the artifact, which is what makes Gradle's existence checks cheap. */
    suspend fun exists(repo: RepositoryDto, path: String): ProxyOutcome {
        if (!isArtifactPath(path)) return ProxyOutcome.NOT_FOUND
        if (cache.fresh(repo.name, path, ttl(repo, path))) return ProxyOutcome.SERVED
        if (cache.missed(repo.name, path)) return ProxyOutcome.NOT_FOUND

        var failed = false
        for (remote in repo.remoteUrls) {
            val response = cache.fetch(upstreamUrl(remote, path), method = HttpMethod.Head)
            when {
                response == null -> failed = true
                response.successful -> return ProxyOutcome.SERVED
                response.status !in UNAVAILABLE_UPSTREAM -> failed = true
            }
        }

        if (!failed) {
            cache.rememberMiss(repo.name, path)
            return ProxyOutcome.NOT_FOUND
        }
        return if (cache.fresh(repo.name, path, null)) ProxyOutcome.SERVED else ProxyOutcome.UPSTREAM_ERROR
    }

    /**
     * Directories are not proxied. Every file a Maven client resolves carries an extension, while a directory
     * path answers with an HTML index upstream — which would be cached as a *file* under that name and block
     * everything below it from ever being stored.
     */
    private fun isArtifactPath(path: String): Boolean {
        val name = path.trimEnd('/').substringAfterLast('/')
        return name.isNotEmpty() && name.contains('.')
    }

    /**
     * Everything a release build resolves is immutable once published. Only the metadata documents and snapshot
     * versions change upstream, so only those expire.
     */
    private fun ttl(repo: RepositoryDto, path: String): Duration? {
        val volatile = path.substringAfterLast('/').startsWith("maven-metadata.xml") ||
            path.contains("-SNAPSHOT/")
        return if (volatile) Duration.ofSeconds(repo.cacheTtlSeconds) else null
    }
}
