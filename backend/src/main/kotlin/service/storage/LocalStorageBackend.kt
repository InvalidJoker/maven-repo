package de.joker.service.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

class LocalStorageBackend(rootPath: String) : StorageBackend {

    private val root: File = File(rootPath).absoluteFile

    /** In-flight [write] temporaries, which must stay invisible to listings. */
    private fun isPartial(name: String): Boolean = name.startsWith(".") && name.endsWith(".part")

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
        dir.listFiles()?.filter { !isPartial(it.name) }?.map {
            StorageEntry(
                name = it.name,
                directory = it.isDirectory,
                size = if (it.isFile) it.length() else null,
                lastModified = Instant.ofEpochMilli(it.lastModified()),
            )
        } ?: emptyList()
    }

    override suspend fun read(repository: String, path: String): StorageObject? = withContext(Dispatchers.IO) {
        val file = fileFor(repository, path)?.takeIf { it.isFile } ?: return@withContext null
        StorageObject(file.length(), file.inputStream())
    }

    override suspend fun stat(repository: String, path: String): StorageEntry? = withContext(Dispatchers.IO) {
        val file = fileFor(repository, path)?.takeIf { it.isFile } ?: return@withContext null
        StorageEntry(file.name, directory = false, size = file.length(), lastModified = Instant.ofEpochMilli(file.lastModified()))
    }

    override suspend fun exists(repository: String, path: String): Boolean = withContext(Dispatchers.IO) {
        fileFor(repository, path)?.isFile == true
    }

    /**
     * Writes through a temporary file and moves it into place, so a reader never observes a half-written
     * artifact — proxy repositories fill the cache while clients are already asking for the same path.
     */
    override suspend fun write(repository: String, path: String, input: InputStream): Boolean =
        withContext(Dispatchers.IO) {
            val file = fileFor(repository, path) ?: return@withContext false
            val parent = file.parentFile ?: return@withContext false
            parent.mkdirs()

            val temp = File.createTempFile(".${file.name}", ".part", parent)
            try {
                temp.outputStream().use { output -> input.copyTo(output) }
                try {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                true
            } finally {
                temp.delete()
            }
        }

    override suspend fun delete(repository: String, path: String): Boolean = withContext(Dispatchers.IO) {
        val file = fileFor(repository, path)?.takeIf { it.isFile } ?: return@withContext false
        file.delete()
    }

    override suspend fun deleteDirectory(repository: String, path: String): Boolean = withContext(Dispatchers.IO) {
        val dir = fileFor(repository, path)?.takeIf { it.isDirectory } ?: return@withContext false
        dir.deleteRecursively()
    }

    override suspend fun renameRepository(from: String, to: String): Boolean = withContext(Dispatchers.IO) {
        val source = fileFor(from, "") ?: return@withContext false
        val target = fileFor(to, "") ?: return@withContext false
        if (!source.isDirectory) return@withContext true
        if (target.exists()) return@withContext false
        target.parentFile?.mkdirs()
        source.renameTo(target)
    }
}
