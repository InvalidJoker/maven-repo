package de.joker.routes

import de.joker.auth.AuthResult
import de.joker.auth.Permission
import de.joker.auth.RepoAccess
import de.joker.auth.RepositoryAccess
import de.joker.model.RepositoryDto
import de.joker.model.RepositoryType
import de.joker.service.docker.BlobUploadSessions
import de.joker.service.docker.Digest
import de.joker.service.docker.DockerBrowserService
import de.joker.service.docker.DockerRegistryService
import de.joker.service.docker.ImageName
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

private const val DOCKER_CONTENT_DIGEST = "Docker-Content-Digest"
private const val DOCKER_UPLOAD_UUID = "Docker-Upload-UUID"
private const val API_VERSION = "Docker-Distribution-Api-Version"
private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024

/**
 * OCI distribution API (`/v2`), the protocol `docker`, `podman`, `buildx` and `skopeo` speak.
 *
 * Image names are `<repository>/<image>`: the first segment picks one of our Docker repositories, the rest is the
 * image path inside it. Since the spec puts the verb *after* a variable-length name (`/v2/<name>/blobs/<digest>`),
 * which Ktor cannot express as a route, every request goes through one tailcard route and is parsed here.
 */
fun Route.dockerRegistryRoutes(
    access: RepositoryAccess,
    registry: DockerRegistryService,
    uploads: BlobUploadSessions,
    browser: DockerBrowserService,
) {
    val api = DockerRegistryApi(access, registry, uploads, browser)

    route("/v2") {
        get { api.ping(call) }
        route("/{path...}") {
            get { api.onGet(call, call.segments(), body = true) }
            head { api.onGet(call, call.segments(), body = false) }
            post { api.onPost(call, call.segments()) }
            patch { api.onPatch(call, call.segments()) }
            put { api.onPut(call, call.segments()) }
            delete { api.onDelete(call, call.segments()) }
        }
    }
}

private fun ApplicationCall.segments(): List<String> =
    parameters.getAll("path").orEmpty().filter { it.isNotEmpty() }

private sealed interface Target {
    val name: ImageName

    data class Manifest(override val name: ImageName, val reference: String) : Target
    data class Blob(override val name: ImageName, val digest: String) : Target
    data class UploadStart(override val name: ImageName) : Target
    data class UploadSession(override val name: ImageName, val id: String) : Target
    data class Tags(override val name: ImageName) : Target
}

/**
 * Splits `<name…>/<verb>/<reference>` from the end, because the name is the variable-length part. An image
 * component may legally be called `blobs` or `manifests`, so only the trailing occurrence is treated as the verb.
 */
private fun parseTarget(segments: List<String>): Target? {
    val size = segments.size
    fun name(drop: Int) = ImageName.parseOrNull(segments.dropLast(drop))

    return when {
        size >= 4 && segments[size - 3] == "blobs" && segments[size - 2] == "uploads" ->
            name(3)?.let { Target.UploadSession(it, segments.last()) }

        size >= 3 && segments[size - 2] == "blobs" && segments.last() == "uploads" ->
            name(2)?.let { Target.UploadStart(it) }

        size >= 3 && segments[size - 2] == "blobs" ->
            name(2)?.let { Target.Blob(it, segments.last()) }

        size >= 3 && segments[size - 2] == "manifests" ->
            name(2)?.let { Target.Manifest(it, segments.last()) }

        size >= 3 && segments[size - 2] == "tags" && segments.last() == "list" ->
            name(2)?.let { Target.Tags(it) }

        else -> null
    }
}

