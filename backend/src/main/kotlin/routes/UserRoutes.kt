package de.joker.routes

import de.joker.AUTH_SESSION
import de.joker.auth.UserSession
import de.joker.service.RepositoryService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Endpoints scoped to the currently logged-in user. */
fun Route.userRoutes(repositories: RepositoryService) {
    authenticate(AUTH_SESSION) {
        get("/api/me/repositories") {
            val session = call.principal<UserSession>()!!
            call.respond(repositories.listForUser(session.userId, session.admin))
        }
    }
}
