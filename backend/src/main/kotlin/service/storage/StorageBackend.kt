package de.joker.service.storage

import java.io.Closeable
import java.io.InputStream

data class StorageEntry(val name: String, val directory: Boolean, val size: Long?)

class StorageObject(val size: Long?, val stream: InputStream) : Closeable {
    override fun close() = stream.close()
}

/**
 * Abstraction over where artifacts are physically stored. Implementations exist for the local
 * filesystem and S3-compatible object stores; the active one is chosen from `StorageConfig`.
 *
 * Paths are repository-relative (the leading `<repository>/` segment is handled by the backend),
 * always use `/` as separator, and must not be able to escape their repository.
 */
interface StorageBackend {
    /** Lists a directory's immediate children, or null if it does not exist / is not a directory. */
    suspend fun list(repository: String, path: String): List<StorageEntry>?

    /** Opens an artifact for reading, or null if it does not exist. */
    suspend fun read(repository: String, path: String): StorageObject?

    /** Whether an artifact (file) exists at the path. */
    suspend fun exists(repository: String, path: String): Boolean

    /** Stores an artifact, returning false if the path is invalid. */
    suspend fun write(repository: String, path: String, input: InputStream): Boolean
}
