package de.joker.service.docker

import de.joker.service.storage.StorageBackend
import de.joker.service.storage.StorageObject
import java.io.File

/**
 * OCI blob and manifest storage on top of [StorageBackend].
 *
 * Blobs are content-addressed and shared by every image in a repository; manifests and tags are per image:
 * ```
 * <repo>/blobs/sha256/<hex>
 * <repo>/images/<image>/manifests/sha256/<hex>
 * <repo>/images/<image>/tags/<tag>          -> file holding the manifest digest
 * ```
 */
class DockerRegistryService(private val storage: StorageBackend) {

    class Manifest(val digest: Digest, val mediaType: String, val bytes: ByteArray)

    sealed interface PutManifestResult {
        data class Stored(val digest: Digest) : PutManifestResult
        data class Invalid(val message: String) : PutManifestResult
        data class MissingBlob(val digest: String) : PutManifestResult
    }

    suspend fun blobExists(repository: String, digest: Digest): Boolean =
        storage.exists(repository, DockerLayout.blob(digest))

    suspend fun readBlob(repository: String, digest: Digest): StorageObject? =
        storage.read(repository, DockerLayout.blob(digest))

    suspend fun writeBlob(repository: String, digest: Digest, file: File): Boolean =
        file.inputStream().use { storage.write(repository, DockerLayout.blob(digest), it) }

    suspend fun deleteBlob(repository: String, digest: Digest): Boolean =
        storage.delete(repository, DockerLayout.blob(digest))

    suspend fun listTags(repository: String, image: String): List<String> =
        storage.list(repository, DockerLayout.tags(image))
            ?.filter { !it.directory }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

    suspend fun tagPushedAt(repository: String, image: String, tag: String): java.time.Instant? =
        storage.list(repository, DockerLayout.tags(image))?.firstOrNull { it.name == tag }?.lastModified

    suspend fun resolveTag(repository: String, image: String, tag: String): Digest? {
        val obj = storage.read(repository, DockerLayout.tag(image, tag)) ?: return null
        val value = obj.use { it.stream.readBytes().decodeToString().trim() }
        return Digest.parseOrNull(value)
    }

    /** Resolves a manifest reference, which is either a tag or a digest. */
    suspend fun resolveReference(repository: String, image: String, reference: String): Digest? =
        Digest.parseOrNull(reference) ?: resolveTag(repository, image, reference)

    suspend fun readManifest(repository: String, image: String, digest: Digest): Manifest? {
        val obj = storage.read(repository, DockerLayout.manifest(image, digest)) ?: return null
        val bytes = obj.use { it.stream.readBytes() }
        return Manifest(digest, manifestMediaType(repository, image, digest, bytes), bytes)
    }

    suspend fun manifestExists(repository: String, image: String, digest: Digest): Boolean =
        storage.exists(repository, DockerLayout.manifest(image, digest))

    suspend fun putManifest(
        repository: String,
        image: String,
        reference: String,
        mediaType: String,
        bytes: ByteArray,
    ): PutManifestResult {
        val digest = Digest.of(bytes)
        val requested = Digest.parseOrNull(reference)
        if (requested != null && requested != digest) {
            return PutManifestResult.Invalid("Provided digest did not match the uploaded manifest")
        }
        if (requested == null && !isValidTag(reference)) {
            return PutManifestResult.Invalid("Invalid tag")
        }

        val manifest = parseManifest(bytes) ?: return PutManifestResult.Invalid("Manifest is not valid JSON")
        missingReference(repository, image, manifest)?.let { return PutManifestResult.MissingBlob(it) }

        storeManifest(repository, image, digest, mediaType, bytes)
        if (requested == null) storeTag(repository, image, reference, digest)
        return PutManifestResult.Stored(digest)
    }

