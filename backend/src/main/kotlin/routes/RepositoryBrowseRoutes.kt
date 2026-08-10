package de.joker.routes

import de.joker.auth.RepositoryAccess
import de.joker.auth.UserSession
import de.joker.model.RepositoryType
import de.joker.service.MavenBrowserService
import de.joker.service.RepositoryService
import de.joker.service.forUser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.repositoryBrowseRoutes(
    repositories: RepositoryService,
    browser: MavenBrowserService,
    access: RepositoryAccess,
) {
    route("/repositories") {
        get {
            val session = call.sessions.get<UserSession>()
            val repos = if (session == null) {
                repositories.listPublic()
            } else {
                repositories.listForUser(session.userId, session.admin)
            }
            call.respond(repos)
        }

        get("/{repo}") {
            val repo = call.repositoryOrNotFound(access) ?: return@get
            val permission = access.effective(call, repo)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
            call.respond(repo.forUser(permission))
        }

        get("/{repo}/tree/{path...}") {
            val repo = call.repositoryOrNotFound(access, type = RepositoryType.MAVEN) ?: return@get
            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
            val result = browser.browse(repo.name, path)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository is empty or path does not exist"))
                return@get
            }
            call.respond(result)
        }

        get("/{repo}/search") {
            val repo = call.repositoryOrNotFound(access, type = RepositoryType.MAVEN) ?: return@get
            val query = call.request.queryParameters["q"].orEmpty()
            call.respond(browser.search(repo.name, query))
        }
    }
}
