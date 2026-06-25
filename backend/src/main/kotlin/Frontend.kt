package de.joker

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

/**
 * Serves the built single-page frontend bundled under `resources/web`. The app uses hash-based
 * routing (`/#/...`), so the server only needs to serve `index.html` and static assets; API and
 * Maven routes take precedence over this catch-all because their paths are more specific.
 */
fun Application.configureFrontend() {
    routing {
        staticResources("/", "web") {
            default("index.html")
        }
    }
}
