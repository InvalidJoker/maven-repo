package de.joker

import de.joker.di.appModule
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configure() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }
    install(Koin) {
        slf4jLogger()
        modules(appModule(environment.config))
    }
    install(ContentNegotiation) {
        json()
    }
}