private class DockerRegistryApi(
    private val access: RepositoryAccess,
    private val registry: DockerRegistryService,
    private val uploads: BlobUploadSessions,
    private val browser: DockerBrowserService,
) {

    suspend fun ping(call: ApplicationCall) {
        when (access.authenticate(call)) {
            is AuthResult.User, AuthResult.Anonymous -> {
                call.response.header(API_VERSION, "registry/2.0")
                call.respondText("{}", ContentType.Application.Json)
            }

            else -> call.unauthorized(name = null, required = null)
        }
    }

    suspend fun onGet(call: ApplicationCall, segments: List<String>, body: Boolean) {
        when {
            segments.isEmpty() -> ping(call)
            segments == listOf("token") -> token(call)
            segments == listOf("_catalog") -> catalog(call)
            else -> when (val target = parseTarget(segments)) {
                is Target.Manifest -> getManifest(call, target, body)
                is Target.Blob -> getBlob(call, target, body)
                is Target.Tags -> tagList(call, target)
                is Target.UploadSession -> uploadStatus(call, target)
                else -> call.unsupported()
            }
        }
    }

    suspend fun onPost(call: ApplicationCall, segments: List<String>) {
        when (val target = parseTarget(segments)) {
            is Target.UploadStart -> startUpload(call, target)
            else -> call.unsupported()
        }
    }

    suspend fun onPatch(call: ApplicationCall, segments: List<String>) {
        when (val target = parseTarget(segments)) {
            is Target.UploadSession -> patchUpload(call, target)
            else -> call.unsupported()
        }
    }

    suspend fun onPut(call: ApplicationCall, segments: List<String>) {
        when (val target = parseTarget(segments)) {
            is Target.UploadSession -> finishUpload(call, target)
            is Target.Manifest -> putManifest(call, target)
            else -> call.unsupported()
        }
    }

    suspend fun onDelete(call: ApplicationCall, segments: List<String>) {
        when (val target = parseTarget(segments)) {
            is Target.Manifest -> deleteManifest(call, target)
            is Target.Blob -> deleteBlob(call, target)
            is Target.UploadSession -> cancelUpload(call, target)
            else -> call.unsupported()
        }
    }

    private suspend fun token(call: ApplicationCall) {
        val principal = if (call.request.headers.contains(HttpHeaders.Authorization)) {
            access.verifyBasic(call) ?: return call.ociError(
                HttpStatusCode.Unauthorized,
                "UNAUTHORIZED",
                "Invalid username or access token",
            )
        } else {
            // Anonymous token: public repositories can still be pulled after the handshake.
            null
        }

        val issued = access.issueToken(principal)
        call.respondOci(
            TokenResponse(
                token = issued.token,
                accessToken = issued.token,
                expiresIn = issued.expiresInSeconds,
                issuedAt = issued.issuedAt.toString(),
            ),
        )
    }

    private suspend fun catalog(call: ApplicationCall) {
        val repositories = access.readable(call, RepositoryType.DOCKER).map { it.name }
        call.respondOci(CatalogResponse(browser.catalog(repositories)))
    }

    private suspend fun tagList(call: ApplicationCall, target: Target.Tags) {
        val repo = authorize(call, target.name, Permission.READ) ?: return
        val tags = registry.listTags(repo.name, target.name.image)
        val limit = call.request.queryParameters["n"]?.toIntOrNull()
        call.respondOci(TagListResponse(target.name.toString(), if (limit != null) tags.take(limit) else tags))
    }

    private suspend fun getManifest(call: ApplicationCall, target: Target.Manifest, body: Boolean) {
        val repo = authorize(call, target.name, Permission.READ) ?: return
        val digest = registry.resolveReference(repo.name, target.name.image, target.reference)
        val manifest = digest?.let { registry.readManifest(repo.name, target.name.image, it) }
            ?: return call.ociError(HttpStatusCode.NotFound, "MANIFEST_UNKNOWN", "Manifest unknown")

        call.response.header(DOCKER_CONTENT_DIGEST, manifest.digest.toString())
        val contentType = ContentType.parse(manifest.mediaType)
        if (body) {
            call.respondBytes(manifest.bytes, contentType)
        } else {
            call.respondHeadersOnly(contentType, manifest.bytes.size.toLong())
        }
    }

    private suspend fun putManifest(call: ApplicationCall, target: Target.Manifest) {
        val repo = authorize(call, target.name, Permission.WRITE) ?: return
        val bytes = call.receiveStream().readBounded(MAX_MANIFEST_BYTES)
            ?: return call.ociError(HttpStatusCode.PayloadTooLarge, "MANIFEST_INVALID", "Manifest is too large")

        val mediaType = call.request.contentType().withoutParameters().toString()
        val result = registry.putManifest(repo.name, target.name.image, target.reference, mediaType, bytes)
        when (result) {
            is DockerRegistryService.PutManifestResult.Stored -> {
                call.response.header(HttpHeaders.Location, "/v2/${target.name}/manifests/${result.digest}")
                call.response.header(DOCKER_CONTENT_DIGEST, result.digest.toString())
                call.respond(HttpStatusCode.Created)
            }

            is DockerRegistryService.PutManifestResult.Invalid ->
                call.ociError(HttpStatusCode.BadRequest, "MANIFEST_INVALID", result.message)

            is DockerRegistryService.PutManifestResult.MissingBlob ->
                call.ociError(
                    HttpStatusCode.BadRequest,
                    "MANIFEST_BLOB_UNKNOWN",
                    "Referenced blob ${result.digest} has not been uploaded",
                )
        }
    }

    private suspend fun deleteManifest(call: ApplicationCall, target: Target.Manifest) {
        val repo = authorize(call, target.name, Permission.WRITE) ?: return
        val digest = Digest.parseOrNull(target.reference)
        val removed = if (digest != null) {
            registry.deleteManifest(repo.name, target.name.image, digest)
        } else {
            registry.deleteTag(repo.name, target.name.image, target.reference)
        }
        if (removed) {
            call.respond(HttpStatusCode.Accepted)
        } else {
            call.ociError(HttpStatusCode.NotFound, "MANIFEST_UNKNOWN", "Manifest unknown")
        }
    }

    private suspend fun getBlob(call: ApplicationCall, target: Target.Blob, body: Boolean) {
        val repo = authorize(call, target.name, Permission.READ) ?: return
        val digest = Digest.parseOrNull(target.digest)
            ?: return call.ociError(HttpStatusCode.BadRequest, "DIGEST_INVALID", "Invalid digest")

        val blob = registry.readBlob(repo.name, digest)
            ?: return call.ociError(HttpStatusCode.NotFound, "BLOB_UNKNOWN", "Blob unknown")

        call.response.header(DOCKER_CONTENT_DIGEST, digest.toString())
        if (body) {
            call.respondStorageObject(blob, ContentType.Application.OctetStream)
        } else {
            blob.close()
            call.respondHeadersOnly(ContentType.Application.OctetStream, blob.size)
        }
    }

    private suspend fun deleteBlob(call: ApplicationCall, target: Target.Blob) {
        val repo = authorize(call, target.name, Permission.WRITE) ?: return
        val digest = Digest.parseOrNull(target.digest)
            ?: return call.ociError(HttpStatusCode.BadRequest, "DIGEST_INVALID", "Invalid digest")

        if (registry.deleteBlob(repo.name, digest)) {
            call.respond(HttpStatusCode.Accepted)
        } else {
            call.ociError(HttpStatusCode.NotFound, "BLOB_UNKNOWN", "Blob unknown")
        }
    }

    private suspend fun startUpload(call: ApplicationCall, target: Target.UploadStart) {
        val repo = authorize(call, target.name, Permission.WRITE) ?: return
        val parameters = call.request.queryParameters

        // Cross-repository mount. Blobs are shared inside a repository, so a mount from the same one is free;
        // anything else falls through to a regular upload, as the spec allows.
        val mount = parameters["mount"]?.let { Digest.parseOrNull(it) }
        val from = parameters["from"]?.substringBefore('/')
        if (mount != null && from == repo.name && registry.blobExists(repo.name, mount)) {
            return call.blobCreated(target.name, mount)
        }

        val digest = parameters["digest"]
        if (digest != null) {
            // Monolithic upload: the complete blob is in this request.
            val parsed = Digest.parseOrNull(digest)
                ?: return call.ociError(HttpStatusCode.BadRequest, "DIGEST_INVALID", "Invalid digest")
            val session = uploads.start(repo.name)
            try {
                session.append(call.receiveStream())
                commit(call, repo.name, target.name, session, parsed)
            } finally {
                uploads.discard(session.id)
            }
            return
        }

        call.uploadAccepted(target.name, uploads.start(repo.name).id, size = 0)
    }

    private suspend fun patchUpload(call: ApplicationCall, target: Target.UploadSession) {
        val repo = authorize(call, target.name, Permission.WRITE) ?: return
        val session = uploads.find(target.id, repo.name)
            ?: return call.ociError(HttpStatusCode.NotFound, "BLOB_UPLOAD_UNKNOWN", "Upload unknown")

        val start = call.request.header(HttpHeaders.ContentRange)
            ?.removePrefix("bytes ")?.substringBefore('-')?.trim()?.toLongOrNull()
        if (start != null && start != session.size) {
            call.response.header(HttpHeaders.Range, "0-${(session.size - 1).coerceAtLeast(0)}")
            return call.ociError(
                HttpStatusCode.RequestedRangeNotSatisfiable,
                "BLOB_UPLOAD_INVALID",
                "Chunk does not continue at ${session.size}",
            )
        }

        call.uploadAccepted(target.name, session.id, session.append(call.receiveStream()))
    }

    private suspend fun finishUpload(call: ApplicationCall, target: Target.UploadSession) {
        val repo = authorize(call, target.name, Permission.WRITE) ?: return
        val session = uploads.find(target.id, repo.name)
            ?: return call.ociError(HttpStatusCode.NotFound, "BLOB_UPLOAD_UNKNOWN", "Upload unknown")
        val digest = call.request.queryParameters["digest"]?.let { Digest.parseOrNull(it) }
            ?: return call.ociError(HttpStatusCode.BadRequest, "DIGEST_INVALID", "Missing or invalid digest")

        try {
            session.append(call.receiveStream())
            commit(call, repo.name, target.name, session, digest)
        } finally {
            uploads.discard(session.id)
        }
    }

    private suspend fun uploadStatus(call: ApplicationCall, target: Target.UploadSession) {
        val repo = authorize(call, target.name, Permission.WRITE) ?: return
        val session = uploads.find(target.id, repo.name)
            ?: return call.ociError(HttpStatusCode.NotFound, "BLOB_UPLOAD_UNKNOWN", "Upload unknown")

        call.response.header(HttpHeaders.Location, uploadLocation(target.name, session.id))
        call.response.header(DOCKER_UPLOAD_UUID, session.id)
        call.response.header(HttpHeaders.Range, "0-${(session.size - 1).coerceAtLeast(0)}")
        call.respond(HttpStatusCode.NoContent)
    }

    private suspend fun cancelUpload(call: ApplicationCall, target: Target.UploadSession) {
        val repo = authorize(call, target.name, Permission.WRITE) ?: return
        if (uploads.find(target.id, repo.name) == null) {
            return call.ociError(HttpStatusCode.NotFound, "BLOB_UPLOAD_UNKNOWN", "Upload unknown")
        }
        uploads.discard(target.id)
        call.respond(HttpStatusCode.NoContent)
    }

    private suspend fun commit(
        call: ApplicationCall,
        repository: String,
        name: ImageName,
        session: BlobUploadSessions.Session,
        digest: Digest,
    ) {
        val actual = withContext(Dispatchers.IO) { Digest.of(session.file) }
        if (actual != digest) {
            return call.ociError(
                HttpStatusCode.BadRequest,
                "DIGEST_INVALID",
                "Uploaded content digest $actual does not match $digest",
            )
        }
        registry.writeBlob(repository, digest, session.file)
        call.blobCreated(name, digest)
    }

    private suspend fun authorize(
        call: ApplicationCall,
        name: ImageName,
        required: Permission,
    ): RepositoryDto? = when (val result = access.check(call, name.repository, required, RepositoryType.DOCKER)) {
        is RepoAccess.Granted -> result.repository

        is RepoAccess.Denied -> {
            when (result.reason) {
                // Without credentials, an unknown repository is indistinguishable from one the caller cannot see,
                // and a challenge is what makes the client offer its token.
                RepoAccess.Reason.NOT_FOUND ->
                    if (result.authenticated) {
                        call.ociError(HttpStatusCode.NotFound, "NAME_UNKNOWN", "Repository ${name.repository} not found")
                    } else {
                        call.unauthorized(name, required)
                    }

                RepoAccess.Reason.UNAUTHENTICATED -> call.unauthorized(name, required)

                RepoAccess.Reason.FORBIDDEN ->
                    call.ociError(HttpStatusCode.Forbidden, "DENIED", "Insufficient permissions for $name")
            }
            null
        }
    }
}

