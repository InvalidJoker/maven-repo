package de.joker.routes

import de.joker.auth.Permission
import de.joker.auth.RepoAccess
import de.joker.auth.RepositoryAccess
import de.joker.model.RepositoryDto
import de.joker.model.RepositoryMode
import de.joker.model.RepositoryType
import de.joker.service.proxy.MavenProxyService
import de.joker.service.proxy.ProxyOutcome
import de.joker.service.storage.StorageBackend
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.mavenRoutes(access: RepositoryAccess, storage: StorageBackend, proxy: MavenProxyService) {
    route("/maven/{repo}") {
        get("/{path...}") {
            val repo = call.authorize(access, Permission.READ) ?: return@get
            val path = call.artifactPath()

            if (repo.mode == RepositoryMode.PROXY) {
                call.finish(proxy.serve(call, repo, path))
                return@get
            }

            val obj = storage.read(repo.name, path)
            if (obj == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Artifact not found"))
                return@get
            }
            call.respondStorageObject(obj, ContentType.defaultForFilePath(path))
        }

        head("/{path...}") {
            val repo = call.authorize(access, Permission.READ) ?: return@head
            val path = call.artifactPath()

            if (repo.mode == RepositoryMode.PROXY) {
                call.respond(if (proxy.exists(repo, path) == ProxyOutcome.SERVED) HttpStatusCode.OK else HttpStatusCode.NotFound)
                return@head
            }
            call.respond(if (storage.exists(repo.name, path)) HttpStatusCode.OK else HttpStatusCode.NotFound)
        }

        put("/{path...}") {
            val repo = call.authorize(access, Permission.WRITE) ?: return@put
            if (!storage.write(repo.name, call.artifactPath(), call.receiveStream())) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid artifact path"))
                return@put
            }
            call.respond(HttpStatusCode.Created)
        }
    }
}

private fun ApplicationCall.artifactPath(): String =
    parameters.getAll("path")?.joinToString("/") ?: ""

private suspend fun ApplicationCall.finish(outcome: ProxyOutcome) {
    when (outcome) {
        // SERVED and ABORTED have both already written to the response.
        ProxyOutcome.SERVED, ProxyOutcome.ABORTED -> Unit
        ProxyOutcome.NOT_FOUND -> respond(HttpStatusCode.NotFound, mapOf("error" to "Artifact not found"))
        ProxyOutcome.UPSTREAM_ERROR ->
            respond(HttpStatusCode.BadGateway, mapOf("error" to "Upstream repository is unavailable"))
    }
}

private suspend fun ApplicationCall.authorize(access: RepositoryAccess, required: Permission): RepositoryDto? {
    val repoName = parameters["repo"]!!
    return when (val result = access.check(this, repoName, required, RepositoryType.MAVEN)) {
        is RepoAccess.Granted -> {
            val repository = result.repository
            if (required == Permission.WRITE && repository.mode == RepositoryMode.PROXY) {
                respond(
                    HttpStatusCode.MethodNotAllowed,
                    mapOf("error" to "$repoName mirrors ${repository.remoteUrl} and cannot be published to"),
                )
                null
            } else {
                repository
            }
        }

        is RepoAccess.Denied -> {
            when (result.reason) {
                RepoAccess.Reason.NOT_FOUND ->
                    respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))

                RepoAccess.Reason.UNAUTHENTICATED -> {
                    response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"$repoName\"")
                    respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authentication required"))
                }

                RepoAccess.Reason.FORBIDDEN ->
                    respond(HttpStatusCode.Forbidden, mapOf("error" to "Insufficient permissions"))
            }
            null
        }
    }
}
