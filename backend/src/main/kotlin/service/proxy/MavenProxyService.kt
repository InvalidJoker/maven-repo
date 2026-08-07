package de.joker.service.proxy

import de.joker.model.RepositoryDto
import de.joker.routes.respondStorageObject
import io.ktor.http.*
import io.ktor.server.application.*
import java.time.Duration

/**
 * Maven proxying is a plain path passthrough: the layout upstream is the layout we cache under, so
 * `/maven/<repo>/org/foo/1.0/foo-1.0.jar` becomes `<remote>/org/foo/1.0/foo-1.0.jar`.
 *
 * Released artifacts are immutable and cached forever; `maven-metadata.xml` and snapshot builds are refetched
 * once the repository's TTL has passed.
 */
class MavenProxyService(private val cache: ProxyCache) {

    suspend fun serve(call: ApplicationCall, repo: RepositoryDto, path: String): ProxyOutcome {
        val remote = repo.remoteUrl ?: return ProxyOutcome.NOT_FOUND
        val contentType = ContentType.defaultForFilePath(path)

        cache.cached(repo.name, path, ttl(repo, path))?.let {
            call.respondStorageObject(it, contentType)
            return ProxyOutcome.SERVED
        }
        if (cache.missed(repo.name, path)) return ProxyOutcome.NOT_FOUND

        val outcome = cache.stream(call, repo.name, path, upstreamUrl(remote, path), contentType)
        if (outcome != ProxyOutcome.UPSTREAM_ERROR) return outcome

        // The upstream is down or broken: an expired copy beats failing the build.
        val stale = cache.cached(repo.name, path) ?: return outcome
        call.respondStorageObject(stale, contentType)
        return ProxyOutcome.SERVED
    }

    /** `HEAD` is answered without pulling the artifact, which is what makes Gradle's existence checks cheap. */
    suspend fun exists(repo: RepositoryDto, path: String): ProxyOutcome {
        val remote = repo.remoteUrl ?: return ProxyOutcome.NOT_FOUND
        if (cache.fresh(repo.name, path, ttl(repo, path))) return ProxyOutcome.SERVED
        if (cache.missed(repo.name, path)) return ProxyOutcome.NOT_FOUND

        val response = cache.fetch(upstreamUrl(remote, path), method = HttpMethod.Head)
            ?: return if (cache.fresh(repo.name, path, null)) ProxyOutcome.SERVED else ProxyOutcome.UPSTREAM_ERROR
        return when {
            response.successful -> ProxyOutcome.SERVED
            response.status == HttpStatusCode.NotFound -> ProxyOutcome.NOT_FOUND
            else -> ProxyOutcome.UPSTREAM_ERROR
        }
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
