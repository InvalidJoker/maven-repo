package de.joker.routes

import de.joker.AUTH_SESSION
import de.joker.auth.LoginRequest
import de.joker.auth.UserDto
import de.joker.auth.UserSession
import de.joker.service.UserService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

/** Mounts `/auth` login, logout and current-user endpoints. */
fun Route.authRoutes(userService: UserService) {
    route("/auth") {
        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = userService.authenticate(request.username, request.password)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
            } else {
                call.sessions.set(UserSession(user.id, user.username, user.admin))
                call.respond(HttpStatusCode.OK, user)
            }
        }

        post("/logout") {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.OK, mapOf("status" to "logged out"))
        }

        authenticate(AUTH_SESSION) {
            get("/me") {
                val session = call.principal<UserSession>()!!
                call.respond(UserDto(session.userId, session.username, session.admin))
            }
        }
    }
}
