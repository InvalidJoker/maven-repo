package de.joker.routes

import de.joker.auth.MavenPrincipal
import de.joker.auth.Permission
import de.joker.auth.UserSession
import de.joker.model.RepositoryDto
import de.joker.service.AccessControlService
import de.joker.service.AccessTokenService
import de.joker.service.RepositoryService
import de.joker.service.RepositoryStorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.mavenRoutes(
    repositories: RepositoryService,
    tokens: AccessTokenService,
    accessControl: AccessControlService,
    storage: RepositoryStorageService,
) {
    route("/maven/{repo}") {
        get("/{path...}") {
            val repo = call.authorize(Permission.READ, repositories, tokens, accessControl) ?: return@get
            val file = storage.fileFor(repo.name, call.artifactPath())
            if (file == null || !file.isFile) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Artifact not found"))
                return@get
            }
            call.respondFile(file)
        }

        head("/{path...}") {
            val repo = call.authorize(Permission.READ, repositories, tokens, accessControl) ?: return@head
            val file = storage.fileFor(repo.name, call.artifactPath())
            call.respond(if (file != null && file.isFile) HttpStatusCode.OK else HttpStatusCode.NotFound)
        }

        put("/{path...}") {
            val repo = call.authorize(Permission.WRITE, repositories, tokens, accessControl) ?: return@put
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

/**
 * Authorizes the current call against the repository named in the route, responding with the
 * appropriate error and returning null when access is denied.
 */
private suspend fun ApplicationCall.authorize(
    required: Permission,
    repositories: RepositoryService,
    tokens: AccessTokenService,
    accessControl: AccessControlService,
): RepositoryDto? {
    val repoName = parameters["repo"]!!
    val repo = repositories.findByName(repoName)
    if (repo == null) {
        respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
        return null
    }

    // Public repositories permit anonymous reads.
    if (!repo.private && required == Permission.READ) return repo

    val principal = resolvePrincipal(tokens)
    if (principal == null) {
        response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"$repoName\"")
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authentication required"))
        return null
    }

    val effective = accessControl.effectivePermission(principal, repo.id)
    if (effective == null || !effective.allows(required)) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Insufficient permissions"))
        return null
    }
    return repo
}

private suspend fun ApplicationCall.resolvePrincipal(tokens: AccessTokenService): MavenPrincipal? {
    sessions.get<UserSession>()?.let { return MavenPrincipal(it.userId, it.admin, tokenId = null) }
    val credentials = request.basicAuthenticationCredentials() ?: return null
    return tokens.verify(credentials.name, credentials.password)
}
