package de.joker.service.storage

import de.joker.service.StorageBackend
import de.joker.service.StorageEntry
import de.joker.service.StorageObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class LocalStorageBackend(rootPath: String) : StorageBackend {

    private val root: File = File(rootPath).absoluteFile

    private fun fileFor(repository: String, path: String): File? {
        val segments = path.split('/', '\\').filter { it.isNotEmpty() }
        if (segments.any { it == ".." || it == "." }) return null

        val repoRoot = File(root, repository)
        val target = if (segments.isEmpty()) repoRoot else File(repoRoot, segments.joinToString(File.separator))

        val basePath = repoRoot.canonicalFile.path
        val targetPath = target.canonicalFile.path
        if (targetPath != basePath && !targetPath.startsWith(basePath + File.separator)) return null

        return target
    }

    override suspend fun list(repository: String, path: String): List<StorageEntry>? = withContext(Dispatchers.IO) {
        val dir = fileFor(repository, path) ?: return@withContext null
        if (!dir.isDirectory) return@withContext null
        dir.listFiles()?.map { StorageEntry(it.name, it.isDirectory, if (it.isFile) it.length() else null) }
            ?: emptyList()
    }

    override suspend fun read(repository: String, path: String): StorageObject? = withContext(Dispatchers.IO) {
        val file = fileFor(repository, path)?.takeIf { it.isFile } ?: return@withContext null
        StorageObject(file.length(), file.inputStream())
    }

    override suspend fun exists(repository: String, path: String): Boolean = withContext(Dispatchers.IO) {
        fileFor(repository, path)?.isFile == true
    }

    override suspend fun write(repository: String, path: String, input: InputStream): Boolean =
        withContext(Dispatchers.IO) {
            val file = fileFor(repository, path) ?: return@withContext false
            file.parentFile?.mkdirs()
            file.outputStream().use { output -> input.copyTo(output) }
            true
        }
}