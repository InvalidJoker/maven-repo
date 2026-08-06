package de.joker.routes

import de.joker.auth.AuthResult
import de.joker.auth.Permission
import de.joker.auth.RepoAccess
import de.joker.auth.RepositoryAccess
import de.joker.model.RepositoryDto
import de.joker.model.RepositoryType
import de.joker.service.UserService
import de.joker.service.npm.NpmRegistryService
import de.joker.service.npm.StoredPackument
import de.joker.service.npm.isValidPackageName
import de.joker.service.npm.isValidTarballName
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.InputStream

private const val MAX_PUBLISH_BYTES = 64 * 1024 * 1024

/**
 * npm registry API at `/npm/<repo>`, the protocol `npm`, `pnpm`, `yarn` and `bun` speak.
 *
 * Like the OCI spec, npm puts verbs after a variable-length package name (`/<name>/-/<file>.tgz`), and a name may
 * be scoped (`@scope/pkg`, sent either as two segments or with the slash percent-encoded). Everything therefore
 * goes through one tailcard route and is split here.
 */
fun Route.npmRegistryRoutes(access: RepositoryAccess, registry: NpmRegistryService, users: UserService) {
    val api = NpmRegistryApi(access, registry, users)

    route("/npm/{repo}") {
        get("/{path...}") { api.onGet(call, call.npmSegments()) }
        put("/{path...}") { api.onPut(call, call.npmSegments()) }
        delete("/{path...}") { api.onDelete(call, call.npmSegments()) }
    }
}

private fun ApplicationCall.npmSegments(): List<String> =
    parameters.getAll("path").orEmpty().flatMap { it.split('/') }.filter { it.isNotEmpty() }

private sealed interface NpmTarget {
    data class Packument(val name: String) : NpmTarget
    data class Tarball(val name: String, val file: String) : NpmTarget
    data class DistTags(val name: String) : NpmTarget
    data class DistTag(val name: String, val tag: String) : NpmTarget
    data object Whoami : NpmTarget
    data object Ping : NpmTarget
}

/** A package name can never be `-`, so the trailing `-` segment unambiguously separates name from tarball. */
private fun parseNpmTarget(segments: List<String>): NpmTarget? {
    if (segments.isEmpty()) return NpmTarget.Ping

    if (segments.first() == "-") {
        val rest = segments.drop(1)
        return when {
            rest == listOf("ping") -> NpmTarget.Ping
            rest == listOf("whoami") -> NpmTarget.Whoami
            rest.firstOrNull() == "package" && rest.lastOrNull() == "dist-tags" ->
                NpmTarget.DistTags(rest.drop(1).dropLast(1).joinToString("/"))

            rest.firstOrNull() == "package" && rest.size >= 4 && rest[rest.size - 2] == "dist-tags" ->
                NpmTarget.DistTag(rest.drop(1).dropLast(2).joinToString("/"), rest.last())

            else -> null
        }
    }

    val separator = segments.size - 2
    if (separator >= 1 && segments[separator] == "-") {
        return NpmTarget.Tarball(segments.take(separator).joinToString("/"), segments.last())
    }
    return NpmTarget.Packument(segments.joinToString("/"))
}