private suspend fun ApplicationCall.blobCreated(name: ImageName, digest: Digest) {
    response.header(HttpHeaders.Location, "/v2/$name/blobs/$digest")
    response.header(DOCKER_CONTENT_DIGEST, digest.toString())
    respond(HttpStatusCode.Created)
}

private suspend fun ApplicationCall.uploadAccepted(name: ImageName, id: String, size: Long) {
    response.header(HttpHeaders.Location, uploadLocation(name, id))
    response.header(DOCKER_UPLOAD_UUID, id)
    response.header(HttpHeaders.Range, if (size == 0L) "0-0" else "0-${size - 1}")
    respond(HttpStatusCode.Accepted)
}

private fun uploadLocation(name: ImageName, id: String): String = "/v2/$name/blobs/uploads/$id"

/** Tells the client where to exchange its access token for a bearer token, and for which scope. */
private fun ApplicationCall.bearerChallenge(name: ImageName?, required: Permission?) {
    val origin = request.origin
    val defaultPort = (origin.scheme == "https" && origin.serverPort == 443) ||
        (origin.scheme == "http" && origin.serverPort == 80)
    val host = if (defaultPort) origin.serverHost else "${origin.serverHost}:${origin.serverPort}"

    val challenge = buildString {
        append("Bearer realm=\"${origin.scheme}://$host/v2/token\",service=\"$host\"")
        if (name != null) {
            val actions = if (required == Permission.WRITE) "pull,push" else "pull"
            append(",scope=\"repository:$name:$actions\"")
        }
    }
    response.header(HttpHeaders.WWWAuthenticate, challenge)
}

