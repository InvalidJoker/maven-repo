package de.joker.routes

import de.joker.AUTH_ADMIN
import de.joker.model.CreateUserRequest
import de.joker.model.UpdateUserRequest
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

            put("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user id"))
                val request = call.receive<UpdateUserRequest>()

                if (request.admin == null && request.password == null) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Nothing to update"))
                }
                if (request.password != null && request.password.length < 6) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Password must be at least 6 characters"),
                    )
                }

                val target = users.findById(id)
                    ?: return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))

                // Don't allow demoting the last remaining administrator.
                if (request.admin == false && target.admin && users.countAdmins() <= 1) {
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Cannot remove the last administrator"),
                    )
                }

                users.update(id, request.admin, request.password)
                call.respond(HttpStatusCode.OK, users.findById(id)!!)
            }
        }
    }
}
