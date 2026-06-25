package de.joker

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

fun Application.configureFrontend() {
    routing {
        staticResources("/", "web") {
            default("index.html")
        }
    }
}
