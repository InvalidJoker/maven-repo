package de.joker

import de.joker.auth.RepositoryAccess
import de.joker.routes.authRoutes
import de.joker.routes.dockerBrowseRoutes
import de.joker.routes.dockerRegistryRoutes
import de.joker.routes.mavenRoutes
import de.joker.routes.npmBrowseRoutes
import de.joker.routes.npmRegistryRoutes
import de.joker.routes.repositoryAdminRoutes
import de.joker.routes.repositoryBrowseRoutes
import de.joker.routes.instanceRoutes
import de.joker.routes.oidcRoutes
import de.joker.routes.tokenRoutes
import de.joker.routes.userAdminRoutes
import de.joker.service.AccessTokenService
import de.joker.service.InstanceSettingsService
import de.joker.service.MavenBrowserService
import de.joker.service.OidcService
import de.joker.service.RepositoryService
import de.joker.service.docker.BlobUploadSessions
import de.joker.service.docker.DockerBrowserService
import de.joker.service.docker.DockerRegistryService
import de.joker.service.npm.NpmBrowserService
import de.joker.service.npm.NpmRegistryService
import de.joker.service.proxy.DockerProxyService
import de.joker.service.proxy.MavenProxyService
import de.joker.service.proxy.NpmProxyService
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
    val repositoryAccess by inject<RepositoryAccess>()
    val storageService by inject<StorageBackend>()
    val mavenBrowser by inject<MavenBrowserService>()
    val dockerRegistry by inject<DockerRegistryService>()
    val dockerBrowser by inject<DockerBrowserService>()
    val npmRegistry by inject<NpmRegistryService>()
    val npmBrowser by inject<NpmBrowserService>()
    val blobUploads by inject<BlobUploadSessions>()
    val instanceSettings by inject<InstanceSettingsService>()
    val oidcService by inject<OidcService>()
    val mavenProxy by inject<MavenProxyService>()
    val dockerProxy by inject<DockerProxyService>()
    val npmProxy by inject<NpmProxyService>()

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        authRoutes(userService)
        mavenRoutes(repositoryAccess, storageService, mavenProxy)
        dockerRegistryRoutes(repositoryAccess, dockerRegistry, blobUploads, dockerBrowser, dockerProxy)
        npmRegistryRoutes(repositoryAccess, npmRegistry, userService, npmProxy)

        if (oidcService.enabled) {
            oidcRoutes(oidcService, userService)
        }

        route("/api") {
            instanceRoutes(instanceSettings, oidcService)
            repositoryBrowseRoutes(repositoryService, mavenBrowser, repositoryAccess)
            dockerBrowseRoutes(repositoryAccess, dockerBrowser, dockerRegistry)
            npmBrowseRoutes(repositoryAccess, npmBrowser, npmRegistry)
            repositoryAdminRoutes(repositoryService, userService, storageService)
            userAdminRoutes(userService)
            tokenRoutes(accessTokenService, repositoryService)
        }
    }
}