private class NpmRegistryApi(
    private val access: RepositoryAccess,
    private val registry: NpmRegistryService,
    private val users: UserService,
) {

    suspend fun onGet(call: ApplicationCall, segments: List<String>) {
        when (val target = parseNpmTarget(segments)) {
            is NpmTarget.Ping -> call.respondJson(buildJsonObject { })
            is NpmTarget.Whoami -> whoami(call)
            is NpmTarget.Packument -> packument(call, target.name)
            is NpmTarget.Tarball -> tarball(call, target)
            is NpmTarget.DistTags -> distTags(call, target.name)
            else -> call.npmError(HttpStatusCode.NotFound, "Not found")
        }
    }

    suspend fun onPut(call: ApplicationCall, segments: List<String>) {
        when (val target = parseNpmTarget(segments)) {
            is NpmTarget.Packument -> publish(call, target.name)
            is NpmTarget.DistTag -> putDistTag(call, target)
            else -> call.npmError(HttpStatusCode.NotFound, "Not found")
        }
    }

    suspend fun onDelete(call: ApplicationCall, segments: List<String>) {
        when (val target = parseNpmTarget(segments)) {
            is NpmTarget.DistTag -> deleteDistTag(call, target)
            is NpmTarget.Packument -> deletePackage(call, target.name)
            else -> call.npmError(HttpStatusCode.NotFound, "Not found")
        }
    }

    private suspend fun whoami(call: ApplicationCall) {
        val principal = (access.authenticate(call) as? AuthResult.User)?.principal
            ?: return call.npmError(HttpStatusCode.Unauthorized, "Authentication required")
        val user = users.findById(principal.userId)
            ?: return call.npmError(HttpStatusCode.Unauthorized, "Authentication required")
        call.respondJson(buildJsonObject { put("username", JsonPrimitive(user.username)) })
    }

    private suspend fun packument(call: ApplicationCall, name: String) {
        val repo = authorize(call, Permission.READ) ?: return
        if (!isValidPackageName(name)) return call.npmError(HttpStatusCode.BadRequest, "Invalid package name")

        val stored = registry.packument(repo.name, name)
        if (stored != null) return call.respondJson(stored.toPackument(call.tarballBase(repo.name, name)))

        // `npm view pkg@1.0.0` and `npm install pkg@tag` also request a single version document.
        val parent = name.substringBeforeLast('/', "")
        val reference = name.substringAfterLast('/')
        val parentPackument = parent.takeIf { it.isNotEmpty() && isValidPackageName(it) }
            ?.let { registry.packument(repo.name, it) }
            ?: return call.npmError(HttpStatusCode.NotFound, "Package not found")

        val version = parentPackument.distTags[reference] ?: reference
        val manifest = parentPackument.versions[version]
            ?: return call.npmError(HttpStatusCode.NotFound, "Version not found")
        call.respondJson(manifest.withAbsoluteTarball(call.tarballBase(repo.name, parent)))
    }

    private suspend fun tarball(call: ApplicationCall, target: NpmTarget.Tarball) {
        val repo = authorize(call, Permission.READ) ?: return
        if (!isValidPackageName(target.name) || !isValidTarballName(target.file)) {
            return call.npmError(HttpStatusCode.BadRequest, "Invalid tarball path")
        }
        val obj = registry.tarball(repo.name, target.name, target.file)
            ?: return call.npmError(HttpStatusCode.NotFound, "Tarball not found")
        call.respondStorageObject(obj, ContentType.Application.OctetStream)
    }

    private suspend fun publish(call: ApplicationCall, name: String) {
        val repo = authorize(call, Permission.WRITE) ?: return
        if (!isValidPackageName(name)) return call.npmError(HttpStatusCode.BadRequest, "Invalid package name")

        val body = call.receiveStream().readBounded(MAX_PUBLISH_BYTES)
            ?: return call.npmError(HttpStatusCode.PayloadTooLarge, "Package exceeds ${MAX_PUBLISH_BYTES / 1024 / 1024} MB")
        val document = runCatching { Json.parseToJsonElement(body.decodeToString()) as? JsonObject }.getOrNull()
            ?: return call.npmError(HttpStatusCode.BadRequest, "Request body is not a JSON object")

        val declared = (document["name"] as? JsonPrimitive)?.content
        if (declared != null && declared != name) {
            return call.npmError(HttpStatusCode.BadRequest, "Package name does not match the request URL")
        }

        when (val result = registry.publish(repo.name, name, document)) {
            is NpmRegistryService.PublishResult.Published ->
                call.respondJson(
                    buildJsonObject {
                        put("ok", JsonPrimitive(true))
                        put("id", JsonPrimitive(name))
                        put("success", JsonPrimitive(true))
                    },
                    HttpStatusCode.Created,
                )

            is NpmRegistryService.PublishResult.Conflict ->
                call.npmError(
                    HttpStatusCode.Conflict,
                    "Cannot publish over the previously published version ${result.version}",
                )

            is NpmRegistryService.PublishResult.Invalid ->
                call.npmError(HttpStatusCode.BadRequest, result.message)
        }
    }

    private suspend fun distTags(call: ApplicationCall, name: String) {
        val repo = authorize(call, Permission.READ) ?: return
        val stored = registry.packument(repo.name, name)
            ?: return call.npmError(HttpStatusCode.NotFound, "Package not found")
        call.respondJson(
            JsonObject(stored.distTags.mapValues { (_, version) -> JsonPrimitive(version) }),
        )
    }

    private suspend fun putDistTag(call: ApplicationCall, target: NpmTarget.DistTag) {
        val repo = authorize(call, Permission.WRITE) ?: return
        val version = call.receiveStream().readBounded(1024)?.decodeToString()?.trim()?.trim('"')
            ?: return call.npmError(HttpStatusCode.BadRequest, "Missing version")

        if (registry.setDistTag(repo.name, target.name, target.tag, version)) {
            call.respondJson(buildJsonObject { put("ok", JsonPrimitive(true)) })
        } else {
            call.npmError(HttpStatusCode.NotFound, "Package or version not found")
        }
    }

    private suspend fun deleteDistTag(call: ApplicationCall, target: NpmTarget.DistTag) {
        val repo = authorize(call, Permission.WRITE) ?: return
        if (registry.deleteDistTag(repo.name, target.name, target.tag)) {
            call.respondJson(buildJsonObject { put("ok", JsonPrimitive(true)) })
        } else {
            call.npmError(HttpStatusCode.NotFound, "Tag not found")
        }
    }

    private suspend fun deletePackage(call: ApplicationCall, name: String) {
        val repo = authorize(call, Permission.WRITE) ?: return
        if (registry.deletePackage(repo.name, name)) {
            call.respondJson(buildJsonObject { put("ok", JsonPrimitive(true)) })
        } else {
            call.npmError(HttpStatusCode.NotFound, "Package not found")
        }
    }

    private suspend fun authorize(call: ApplicationCall, required: Permission): RepositoryDto? {
        val name = call.parameters["repo"]!!
        return when (val result = access.check(call, name, required, RepositoryType.NPM)) {
            is RepoAccess.Granted -> result.repository
            is RepoAccess.Denied -> {
                when (result.reason) {
                    RepoAccess.Reason.NOT_FOUND ->
                        call.npmError(HttpStatusCode.NotFound, "Repository not found")

                    RepoAccess.Reason.UNAUTHENTICATED ->
                        call.npmError(
                            HttpStatusCode.Unauthorized,
                            "Authentication required — add //<host>/npm/$name/:_authToken=<access-token> to your .npmrc",
                        )

                    RepoAccess.Reason.FORBIDDEN ->
                        call.npmError(HttpStatusCode.Forbidden, "Insufficient permissions")
                }
                null
            }
        }
    }
}

