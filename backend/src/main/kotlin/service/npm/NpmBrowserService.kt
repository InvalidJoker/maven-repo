package de.joker.service.npm

import de.joker.model.NpmPackageDetailDto
import de.joker.model.NpmPackageDto
import de.joker.model.NpmVersionDetailDto
import de.joker.model.NpmVersionDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Read-only view of an npm repository for the web UI. */
class NpmBrowserService(private val registry: NpmRegistryService) {

    suspend fun packages(repository: String): List<NpmPackageDto> =
        registry.listPackages(repository).mapNotNull { name ->
            val stored = registry.packument(repository, name) ?: return@mapNotNull null
            val latest = stored.latest
            NpmPackageDto(
                name = name,
                versions = stored.versions.size,
                latest = latest,
                description = latest?.let { stored.versions[it]?.string("description") },
                modified = stored.time["modified"],
            )
        }

    suspend fun packageDetail(repository: String, name: String): NpmPackageDetailDto? {
        val stored = registry.packument(repository, name) ?: return null
        return NpmPackageDetailDto(
            name = name,
            description = stored.latest?.let { stored.versions[it]?.string("description") },
            distTags = stored.distTags,
            versions = stored.versions.keys
                .sortedWith(VERSION_ORDER.reversed())
                .map { NpmVersionDto(it, stored.time[it], stored.tagsOf(it)) },
        )
    }

    suspend fun versionDetail(repository: String, name: String, reference: String): NpmVersionDetailDto? {
        val stored = registry.packument(repository, name) ?: return null
        val version = stored.distTags[reference] ?: reference
        val manifest = stored.versions[version] ?: return null
        val dist = manifest["dist"] as? JsonObject
        val file = dist?.string("tarball") ?: tarballNameFor(name, version)

        return NpmVersionDetailDto(
            name = name,
            version = version,
            description = manifest.string("description"),
            license = manifest.string("license"),
            homepage = manifest.string("homepage"),
            published = stored.time[version],
            tarball = file,
            tarballSize = registry.tarballSize(repository, name, file),
            shasum = dist?.string("shasum"),
            integrity = dist?.string("integrity"),
            tags = stored.tagsOf(version),
            keywords = manifest.stringList("keywords"),
            dependencies = manifest.stringMap("dependencies"),
            devDependencies = manifest.stringMap("devDependencies"),
        )
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

private fun JsonObject.stringMap(key: String): Map<String, String> =
    (this[key] as? JsonObject)
        ?.mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull.orEmpty() }
        .orEmpty()
