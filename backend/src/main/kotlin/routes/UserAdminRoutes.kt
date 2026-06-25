package de.joker.routes

import de.joker.AUTH_ADMIN
import de.joker.model.CreateUserRequest
import de.joker.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userAdminRoutes(users: UserService) {
    authenticate(AUTH_ADMIN) {
        route("/api/users") {
            get {
                call.respond(users.list())
            }

            post {
                val request = call.receive<CreateUserRequest>()
                val username = request.username.trim()
                if (username.length < 3 || request.password.length < 6) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Username must be at least 3 and password at least 6 characters"),
                    )
                    return@post
                }
                if (users.findByUsername(username) != null) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Username already taken"))
                    return@post
                }
                val user = users.createUser(username, request.password, admin = request.admin)
                call.respond(HttpStatusCode.Created, user)
            }
        }
    }
}
