package de.joker.routes

import de.joker.auth.MavenPrincipal
import de.joker.auth.UserSession
import de.joker.service.AccessControlService
import de.joker.service.RepositoryBrowserService
import de.joker.service.RepositoryService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.repositoryBrowseRoutes(
    repositories: RepositoryService,
    browser: RepositoryBrowserService,
    accessControl: AccessControlService,
) {
    get("/api/repositories/visible") {
        val session = call.sessions.get<UserSession>()
        val repos = if (session == null) {
            repositories.listPublic()
        } else {
            repositories.listForUser(session.userId, session.admin)
        }
        call.respond(repos)
    }

    get("/api/repositories/{repo}/tree/{path...}") {
        val repoName = call.parameters["repo"]!!
        val repo = repositories.findByName(repoName)

        // Hide private repositories the caller can't read behind a 404.
        val canRead = repo != null && (!repo.private || run {
            val session = call.sessions.get<UserSession>() ?: return@run false
            val principal = MavenPrincipal(session.userId, session.admin, tokenId = null)
            accessControl.effectivePermission(principal, repo.id) != null
        })
        if (repo == null || !canRead) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
            return@get
        }

        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
        val result = browser.browse(repoName, path)
        if (result == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository is empty or path does not exist"))
            return@get
        }
        call.respond(result)
    }

    get("/api/repositories/{repo}/search") {
        val repoName = call.parameters["repo"]!!
        val repo = repositories.findByName(repoName)

        val canRead = repo != null && (!repo.private || run {
            val session = call.sessions.get<UserSession>() ?: return@run false
            val principal = MavenPrincipal(session.userId, session.admin, tokenId = null)
            accessControl.effectivePermission(principal, repo.id) != null
        })
        if (repo == null || !canRead) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
            return@get
        }

        val query = call.request.queryParameters["q"].orEmpty()
        call.respond(browser.search(repoName, query))
    }
}
