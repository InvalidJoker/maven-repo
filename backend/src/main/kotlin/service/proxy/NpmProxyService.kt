package de.joker.service.proxy

import de.joker.model.RepositoryDto
import de.joker.routes.respondStorageObject
import de.joker.service.npm.NpmLayout
import de.joker.service.npm.NpmRegistryService
import de.joker.service.npm.StoredPackument
import de.joker.service.npm.isValidTarballName
import de.joker.service.npm.npmJson
import de.joker.service.npm.tarballNameFor
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.time.Duration

sealed interface ProxyPackument {
    data class Found(val packument: StoredPackument) : ProxyPackument
    data object NotFound : ProxyPackument
    data object Error : ProxyPackument
}

/**
 * npm proxying mirrors the upstream packument into the same document a published package uses, so the browse
 * API, dist-tags and version lookups work on cached packages without knowing where they came from. Only the
 * `dist.tarball` URLs are rewritten: they are stored as bare file names (pointing back at this registry) with
 * the upstream URL kept alongside, which is what lets a tarball be fetched lazily on first install.
 */
class NpmProxyService(private val cache: ProxyCache, private val registry: NpmRegistryService) {

    suspend fun packument(repo: RepositoryDto, name: String): ProxyPackument {
        val remote = repo.remoteUrl ?: return ProxyPackument.NotFound
        val path = NpmLayout.packument(name)
        if (cache.fresh(repo.name, path, Duration.ofSeconds(repo.cacheTtlSeconds))) {
            return found(repo, name) ?: ProxyPackument.NotFound
        }

        val response = cache.fetch(upstreamUrl(remote, name), accept = listOf(ContentType.Application.Json.toString()))
        val mirrored = response?.takeIf { it.successful }?.let { mirror(name, it.body) }
        if (mirrored == null) {
            // A 404, a 500 or an unreachable upstream all fall back to whatever was cached last.
            found(repo, name)?.let { return it }
            return if (response?.status == HttpStatusCode.NotFound) ProxyPackument.NotFound else ProxyPackument.Error
        }

        registry.store(repo.name, mirrored)
        return ProxyPackument.Found(mirrored)
    }

    private suspend fun found(repo: RepositoryDto, name: String): ProxyPackument.Found? =
        registry.packument(repo.name, name)?.let { ProxyPackument.Found(it) }

    suspend fun tarball(call: ApplicationCall, repo: RepositoryDto, name: String, file: String): ProxyOutcome {
        val remote = repo.remoteUrl ?: return ProxyOutcome.NOT_FOUND
        val path = NpmLayout.tarball(name, file)

        cache.cached(repo.name, path)?.let {
            call.respondStorageObject(it, ContentType.Application.OctetStream)
            return ProxyOutcome.SERVED
        }

        val url = (packument(repo, name) as? ProxyPackument.Found)?.packument?.remote?.get(file)
            ?: upstreamUrl(remote, "$name/-/$file")
        return cache.stream(call, repo.name, path, url, ContentType.Application.OctetStream)
    }

    /** Rewrites an upstream packument into the stored form, remembering where each tarball actually lives. */
    private fun mirror(name: String, body: ByteArray): StoredPackument? {
        val document = runCatching { npmJson.parseToJsonElement(body.decodeToString()) as? JsonObject }.getOrNull()
        val versions = document?.get("versions")?.jsonObject ?: return null

        val remote = LinkedHashMap<String, String>()
        val mirrored = versions.mapValues { (version, element) ->
            val manifest = element.jsonObject
            val dist = manifest["dist"] as? JsonObject ?: JsonObject(emptyMap())
            val url = (dist["tarball"] as? JsonPrimitive)?.contentOrNull

            val file = url?.substringBefore('?')?.substringAfterLast('/')?.takeIf { isValidTarballName(it) }
                ?: tarballNameFor(name, version)
            if (url != null) remote[file] = url

            JsonObject(manifest + ("dist" to JsonObject(dist + ("tarball" to JsonPrimitive(file)))))
        }

        return StoredPackument(
            name = name,
            distTags = document["dist-tags"]?.jsonObject?.strings().orEmpty(),
            versions = mirrored,
            time = document["time"]?.jsonObject?.strings().orEmpty(),
            remote = remote,
        )
    }

    private fun JsonObject.strings(): Map<String, String> =
        mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }.toMap()
}
