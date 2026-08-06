package de.joker.routes

import de.joker.auth.Permission
import de.joker.auth.RepositoryAccess
import de.joker.model.RepositoryType
import de.joker.service.docker.DockerBrowserService
import de.joker.service.docker.DockerRegistryService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.dockerBrowseRoutes(
    access: RepositoryAccess,
    browser: DockerBrowserService,
    registry: DockerRegistryService,
) {
    route("/repositories/{repo}/docker") {
        get("/images") {
            val repo = call.repositoryOrNotFound(access, type = RepositoryType.DOCKER) ?: return@get
            call.respond(browser.images(repo.name))
        }

        get("/tags") {
            val repo = call.repositoryOrNotFound(access, type = RepositoryType.DOCKER) ?: return@get
            val image = call.image() ?: return@get
            call.respond(browser.tags(repo.name, image))
        }

        get("/manifest") {
            val repo = call.repositoryOrNotFound(access, type = RepositoryType.DOCKER) ?: return@get
            val image = call.image() ?: return@get
            val reference = call.request.queryParameters["reference"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing reference"))

            val manifest = browser.manifest(repo.name, image, reference)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Manifest not found"))
            call.respond(manifest)
        }

        delete("/tags") {
            val repo = call.repositoryOrNotFound(access, Permission.WRITE, RepositoryType.DOCKER) ?: return@delete
            val image = call.image() ?: return@delete
            val tag = call.request.queryParameters["tag"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing tag"))

            if (registry.deleteTag(repo.name, image, tag)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Tag not found"))
            }
        }

        delete("/images") {
            val repo = call.repositoryOrNotFound(access, Permission.WRITE, RepositoryType.DOCKER) ?: return@delete
            val image = call.image() ?: return@delete

            if (registry.deleteImage(repo.name, image)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Image not found"))
            }
        }
    }
}

private suspend fun ApplicationCall.image(): String? {
    val image = request.queryParameters["image"]?.takeIf { it.isNotBlank() }
    if (image == null || image.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid image name"))
        return null
    }
    return image
}
