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
            staticResources("/", "web")
        } else {
            staticResources("/", "web") { default("index.html") }
        }
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
