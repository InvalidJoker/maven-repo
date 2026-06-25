package de.joker.service

import de.joker.model.ArtifactInfo
import de.joker.model.BrowseEntry
import de.joker.model.BrowseResponse
import de.joker.model.VersionInfo
import java.io.File

/**
 * Turns a repository directory into a [BrowseResponse]: a sorted listing plus inferred Maven
 * coordinates so the UI can show install instructions. Detection follows the standard layout
 * `group/parts/artifactId/version/files`.
 */
class RepositoryBrowserService(private val storage: RepositoryStorageService) {

    fun browse(repository: String, path: String): BrowseResponse? {
        val files = storage.listDirectory(repository, path) ?: return null
        val segments = path.split('/').filter { it.isNotEmpty() }

        val entries = files
            .sortedWith(
                compareByDescending<File> { it.isDirectory }
                    .thenComparator { a, b -> compareEntryNames(a.name, b.name) },
            )
            .map { BrowseEntry(it.name, it.isDirectory, if (it.isFile) it.length() else null) }

        val versionDirs = files.filter { it.isDirectory && isVersion(it.name) }.map { it.name }

        var artifact: ArtifactInfo? = null
        var version: VersionInfo? = null

        if (segments.size >= 2 && versionDirs.isNotEmpty()) {
            // e.g. com/example/mylib -> groupId=com.example, artifactId=mylib
            artifact = ArtifactInfo(
                groupId = segments.dropLast(1).joinToString("."),
                artifactId = segments.last(),
                versions = versionDirs.sortedWith(VERSION_COMPARATOR.reversed()),
                latestVersion = latestVersion(versionDirs),
            )
        } else if (segments.size >= 3 && isVersion(segments.last()) && files.any { it.isFile }) {
            // e.g. com/example/mylib/1.0.0 -> version=1.0.0, artifactId=mylib
            version = VersionInfo(
                groupId = segments.dropLast(2).joinToString("."),
                artifactId = segments[segments.size - 2],
                version = segments.last(),
            )
        }

        return BrowseResponse(repository, path, entries, artifact, version)
    }

    /** Highest version, preferring releases over SNAPSHOTs. */
    private fun latestVersion(versions: List<String>): String {
        val releases = versions.filterNot { it.contains("SNAPSHOT", ignoreCase = true) }
        val pool = releases.ifEmpty { versions }
        return pool.maxWithOrNull(VERSION_COMPARATOR) ?: versions.first()
    }

    private fun compareEntryNames(a: String, b: String): Int =
        if (isVersion(a) && isVersion(b)) VERSION_COMPARATOR.reversed().compare(a, b) else a.compareTo(b)

    private fun isVersion(name: String): Boolean = name.isNotEmpty() && name[0].isDigit()

    companion object {
        /** Compares dotted/dashed versions numerically segment-by-segment. */
        val VERSION_COMPARATOR: Comparator<String> = Comparator { a, b ->
            val ta = a.split('.', '-')
            val tb = b.split('.', '-')
            for (i in 0 until maxOf(ta.size, tb.size)) {
                val x = ta.getOrNull(i) ?: "0"
                val y = tb.getOrNull(i) ?: "0"
                val xi = x.toIntOrNull()
                val yi = y.toIntOrNull()
                val cmp = if (xi != null && yi != null) xi.compareTo(yi) else x.compareTo(y)
                if (cmp != 0) return@Comparator cmp
            }
            0
        }
    }
}