    /**
     * Stores a manifest as it arrived, without checking that its blobs are present. Pushes go through
     * [putManifest], which does check; a proxy repository caches the manifest first and pulls the blobs the
     * client actually asks for afterwards.
     */
    suspend fun storeManifest(repository: String, image: String, digest: Digest, mediaType: String, bytes: ByteArray) {
        storage.write(repository, DockerLayout.manifest(image, digest), bytes.inputStream())
        storage.write(
            repository,
            DockerLayout.manifestMediaType(image, digest),
            mediaType.toByteArray().inputStream(),
        )
    }

    suspend fun storeTag(repository: String, image: String, tag: String, digest: Digest) {
        storage.write(repository, DockerLayout.tag(image, tag), digest.toString().toByteArray().inputStream())
    }

    /** Removes a manifest and every tag pointing at it. */
    suspend fun deleteManifest(repository: String, image: String, digest: Digest): Boolean {
        val removed = storage.delete(repository, DockerLayout.manifest(image, digest))
        storage.delete(repository, DockerLayout.manifestMediaType(image, digest))
        for (tag in listTags(repository, image)) {
            if (resolveTag(repository, image, tag) == digest) storage.delete(repository, DockerLayout.tag(image, tag))
        }
        return removed
    }

    suspend fun deleteTag(repository: String, image: String, tag: String): Boolean =
        storage.delete(repository, DockerLayout.tag(image, tag))

    suspend fun deleteImage(repository: String, image: String): Boolean =
        storage.deleteDirectory(repository, DockerLayout.imageRoot(image))

    /**
     * Walks the image tree of a repository. A directory holding a `tags` child is an image; nested namespaces
     * (`team/api`) are reached by recursing into everything else.
     */
    suspend fun listImages(repository: String): List<String> {
        val images = ArrayList<String>()

        suspend fun walk(path: String, name: String) {
            val listing = storage.list(repository, path) ?: return
            if (name.isNotEmpty() && listing.any { it.directory && it.name == "tags" }) images += name
            for (entry in listing) {
                if (entry.directory && entry.name != "tags" && entry.name != "manifests") {
                    walk("$path/${entry.name}", if (name.isEmpty()) entry.name else "$name/${entry.name}")
                }
            }
        }

        walk(DockerLayout.IMAGES, "")
        return images.sorted()
    }

    internal suspend fun parsedManifest(repository: String, image: String, digest: Digest): Pair<Manifest, OciManifest>? {
        val manifest = readManifest(repository, image, digest) ?: return null
        val parsed = parseManifest(manifest.bytes) ?: return null
        return manifest to parsed
    }

    internal suspend fun readImageConfig(repository: String, digest: Digest): OciImageConfig? =
        readBlob(repository, digest)?.use { parseImageConfig(it.stream) }

    /** Returns the digest of the first referenced blob or child manifest that has not been uploaded yet. */
    private suspend fun missingReference(repository: String, image: String, manifest: OciManifest): String? {
        if (manifest.index) {
            for (child in manifest.manifests) {
                val digest = Digest.parseOrNull(child.digest) ?: return child.digest
                if (!manifestExists(repository, image, digest) && !blobExists(repository, digest)) return child.digest
            }
            return null
        }
        val descriptors = listOfNotNull(manifest.config) + manifest.layers
        for (descriptor in descriptors) {
            if (descriptor.external) continue
            val digest = Digest.parseOrNull(descriptor.digest) ?: return descriptor.digest
            if (!blobExists(repository, digest)) return descriptor.digest
        }
        return null
    }

    private suspend fun manifestMediaType(
        repository: String,
        image: String,
        digest: Digest,
        bytes: ByteArray,
    ): String {
        val stored = storage.read(repository, DockerLayout.manifestMediaType(image, digest))
            ?.use { it.stream.readBytes().decodeToString().trim() }
        if (!stored.isNullOrEmpty()) return stored

        // Manifests pushed before the media type was recorded: fall back to what the document itself says.
        val parsed = parseManifest(bytes)
        return parsed?.mediaType
            ?: if (parsed?.index == true) MediaTypes.OCI_INDEX else MediaTypes.OCI_MANIFEST
    }
}
