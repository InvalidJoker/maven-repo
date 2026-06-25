package de.joker.routes

import de.joker.AUTH_ADMIN
import de.joker.model.CreateRepositoryRequest
import de.joker.model.GrantPermissionRequest
import de.joker.service.RepositoryService
import de.joker.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.repositoryAdminRoutes(repositories: RepositoryService, users: UserService) {
    authenticate(AUTH_ADMIN) {
        route("/api/repositories") {
            get {
                call.respond(repositories.list())
            }

            post {
                val request = call.receive<CreateRepositoryRequest>()
                if (repositories.findByName(request.name) != null) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Repository already exists"))
                    return@post
                }
                call.respond(HttpStatusCode.Created, repositories.create(request.name, request.private))
            }

            route("/{repo}/permissions") {
                get {
                    val repo = repositories.findByName(call.parameters["repo"]!!)
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
                    call.respond(repositories.listPermissions(repo.id))
                }

                post {
                    val repo = repositories.findByName(call.parameters["repo"]!!)
                        ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
                    val request = call.receive<GrantPermissionRequest>()
                    val user = users.findByUsername(request.username)
                        ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    repositories.grant(repo.id, user.id, request.permission)
                    call.respond(HttpStatusCode.OK)
                }

                delete("/{username}") {
                    val repo = repositories.findByName(call.parameters["repo"]!!)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "Repository not found"))
                    val user = users.findByUsername(call.parameters["username"]!!)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    repositories.revoke(repo.id, user.id)
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
