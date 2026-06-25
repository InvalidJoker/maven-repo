package de.joker

import de.joker.routes.authRoutes
import de.joker.routes.mavenRoutes
import de.joker.routes.repositoryAdminRoutes
import de.joker.routes.repositoryBrowseRoutes
import de.joker.routes.tokenRoutes
import de.joker.service.AccessControlService
import de.joker.service.AccessTokenService
import de.joker.service.RepositoryService
import de.joker.service.RepositoryStorageService
import de.joker.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val userService by inject<UserService>()
    val repositoryService by inject<RepositoryService>()
    val accessTokenService by inject<AccessTokenService>()
    val accessControlService by inject<AccessControlService>()
    val storageService by inject<RepositoryStorageService>()

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        authRoutes(userService)
        repositoryBrowseRoutes(repositoryService)
        repositoryAdminRoutes(repositoryService, userService)
        tokenRoutes(accessTokenService, repositoryService)
        mavenRoutes(repositoryService, accessTokenService, accessControlService, storageService)
    }
}
