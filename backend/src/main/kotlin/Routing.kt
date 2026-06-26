package de.joker

import de.joker.routes.authRoutes
import de.joker.routes.mavenRoutes
import de.joker.routes.repositoryAdminRoutes
import de.joker.routes.repositoryBrowseRoutes
import de.joker.routes.tokenRoutes
import de.joker.routes.userAdminRoutes
import de.joker.service.AccessControlService
import de.joker.service.AccessTokenService
import de.joker.service.RepositoryBrowserService
import de.joker.service.RepositoryService
import de.joker.service.storage.StorageBackend
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
    val storageService by inject<StorageBackend>()
    val browserService by inject<RepositoryBrowserService>()

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        authRoutes(userService)
        mavenRoutes(repositoryService, accessTokenService, accessControlService, storageService)

        route("/api") {
            repositoryBrowseRoutes(repositoryService, browserService, accessControlService)
            repositoryAdminRoutes(repositoryService, userService)
            userAdminRoutes(userService)
            tokenRoutes(accessTokenService, repositoryService)
        }
    }
}