private suspend fun ApplicationCall.unauthorized(name: ImageName?, required: Permission?) {
    bearerChallenge(name, required)
    ociError(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Authentication required")
}

private suspend fun ApplicationCall.unsupported() =
    ociError(HttpStatusCode.NotFound, "UNSUPPORTED", "Unsupported registry operation")

private suspend fun ApplicationCall.ociError(status: HttpStatusCode, code: String, message: String) {
    response.header(API_VERSION, "registry/2.0")
    respondOci(OciErrorResponse(listOf(OciErrorDetail(code, message))), status)
}

/**
 * Registry clients send an `Accept` header listing only manifest media types, which content negotiation answers
 * with `406` — including for error bodies, hiding the actual failure. Registry JSON is therefore written directly.
 */
private suspend inline fun <reified T> ApplicationCall.respondOci(
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondText(Json.encodeToString(value), ContentType.Application.Json, status)
}

/** Reads at most [limit] bytes, or null when the body is larger. */
private suspend fun InputStream.readBounded(limit: Int): ByteArray? = withContext(Dispatchers.IO) {
    val bytes = readNBytes(limit + 1)
    if (bytes.size > limit) null else bytes
}

@Serializable
private data class OciErrorDetail(val code: String, val message: String)

@Serializable
private data class OciErrorResponse(val errors: List<OciErrorDetail>)

@Serializable
private data class TokenResponse(
    val token: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("issued_at") val issuedAt: String,
)

@Serializable
private data class CatalogResponse(val repositories: List<String>)

@Serializable
private data class TagListResponse(val name: String, val tags: List<String>)
