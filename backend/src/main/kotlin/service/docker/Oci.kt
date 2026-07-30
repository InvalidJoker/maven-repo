package de.joker.service.docker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Content-addressable identifier of a blob or manifest, e.g. `sha256:ab12…`. */
data class Digest(val algorithm: String, val hex: String) {
    override fun toString(): String = "$algorithm:$hex"

    companion object {
        private val PATTERN = Regex("^(sha256|sha512):([a-f0-9]{32,128})$")

        fun parseOrNull(value: String): Digest? =
            PATTERN.matchEntire(value)?.let { Digest(it.groupValues[1], it.groupValues[2]) }

        fun of(bytes: ByteArray): Digest = Digest("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).hex())

        fun of(file: File): Digest {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return Digest("sha256", digest.digest().hex())
        }

        private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    }
}

/**
 * Names in the OCI distribution spec are `<repository>/<image>`, where the first segment selects one of our
 * repositories and the rest is the image path inside it (`docker push host/releases/team/api:1.0`).
 */
data class ImageName(val repository: String, val image: String) {
    override fun toString(): String = "$repository/$image"

    companion object {
        // https://github.com/opencontainers/distribution-spec — one component of a repository name.
        private val COMPONENT = Regex("[a-z0-9]+((\\.|_|__|-+)[a-z0-9]+)*")

        fun parseOrNull(segments: List<String>): ImageName? {
            if (segments.size < 2) return null
            if (segments.any { !COMPONENT.matches(it) }) return null
            return ImageName(segments.first(), segments.drop(1).joinToString("/"))
        }
    }
}

private val TAG_PATTERN = Regex("[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}")

fun isValidTag(tag: String): Boolean = TAG_PATTERN.matches(tag)

/** Storage paths, relative to the repository root of a [de.joker.service.storage.StorageBackend]. */
object DockerLayout {
    const val IMAGES = "images"

    fun blob(digest: Digest): String = "blobs/${digest.algorithm}/${digest.hex}"

    fun manifest(image: String, digest: Digest): String = "$IMAGES/$image/manifests/${digest.algorithm}/${digest.hex}"

    /** The media type a manifest was pushed with, which cannot be recovered from the manifest bytes alone. */
    fun manifestMediaType(image: String, digest: Digest): String = manifest(image, digest) + ".mediatype"

    fun tags(image: String): String = "$IMAGES/$image/tags"

    fun tag(image: String, tag: String): String = "${tags(image)}/$tag"

    fun imageRoot(image: String): String = "$IMAGES/$image"
}

object MediaTypes {
    const val OCI_MANIFEST = "application/vnd.oci.image.manifest.v1+json"
    const val OCI_INDEX = "application/vnd.oci.image.index.v1+json"
    const val DOCKER_MANIFEST = "application/vnd.docker.distribution.manifest.v2+json"
    const val DOCKER_MANIFEST_LIST = "application/vnd.docker.distribution.manifest.list.v2+json"

    val MANIFEST_TYPES = setOf(OCI_MANIFEST, OCI_INDEX, DOCKER_MANIFEST, DOCKER_MANIFEST_LIST)
}

internal val ociJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class OciDescriptor(
    val mediaType: String = "",
    val digest: String = "",
    val size: Long = 0,
    val platform: OciPlatform? = null,
    val urls: List<String> = emptyList(),
) {
    /** Layers hosted elsewhere (foreign/non-distributable) are referenced but never uploaded. */
    val external: Boolean get() = urls.isNotEmpty() || "foreign" in mediaType || "nondistributable" in mediaType
}

@Serializable
internal data class OciPlatform(
    val os: String? = null,
    val architecture: String? = null,
    val variant: String? = null,
)

@Serializable
internal data class OciManifest(
    val schemaVersion: Int = 2,
    val mediaType: String? = null,
    val config: OciDescriptor? = null,
    val layers: List<OciDescriptor> = emptyList(),
    val manifests: List<OciDescriptor> = emptyList(),
    val annotations: Map<String, String> = emptyMap(),
) {
    val index: Boolean get() = manifests.isNotEmpty() || mediaType in setOf(MediaTypes.OCI_INDEX, MediaTypes.DOCKER_MANIFEST_LIST)
}

/** The image config blob an image manifest points at; carries the metadata shown in the UI. */
@Serializable
internal data class OciImageConfig(
    val created: String? = null,
    val architecture: String? = null,
    val os: String? = null,
    val config: OciImageConfigInner? = null,
)

@Serializable
internal data class OciImageConfigInner(
    @SerialName("Labels") val labels: Map<String, String>? = null,
)

internal fun parseManifest(bytes: ByteArray): OciManifest? =
    runCatching { ociJson.decodeFromString(OciManifest.serializer(), bytes.decodeToString()) }.getOrNull()

internal fun parseImageConfig(stream: InputStream): OciImageConfig? =
    runCatching { ociJson.decodeFromString(OciImageConfig.serializer(), stream.readBytes().decodeToString()) }.getOrNull()
