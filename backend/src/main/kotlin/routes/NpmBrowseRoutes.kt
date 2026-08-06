package de.joker.routes

import de.joker.auth.Permission
import de.joker.auth.RepositoryAccess
import de.joker.model.RepositoryType
import de.joker.service.npm.NpmBrowserService
import de.joker.service.npm.NpmRegistryService
import de.joker.service.npm.isValidPackageName
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Browser API for npm repositories. Package names may be scoped (`@scope/name`), so they travel as a query
 * parameter rather than as path segments.
 */
fun Route.npmBrowseRoutes(
    access: RepositoryAccess,
    browser: NpmBrowserService,
    registry: NpmRegistryService,
) {
    route("/repositories/{repo}/npm") {
        get("/packages") {
            val repo = call.repositoryOrNotFound(access, type = RepositoryType.NPM) ?: return@get
            call.respond(browser.packages(repo.name))
        }

        get("/package") {
            val repo = call.repositoryOrNotFound(access, type = RepositoryType.NPM) ?: return@get
            val name = call.packageName() ?: return@get
            val detail = browser.packageDetail(repo.name, name)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Package not found"))
            call.respond(detail)
        }

        get("/version") {
            val repo = call.repositoryOrNotFound(access, type = RepositoryType.NPM) ?: return@get
            val name = call.packageName() ?: return@get
            val version = call.request.queryParameters["version"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing version"))

            val detail = browser.versionDetail(repo.name, name, version)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Version not found"))
            call.respond(detail)
        }

        delete("/version") {
            val repo = call.repositoryOrNotFound(access, Permission.WRITE, RepositoryType.NPM) ?: return@delete
            val name = call.packageName() ?: return@delete
            val version = call.request.queryParameters["version"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing version"))

            if (registry.deleteVersion(repo.name, name, version)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Version not found"))
            }
        }

        delete("/package") {
            val repo = call.repositoryOrNotFound(access, Permission.WRITE, RepositoryType.NPM) ?: return@delete
            val name = call.packageName() ?: return@delete

            if (registry.deletePackage(repo.name, name)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Package not found"))
            }
        }
    }
}

private suspend fun ApplicationCall.packageName(): String? {
    val name = request.queryParameters["name"]
    if (name == null || !isValidPackageName(name)) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing or invalid package name"))
        return null
    }
    return name
}
