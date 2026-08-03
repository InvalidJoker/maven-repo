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
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * Writes JSON explicitly rather than through content negotiation. Registry clients send `Accept` headers that
 * list only their own media types, which negotiation answers with `406` — hiding even the error body.
 */
suspend inline fun <reified T> ApplicationCall.respondJson(
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondText(Json.encodeToString(value), ContentType.Application.Json, status)
}

/** Reads at most [limit] bytes of a request body, or null when it is larger. */
suspend fun InputStream.readBounded(limit: Int): ByteArray? = withContext(Dispatchers.IO) {
    val bytes = readNBytes(limit + 1)
    if (bytes.size > limit) null else bytes
}

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
