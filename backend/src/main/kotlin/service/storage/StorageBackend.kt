package de.joker.service.storage

import java.io.Closeable
import java.io.InputStream

data class StorageEntry(val name: String, val directory: Boolean, val size: Long?)

class StorageObject(val size: Long?, val stream: InputStream) : Closeable {
    override fun close() = stream.close()
}

interface StorageBackend {
    suspend fun list(repository: String, path: String): List<StorageEntry>?

    suspend fun read(repository: String, path: String): StorageObject?

    suspend fun exists(repository: String, path: String): Boolean

    suspend fun write(repository: String, path: String, input: InputStream): Boolean
}