/** Tarballs are stored by file name; the absolute URL is built per request so the registry can change hosts. */
private fun ApplicationCall.tarballBase(repository: String, name: String): String {
    val origin = request.origin
    val defaultPort = (origin.scheme == "https" && origin.serverPort == 443) ||
        (origin.scheme == "http" && origin.serverPort == 80)
    val host = if (defaultPort) origin.serverHost else "${origin.serverHost}:${origin.serverPort}"
    return "${origin.scheme}://$host/npm/$repository/$name/-"
}

private fun StoredPackument.toPackument(tarballBase: String): JsonObject = buildJsonObject {
    put("_id", JsonPrimitive(name))
    put("name", JsonPrimitive(name))
    latest?.let { versions[it]?.get("description") }?.let { put("description", it) }
    put("dist-tags", JsonObject(distTags.mapValues { (_, version) -> JsonPrimitive(version) }))
    put("versions", JsonObject(versions.mapValues { (_, manifest) -> manifest.withAbsoluteTarball(tarballBase) }))
    put("time", JsonObject(time.mapValues { (_, value) -> JsonPrimitive(value) }))
}

private fun JsonObject.withAbsoluteTarball(tarballBase: String): JsonObject {
    val dist = this["dist"] as? JsonObject ?: return this
    val file = (dist["tarball"] as? JsonPrimitive)?.content ?: return this
    if (file.startsWith("http://") || file.startsWith("https://")) return this
    return JsonObject(this + ("dist" to JsonObject(dist + ("tarball" to JsonPrimitive("$tarballBase/$file")))))
}

private suspend fun ApplicationCall.npmError(status: HttpStatusCode, message: String) {
    respondJson(
        buildJsonObject {
            put("error", JsonPrimitive(message))
            put("reason", JsonPrimitive(message))
        },
        status,
    )
}

