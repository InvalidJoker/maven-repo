package de.joker.routes

import de.joker.auth.Permission
import de.joker.auth.RepoAccess
import de.joker.auth.RepositoryAccess
import de.joker.model.RepositoryDto
import de.joker.model.RepositoryType
import de.joker.service.storage.StorageBackend
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.mavenRoutes(access: RepositoryAccess, storage: StorageBackend) {
    route("/maven/{repo}") {
        get("/{path...}") {
            val repo = call.authorize(access, Permission.READ) ?: return@get
            val path = call.artifactPath()
            val obj = storage.read(repo.name, path)
            if (obj == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Artifact not found"))
                return@get
            }
            call.respondStorageObject(obj, ContentType.defaultForFilePath(path))
        }

        head("/{path...}") {
            val repo = call.authorize(access, Permission.READ) ?: return@head
            val exists = storage.exists(repo.name, call.artifactPath())
            call.respond(if (exists) HttpStatusCode.OK else HttpStatusCode.NotFound)
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

private suspend fun ApplicationCall.authorize(access: RepositoryAccess, required: Permission): RepositoryDto? {
    val repoName = parameters["repo"]!!
    return when (val result = access.check(this, repoName, required, RepositoryType.MAVEN)) {
        is RepoAccess.Granted -> result.repository

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
