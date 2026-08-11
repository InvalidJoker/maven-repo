package de.joker.routes

import de.joker.AUTH_ADMIN
import de.joker.model.FooterSettings
import de.joker.model.SetAccentRequest
import de.joker.model.SetIconUrlRequest
import de.joker.model.UpdateInstanceRequest
import de.joker.service.InstanceSettingsService
import de.joker.service.OidcService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val MAX_ICON_BYTES = 1024 * 1024
private const val MAX_FOOTER_LINKS = 10
private val ICON_NAME = Regex("[a-z0-9-]{1,48}")

fun Route.instanceRoutes(settings: InstanceSettingsService, oidc: OidcService) {
    route("/instance") {
        get {
            call.respond(
                settings.settings().copy(
                    oidc = oidc.enabled,
                    oidcLabel = if (oidc.enabled) oidc.buttonLabel else null,
                ),
            )
        }

        get("/icon") {
            val icon = settings.icon()
            if (icon == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                // The icon URL is content-versioned (?v=<mtime>), so a hit is safe to cache forever.
                call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
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

            put("/footer") {
                val footer = call.receive<FooterSettings>()
                val links = footer.links.map { it.copy(label = it.label.trim(), url = it.url.trim(), icon = it.icon.trim()) }
                val error = when {
                    links.size > MAX_FOOTER_LINKS -> "At most $MAX_FOOTER_LINKS footer links"
                    links.any { it.label.isEmpty() || it.label.length > 32 } -> "Link labels must be 1–32 characters"
                    links.any { !(it.url.startsWith("http://") || it.url.startsWith("https://")) || it.url.length > 2048 } ->
                        "Link URLs must be http(s) URLs"
                    // Icons are lucide names; the frontend falls back to a generic one if it doesn't know it.
                    links.any { !ICON_NAME.matches(it.icon) } -> "Invalid icon name"
                    else -> null
                }
                if (error != null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to error))
                    return@put
                }
                settings.setFooter(footer.copy(links = links))
                call.respond(settings.settings())
            }
        }
    }
}
