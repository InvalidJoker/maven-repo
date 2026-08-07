package de.joker.service.proxy

import de.joker.model.RepositoryDto
import de.joker.routes.respondStorageObject
import de.joker.service.docker.Digest
import de.joker.service.docker.DockerLayout
import de.joker.service.docker.DockerRegistryService
import de.joker.service.docker.MediaTypes
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration

@Serializable
private data class UpstreamTags(val tags: List<String> = emptyList())

sealed interface ProxyManifest {
    data class Found(val manifest: DockerRegistryService.Manifest) : ProxyManifest
    data object NotFound : ProxyManifest
    data object Error : ProxyManifest
}

data class UpstreamBlob(val outcome: ProxyOutcome, val size: Long? = null)

/**
 * OCI proxying, cached into exactly the layout a push produces (`blobs/…`, `images/<image>/manifests/…`,
 * `images/<image>/tags/<tag>`), so cached images browse and pull like local ones.
 *
 * Manifests are content-addressed and cached forever; only the tag → digest mapping expires, because that is
 * the one thing that moves upstream.
 */
class DockerProxyService(
    private val cache: ProxyCache,
    private val registry: DockerRegistryService,
    private val auth: UpstreamRegistryAuth,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun manifest(repo: RepositoryDto, image: String, reference: String): ProxyManifest {
        val remote = repo.remoteUrl ?: return ProxyManifest.NotFound
        val digest = Digest.parseOrNull(reference)

        cached(repo, image, reference, digest, stale = false)?.let { return ProxyManifest.Found(it) }

        val upstream = upstreamImage(remote, image)
        val response = cache.fetch(
            url = "${remote.trimEnd('/')}/v2/$upstream/manifests/$reference",
            auth = auth.forImage(remote, upstream),
            accept = MediaTypes.MANIFEST_TYPES.toList(),
        )

        if (response == null || !response.successful) {
            // Keep serving what we have when the upstream is unreachable or rate-limiting us.
            cached(repo, image, reference, digest, stale = true)?.let { return ProxyManifest.Found(it) }
            return if (response?.status == HttpStatusCode.NotFound) ProxyManifest.NotFound else ProxyManifest.Error
        }

        val bytes = response.body
        val actual = Digest.of(bytes)
        if (digest != null && digest != actual) return ProxyManifest.Error

        val mediaType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()
            ?.takeIf { it in MediaTypes.MANIFEST_TYPES }
            ?: MediaTypes.OCI_MANIFEST
        registry.storeManifest(repo.name, image, actual, mediaType, bytes)
        if (digest == null) registry.storeTag(repo.name, image, reference, actual)

        return ProxyManifest.Found(DockerRegistryService.Manifest(actual, mediaType, bytes))
    }

    suspend fun blob(call: ApplicationCall, repo: RepositoryDto, image: String, digest: Digest): ProxyOutcome {
        val remote = repo.remoteUrl ?: return ProxyOutcome.NOT_FOUND

        registry.readBlob(repo.name, digest)?.let {
            call.respondStorageObject(it, ContentType.Application.OctetStream)
            return ProxyOutcome.SERVED
        }

        val upstream = upstreamImage(remote, image)
        return cache.stream(
            call = call,
            repository = repo.name,
            path = DockerLayout.blob(digest),
            url = "${remote.trimEnd('/')}/v2/$upstream/blobs/$digest",
            contentType = ContentType.Application.OctetStream,
            auth = auth.forImage(remote, upstream),
            headers = mapOf("Docker-Content-Digest" to digest.toString()),
        )
    }

    /** Answers `HEAD` for a blob without pulling the layer. */
    suspend fun blobHead(repo: RepositoryDto, image: String, digest: Digest): UpstreamBlob {
        val remote = repo.remoteUrl ?: return UpstreamBlob(ProxyOutcome.NOT_FOUND)
        val upstream = upstreamImage(remote, image)
        val response = cache.fetch(
            url = "${remote.trimEnd('/')}/v2/$upstream/blobs/$digest",
            auth = auth.forImage(remote, upstream),
            method = HttpMethod.Head,
        ) ?: return UpstreamBlob(ProxyOutcome.UPSTREAM_ERROR)

        return when {
            response.successful ->
                UpstreamBlob(ProxyOutcome.SERVED, response.headers[HttpHeaders.ContentLength]?.toLongOrNull())

            response.status == HttpStatusCode.NotFound -> UpstreamBlob(ProxyOutcome.NOT_FOUND)
            else -> UpstreamBlob(ProxyOutcome.UPSTREAM_ERROR)
        }
    }

    /** Upstream tag list, falling back to the tags of whatever has been cached so far. */
    suspend fun tags(repo: RepositoryDto, image: String): List<String> {
        val remote = repo.remoteUrl ?: return emptyList()
        val upstream = upstreamImage(remote, image)
        val response = cache.fetch(
            url = "${remote.trimEnd('/')}/v2/$upstream/tags/list",
            auth = auth.forImage(remote, upstream),
            accept = listOf(ContentType.Application.Json.toString()),
        )
        val tags = response?.takeIf { it.successful }
            ?.let { runCatching { json.decodeFromString(UpstreamTags.serializer(), it.body.decodeToString()) }.getOrNull() }
            ?: return registry.listTags(repo.name, image)
        return tags.tags.sorted()
    }

    private suspend fun cached(
        repo: RepositoryDto,
        image: String,
        reference: String,
        digest: Digest?,
        stale: Boolean,
    ): DockerRegistryService.Manifest? {
        if (digest != null) return registry.readManifest(repo.name, image, digest)

        val ttl = if (stale) null else Duration.ofSeconds(repo.cacheTtlSeconds)
        if (!cache.fresh(repo.name, DockerLayout.tag(image, reference), ttl)) return null
        val resolved = registry.resolveTag(repo.name, image, reference) ?: return null
        return registry.readManifest(repo.name, image, resolved)
    }

    /**
     * Docker Hub keeps its official images under `library/`, which clients never spell out — `docker pull
     * <host>/<repo>/nginx` has to become `library/nginx` upstream.
     */
    private fun upstreamImage(remote: String, image: String): String {
        if (image.contains('/')) return image
        val host = runCatching { Url(remote).host }.getOrNull() ?: return image
        return if (host in DOCKER_HUB_HOSTS) "library/$image" else image
    }

    private companion object {
        val DOCKER_HUB_HOSTS = setOf("registry-1.docker.io", "index.docker.io", "registry.docker.io", "docker.io")
    }
}
