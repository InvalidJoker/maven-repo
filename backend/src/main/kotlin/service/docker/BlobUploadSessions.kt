package de.joker.service.docker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-flight `POST/PATCH/PUT` blob uploads. Chunks are appended to a local scratch file and only the finished blob
 * is handed to the storage backend, which has no append operation. Sessions are therefore bound to this process
 * and are discarded on restart — clients restart the upload, which the distribution spec allows.
 */
class BlobUploadSessions(rootPath: String) {

    private val root = File(rootPath).absoluteFile
    private val sessions = ConcurrentHashMap<String, Session>()

    init {
        root.deleteRecursively()
        root.mkdirs()
    }

    class Session(val id: String, val repository: String, val file: File) {
        private val mutex = Mutex()

        val size: Long get() = file.length()

        /** Appends a chunk and returns the new total size. Serialized so concurrent PATCHes cannot interleave. */
        suspend fun append(input: InputStream): Long = mutex.withLock {
            withContext(Dispatchers.IO) {
                FileOutputStream(file, true).buffered().use { output -> input.copyTo(output) }
                file.length()
            }
        }
    }

    fun start(repository: String): Session {
        val id = UUID.randomUUID().toString()
        val session = Session(id, repository, File(root, id).apply { createNewFile() })
        sessions[id] = session
        return session
    }

    fun find(id: String, repository: String): Session? = sessions[id]?.takeIf { it.repository == repository }

    fun discard(id: String) {
        sessions.remove(id)?.file?.delete()
    }
}
