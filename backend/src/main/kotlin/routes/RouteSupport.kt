package de.joker.routes

import de.joker.auth.Permission
import de.joker.auth.RepoAccess
import de.joker.auth.RepositoryAccess
import de.joker.model.RepositoryDto
import de.joker.model.RepositoryType
import de.joker.service.storage.StorageObject
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Streams a stored artifact to the client, advertising its length so clients can show progress. */
suspend fun ApplicationCall.respondStorageObject(obj: StorageObject, contentType: ContentType) {
    respondBytesWriter(contentType, HttpStatusCode.OK, obj.size) {
        obj.use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = withContext(Dispatchers.IO) { source.stream.read(buffer) }
                if (read <= 0) break
                writeFully(buffer, 0, read)
            }
        }
        flush()
    }
}

/** Response for `HEAD`, which must carry the headers of the `GET` it mirrors but no body. */
suspend fun ApplicationCall.respondHeadersOnly(type: ContentType, length: Long?) {
    respond(
        object : OutgoingContent.NoContent() {
            override val status: HttpStatusCode = HttpStatusCode.OK
            override val contentType: ContentType = type
            override val contentLength: Long? = length
        },
    )
}

/**
 * Repository lookup for the browser API. Unlike the Maven and Docker endpoints, which challenge for credentials,
 * the UI is already signed in (or not) — anything the caller may not see is simply reported as missing.
 */
suspend fun ApplicationCall.repositoryOrNotFound(
    access: RepositoryAccess,
    required: Permission = Permission.READ,
    type: RepositoryType? = null,
): RepositoryDto? {
    val name = parameters["repo"]!!
    return when (val result = access.check(this, name, required, type)) {
        is RepoAccess.Granted -> result.repository
        is RepoAccess.Denied -> {
            if (result.reason == RepoAccess.Reason.FORBIDDEN) {
                respond(HttpStatusCode.Forbidden, mapOf("error" to "Insufficient permissions"))
            } else {
                respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
            }
            null
        }
    }
}
