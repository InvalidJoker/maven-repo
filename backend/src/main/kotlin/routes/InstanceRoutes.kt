package de.joker.routes

import de.joker.AUTH_ADMIN
import de.joker.model.SetAccentRequest
import de.joker.model.SetIconUrlRequest
import de.joker.model.UpdateInstanceRequest
import de.joker.service.InstanceSettingsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val MAX_ICON_BYTES = 1024 * 1024

fun Route.instanceRoutes(settings: InstanceSettingsService) {
    route("/instance") {
        get {
            call.respond(settings.settings())
        }

        get("/icon") {
            val icon = settings.icon()
            if (icon == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respondBytes(icon.bytes, ContentType.parse(icon.contentType))
            }
        }

        authenticate(AUTH_ADMIN) {
            put {
                val name = call.receive<UpdateInstanceRequest>().name.trim()
                if (name.isEmpty() || name.length > 64) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Name must be 1–64 characters"))
                    return@put
                }
                settings.updateName(name)
                call.respond(settings.settings())
            }

            put("/icon") {
                val url = call.receive<SetIconUrlRequest>().url.trim()
                if (url.length > 2048 || !(url.startsWith("http://") || url.startsWith("https://"))) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Icon URL must be an http(s) URL"))
                    return@put
                }
                settings.setIconUrl(url)
                call.respond(settings.settings())
            }

            post("/icon") {
                val contentType = call.request.contentType()
                if (!contentType.match(ContentType.Image.Any)) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Icon must be an image"))
                    return@post
                }
                val bytes = call.receive<ByteArray>()
                if (bytes.isEmpty() || bytes.size > MAX_ICON_BYTES) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Icon must be between 1 byte and 1 MB"))
                    return@post
                }
                settings.setIcon(bytes, contentType.withoutParameters().toString())
                call.respond(settings.settings())
            }

            delete("/icon") {
                settings.clearIcon()
                call.respond(settings.settings())
            }

            put("/accent") {
                settings.setAccent(call.receive<SetAccentRequest>().accent)
                call.respond(settings.settings())
            }
        }
    }
}
