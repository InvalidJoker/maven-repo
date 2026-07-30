package de.joker.service.docker

import de.joker.model.DockerImageDto
import de.joker.model.DockerLayerDto
import de.joker.model.DockerManifestDto
import de.joker.model.DockerPlatformDto
import de.joker.model.DockerTagDto

/** Read-only view of a Docker repository for the web UI and the `/v2/_catalog` endpoint. */
class DockerBrowserService(private val registry: DockerRegistryService) {

    suspend fun images(repository: String): List<DockerImageDto> =
        registry.listImages(repository).map { image ->
            val tags = registry.listTags(repository, image)
            val lastPushed = tags.mapNotNull { registry.tagPushedAt(repository, image, it) }.maxOrNull()
            DockerImageDto(image, tags.size, lastPushed?.toString())
        }

    suspend fun tags(repository: String, image: String): List<DockerTagDto> =
        registry.listTags(repository, image).map { tag ->
            DockerTagDto(
                tag = tag,
                digest = registry.resolveTag(repository, image, tag)?.toString(),
                pushedAt = registry.tagPushedAt(repository, image, tag)?.toString(),
            )
        }

    suspend fun manifest(repository: String, image: String, reference: String): DockerManifestDto? {
        val digest = registry.resolveReference(repository, image, reference) ?: return null
        val (manifest, parsed) = registry.parsedManifest(repository, image, digest) ?: return null

        val layers = parsed.layers.map { DockerLayerDto(it.digest, it.size, it.mediaType) }
        val platforms = parsed.manifests.map {
            DockerPlatformDto(it.digest, it.platform?.os, it.platform?.architecture, it.platform?.variant)
        }
        val config = parsed.config?.digest
            ?.let { Digest.parseOrNull(it) }
            ?.let { registry.readImageConfig(repository, it) }

        return DockerManifestDto(
            image = image,
            reference = reference,
            digest = digest.toString(),
            mediaType = manifest.mediaType,
            manifestSize = manifest.bytes.size.toLong(),
            totalSize = layers.sumOf { it.size } + (parsed.config?.size ?: 0) +
                parsed.manifests.sumOf { it.size },
            created = config?.created,
            os = config?.os,
            architecture = config?.architecture,
            layers = layers,
            platforms = platforms,
            labels = config?.config?.labels.orEmpty(),
        )
    }

    /** `<repository>/<image>` names, the form the Docker CLI uses. */
    suspend fun catalog(repositories: List<String>): List<String> =
        repositories.flatMap { repository -> registry.listImages(repository).map { "$repository/$it" } }.sorted()
}
