package de.joker.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Stores and serves artifact files on disk, one directory tree per repository. */
class RepositoryStorageService(rootPath: String) {

    private val root: File = File(rootPath).absoluteFile

    /**
     * Resolves an artifact path within a repository, guarding against path traversal.
     * Returns null if the path escapes the repository directory.
     */
    fun fileFor(repository: String, path: String): File? {
        val segments = path.split('/', '\\').filter { it.isNotEmpty() }
        if (segments.any { it == ".." || it == "." }) return null

        val repoRoot = File(root, repository)
        val target = if (segments.isEmpty()) repoRoot else File(repoRoot, segments.joinToString(File.separator))

        val basePath = repoRoot.canonicalFile.path
        val targetPath = target.canonicalFile.path
        if (targetPath != basePath && !targetPath.startsWith(basePath + File.separator)) return null

        return target
    }

    /** Lists the contents of a directory, or null if the path is invalid or not a directory. */
    fun listDirectory(repository: String, path: String): List<File>? {
        val dir = fileFor(repository, path) ?: return null
        if (!dir.isDirectory) return null
        return dir.listFiles()?.toList() ?: emptyList()
    }

    /** Writes an uploaded artifact; returns false if the path is invalid. */
    suspend fun write(repository: String, path: String, input: InputStream): Boolean = withContext(Dispatchers.IO) {
        val file = fileFor(repository, path) ?: return@withContext false
        file.parentFile?.mkdirs()
        file.outputStream().use { output -> input.copyTo(output) }
        true
    }
}
