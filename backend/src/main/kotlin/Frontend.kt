package de.joker

import de.joker.model.InstanceSettings
import de.joker.service.InstanceSettingsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureFrontend() {
    val settings by inject<InstanceSettingsService>()
    val template = environment.classLoader.getResource("web/index.html")?.readText()

    routing {
        if (template != null) {
            get("/") {
                call.respondText(renderIndex(template, settings.settings()), ContentType.Text.Html)
            }
            staticResources("/", "web") { cacheControl(::staticCache) }
        } else {
            staticResources("/", "web") {
                default("index.html")
                cacheControl(::staticCache)
            }
        }
    }
}

/** Long cache for content-hashed Vite assets; a shorter one for the favicon and other statics. */
private fun staticCache(resource: java.net.URL): List<CacheControl> {
    val path = resource.path
    return when {
        "/assets/" in path ->
            listOf(CacheControl.MaxAge(maxAgeSeconds = 31_536_000, visibility = CacheControl.Visibility.Public))
        path.endsWith(".svg") || path.endsWith(".png") || path.endsWith(".ico") ->
            listOf(CacheControl.MaxAge(maxAgeSeconds = 86_400, visibility = CacheControl.Visibility.Public))
        else -> emptyList()
    }
}

private fun renderIndex(template: String, settings: InstanceSettings): String {
    val faviconHref = settings.iconUrl ?: "/favicon.svg"
    return template
        .replace("{{appName}}", escapeHtml(settings.name))
        .replace("{{faviconHref}}", escapeHtml(faviconHref))
}

private fun escapeHtml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
