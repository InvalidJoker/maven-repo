package de.joker.service.storage

import java.io.Closeable
import java.io.InputStream
import java.time.Instant

data class StorageEntry(
    val name: String,
    val directory: Boolean,
    val size: Long?,
    val lastModified: Instant? = null,
)

class StorageObject(val size: Long?, val stream: InputStream) : Closeable {
    override fun close() = stream.close()
}

interface StorageBackend {
    suspend fun list(repository: String, path: String): List<StorageEntry>?

    suspend fun read(repository: String, path: String): StorageObject?

    suspend fun exists(repository: String, path: String): Boolean

    suspend fun write(repository: String, path: String, input: InputStream): Boolean

    suspend fun delete(repository: String, path: String): Boolean

    /** Removes a directory and everything below it. Returns false when the directory does not exist. */
    suspend fun deleteDirectory(repository: String, path: String): Boolean
}
