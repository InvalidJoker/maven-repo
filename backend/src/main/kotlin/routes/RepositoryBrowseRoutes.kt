package de.joker.routes

import de.joker.auth.UserSession
import de.joker.service.RepositoryService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

/**
 * Public repository browsing. Anonymous visitors see only public repositories; an authenticated
 * session additionally reveals the private repositories that user may access (all of them, for
 * admins).
 */
fun Route.repositoryBrowseRoutes(repositories: RepositoryService) {
    get("/api/repositories/visible") {
        val session = call.sessions.get<UserSession>()
        val repos = if (session == null) {
            repositories.listPublic()
        } else {
            repositories.listForUser(session.userId, session.admin)
        }
        call.respond(repos)
    }
}
