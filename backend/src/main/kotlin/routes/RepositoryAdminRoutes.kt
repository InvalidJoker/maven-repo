package de.joker.routes

import de.joker.AUTH_ADMIN
import de.joker.model.CreateRepositoryRequest
import de.joker.model.GrantPermissionRequest
import de.joker.model.RepositoryMode
import de.joker.model.RepositoryType
import de.joker.model.UpdateRepositoryRequest
import de.joker.service.RepositoryService
import de.joker.service.UserService
import de.joker.service.storage.StorageBackend
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI

private val REPOSITORY_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
private const val MAX_CACHE_TTL_SECONDS = 30L * 24 * 60 * 60

fun Route.repositoryAdminRoutes(repositories: RepositoryService, users: UserService, storage: StorageBackend) {
    authenticate(AUTH_ADMIN) {
        route("/repositories") {
            get {
                call.respond(repositories.list())
            }

            post {
                val request = call.receive<CreateRepositoryRequest>()
                val name = request.name.trim()
                // Docker repository names become the first segment of an image reference, which the OCI spec
                // restricts to lowercase.
                if (!REPOSITORY_NAME.matches(name) || (request.type == RepositoryType.DOCKER && name != name.lowercase())) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid repository name"))
                    return@post
                }
                if (repositories.findByName(name) != null) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Repository already exists"))
                    return@post
                }

                val remote = if (request.mode == RepositoryMode.PROXY) {
                    remoteUrl(request.remoteUrl, request.type)
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "A proxy repository needs an http(s) upstream URL"),
                        )
                } else {
                    null
                }
                if (request.cacheTtlSeconds !in 0..MAX_CACHE_TTL_SECONDS) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid cache lifetime"))
                }

                call.respond(
                    HttpStatusCode.Created,
                    repositories.create(request.copy(name = name, remoteUrl = remote)),
                )
            }

            put("/{repo}") {
                val repo = repositories.findByName(call.parameters["repo"]!!)
                    ?: return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
                val request = call.receive<UpdateRepositoryRequest>()

                val remote = request.remoteUrl?.let { url ->
                    if (repo.mode != RepositoryMode.PROXY) {
                        return@put call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Only proxy repositories have an upstream"),
                        )
                    }
                    remoteUrl(url, repo.type) ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid upstream URL"),
                    )
                }
                if (request.cacheTtlSeconds != null && request.cacheTtlSeconds !in 0..MAX_CACHE_TTL_SECONDS) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid cache lifetime"))
                }

                repositories.update(repo.id, request.copy(remoteUrl = remote))
                call.respond(repositories.findByName(repo.name)!!)
            }

            /** Drops everything a proxy repository has mirrored so far; it refills on the next request. */
            delete("/{repo}/cache") {
                val repo = repositories.findByName(call.parameters["repo"]!!)
                    ?: return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
                if (repo.mode != RepositoryMode.PROXY) {
                    return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Only proxy repositories have a cache"),
                    )
                }
                storage.deleteDirectory(repo.name, "")
                call.respond(HttpStatusCode.OK)
            }

            route("/{repo}/permissions") {
                get {
                    val repo = repositories.findByName(call.parameters["repo"]!!)
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
                    call.respond(repositories.listPermissions(repo.id))
                }

                post {
                    val repo = repositories.findByName(call.parameters["repo"]!!)
                        ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
                    val request = call.receive<GrantPermissionRequest>()
                    val user = users.findByUsername(request.username)
                        ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    repositories.grant(repo.id, user.id, request.permission)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/{username}") {
                    val repo = repositories.findByName(call.parameters["repo"]!!)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
                    val user = users.findByUsername(call.parameters["username"]!!)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    repositories.revoke(repo.id, user.id)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}

/**
 * Upstreams are stored without a trailing slash. Docker upstreams are the registry root rather than its API
 * root, because the `/v2/` prefix is part of every request the proxy makes — pasting it in is the obvious
 * mistake, so it is trimmed instead of rejected.
 */
private fun remoteUrl(value: String?, type: RepositoryType): String? {
    val trimmed = value?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    return if (type == RepositoryType.DOCKER) trimmed.removeSuffix("/v2") else trimmed
}
