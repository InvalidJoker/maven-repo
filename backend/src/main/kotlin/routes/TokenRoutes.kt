package de.joker.routes

import de.joker.AUTH_SESSION
import de.joker.auth.UserSession
import de.joker.model.CreateTokenRequest
import de.joker.model.UpdateTokenRequest
import de.joker.service.AccessTokenService
import de.joker.service.RepositoryService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.tokenRoutes(tokens: AccessTokenService, repositories: RepositoryService) {
    authenticate(AUTH_SESSION) {
        route("/tokens") {
            get {
                val session = call.principal<UserSession>()!!
                call.respond(tokens.listForUser(session.userId))
            }

            post {
                val session = call.principal<UserSession>()!!
                val request = call.receive<CreateTokenRequest>()
                val scopes = repositories.resolveScopes(request.scopes)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown repository in scopes"))
                call.respond(HttpStatusCode.Created, tokens.create(session.userId, request.name, scopes))
            }

            put("/{id}") {
                val session = call.principal<UserSession>()!!
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid token id"))
                val request = call.receive<UpdateTokenRequest>()
                val scopes = repositories.resolveScopes(request.scopes)
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown repository in scopes"))
                if (tokens.update(session.userId, id, request.name, scopes)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Token not found"))
                }
            }

            delete("/{id}") {
                val session = call.principal<UserSession>()!!
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid token id"))
                if (tokens.delete(session.userId, id)) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Token not found"))
                }
            }
        }
    }
}
