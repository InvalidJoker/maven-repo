package de.joker.service.npm

import de.joker.service.storage.StorageBackend
import de.joker.service.storage.StorageObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * npm registry storage on top of [StorageBackend]:
 * ```
 * <repo>/packages/<name>/packument.json   -> metadata for every published version
 * <repo>/packages/<name>/-/<file>.tgz     -> the tarballs, named as npm names them
 * ```
 */
class NpmRegistryService(private val storage: StorageBackend) {

    /** Publishing is read-modify-write on one document, so writes to the same package are serialized. */
    private val locks = ConcurrentHashMap<String, Mutex>()

    sealed interface PublishResult {
        data class Published(val versions: List<String>) : PublishResult
        data class Conflict(val version: String) : PublishResult
        data class Invalid(val message: String) : PublishResult
    }

    suspend fun packument(repository: String, name: String): StoredPackument? {
        val obj = storage.read(repository, NpmLayout.packument(name)) ?: return null
        val text = obj.use { it.stream.readBytes().decodeToString() }
        return runCatching { npmJson.decodeFromString(StoredPackument.serializer(), text) }.getOrNull()
    }

    suspend fun tarball(repository: String, name: String, file: String): StorageObject? =
        storage.read(repository, NpmLayout.tarball(name, file))

    suspend fun publish(repository: String, name: String, body: JsonObject): PublishResult = withLock(repository, name) {
        val versions = body["versions"]?.jsonObject
            ?: return@withLock PublishResult.Invalid("Request has no versions")
        val attachments = body["_attachments"]?.jsonObject ?: JsonObject(emptyMap())

        val stored = packument(repository, name) ?: StoredPackument(name)
        val now = Instant.now().toString()
        val newVersions = LinkedHashMap(stored.versions)
        val times = LinkedHashMap(stored.time)
        val published = ArrayList<String>()

        for ((version, element) in versions) {
            val manifest = element.jsonObject
            val file = tarballNameFor(name, version)
            val attachment = attachments[file]?.jsonObject
                ?: attachments.entries.singleOrNull()?.value?.jsonObject

            if (attachment == null) {
                // No payload: a metadata-only update such as `npm deprecate`, which may only touch known versions.
                if (newVersions.containsKey(version)) newVersions[version] = keepDist(manifest, stored.versions[version])
                continue
            }
            if (newVersions.containsKey(version)) return@withLock PublishResult.Conflict(version)

            val data = attachment["data"]?.jsonPrimitive?.content
                ?: return@withLock PublishResult.Invalid("Attachment for $version has no data")
            val bytes = runCatching { Base64.getDecoder().decode(data) }.getOrNull()
                ?: return@withLock PublishResult.Invalid("Attachment for $version is not valid base64")

            if (!isValidTarballName(file)) return@withLock PublishResult.Invalid("Invalid tarball name $file")
            storage.write(repository, NpmLayout.tarball(name, file), bytes.inputStream())

            newVersions[version] = manifest.withDist(
                tarball = file,
                shasum = sha1Hex(bytes),
                integrity = sha512Integrity(bytes),
            )
            times[version] = now
            published += version
        }

        if (published.isEmpty() && newVersions == stored.versions) {
            return@withLock PublishResult.Invalid("Nothing to publish")
        }

        val distTags = LinkedHashMap(stored.distTags)
        body["dist-tags"]?.jsonObject?.forEach { (tag, value) ->
            val version = value.jsonPrimitive.content
            if (newVersions.containsKey(version)) distTags[tag] = version
        }
        published.lastOrNull()?.let { distTags.putIfAbsent("latest", it) }

        times["created"] = stored.time["created"] ?: now
        times["modified"] = now

        store(repository, StoredPackument(name, distTags, newVersions, times))
        PublishResult.Published(published)
    }

    suspend fun setDistTag(repository: String, name: String, tag: String, version: String): Boolean =
        withLock(repository, name) {
            val stored = packument(repository, name) ?: return@withLock false
            if (!stored.versions.containsKey(version)) return@withLock false
            store(repository, stored.copy(distTags = stored.distTags + (tag to version)))
            true
        }

    suspend fun deleteDistTag(repository: String, name: String, tag: String): Boolean = withLock(repository, name) {
        val stored = packument(repository, name) ?: return@withLock false
        if (tag == "latest" || !stored.distTags.containsKey(tag)) return@withLock false
        store(repository, stored.copy(distTags = stored.distTags - tag))
        true
    }

    suspend fun deleteVersion(repository: String, name: String, version: String): Boolean = withLock(repository, name) {
        val stored = packument(repository, name) ?: return@withLock false
        if (!stored.versions.containsKey(version)) return@withLock false

        storage.delete(repository, NpmLayout.tarball(name, tarballNameFor(name, version)))
        val versions = stored.versions - version
        if (versions.isEmpty()) {
            storage.deleteDirectory(repository, NpmLayout.packageRoot(name))
            return@withLock true
        }

        val distTags = stored.distTags.filterValues { it != version }.toMutableMap()
        if (!distTags.containsKey("latest")) {
            versions.keys.maxWithOrNull(VERSION_ORDER)?.let { distTags["latest"] = it }
        }
        store(repository, stored.copy(distTags = distTags, versions = versions, time = stored.time - version))
        true
    }

    suspend fun deletePackage(repository: String, name: String): Boolean =
        storage.deleteDirectory(repository, NpmLayout.packageRoot(name))

    suspend fun tarballSize(repository: String, name: String, file: String): Long? =
        storage.list(repository, NpmLayout.tarballs(name))?.firstOrNull { it.name == file }?.size

    /** npm names are at most one level deep (`@scope/name`), so the walk never needs to recurse further. */
    suspend fun listPackages(repository: String): List<String> {
        val root = storage.list(repository, NpmLayout.PACKAGES) ?: return emptyList()
        val names = ArrayList<String>()
        for (entry in root.filter { it.directory }) {
            if (!entry.name.startsWith("@")) {
                names += entry.name
                continue
            }
            val scoped = storage.list(repository, "${NpmLayout.PACKAGES}/${entry.name}").orEmpty()
            for (child in scoped.filter { it.directory }) names += "${entry.name}/${child.name}"
        }
        return names.sorted()
    }

    suspend fun store(repository: String, packument: StoredPackument) {
        val json = npmJson.encodeToString(StoredPackument.serializer(), packument)
        storage.write(repository, NpmLayout.packument(packument.name), json.toByteArray().inputStream())
    }

    private suspend fun <T> withLock(repository: String, name: String, block: suspend () -> T): T =
        locks.computeIfAbsent("$repository/$name") { Mutex() }.withLock { block() }

    /** Keeps the `dist` block of an already-stored version when a metadata-only update replaces the manifest. */
    private fun keepDist(manifest: JsonObject, previous: JsonObject?): JsonObject {
        val dist = previous?.get("dist") ?: return manifest
        return JsonObject(manifest + ("dist" to dist))
    }
}

/**
 * Replaces whatever the client claimed in `dist` with what we actually stored. The tarball is kept as a file
 * name and expanded to an absolute URL when the packument is served, so the registry survives moving hosts.
 */
private fun JsonObject.withDist(tarball: String, shasum: String, integrity: String): JsonObject =
    JsonObject(
        this + (
            "dist" to buildJsonObject {
                put("tarball", JsonPrimitive(tarball))
                put("shasum", JsonPrimitive(shasum))
                put("integrity", JsonPrimitive(integrity))
            }
            ),
    )
