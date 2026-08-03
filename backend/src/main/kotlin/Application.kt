package de.joker

import de.joker.di.appModule
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.httpMethod
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.Level

fun Application.configure() {
    install(XForwardedHeaders)

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        allowNonSimpleContentTypes = true
        anyHost()
    }
    install(Koin) {
        slf4jLogger()
        modules(appModule(environment.config))
    }
    install(ContentNegotiation) {
        json()
    }
    install(CallLogging) {
        level = Level.INFO
    }

    stripHeadResponseBodies()
}

private fun Application.stripHeadResponseBodies() {
    sendPipeline.intercept(ApplicationSendPipeline.After) { message ->
        if (call.request.httpMethod != HttpMethod.Head) return@intercept
        val content = message as? OutgoingContent ?: return@intercept
        if (content is OutgoingContent.NoContent) return@intercept

        proceedWith(
            object : OutgoingContent.NoContent() {
                override val status: HttpStatusCode? = content.status
                override val contentType: ContentType? = content.contentType
                override val contentLength: Long? = content.contentLength
                override val headers: Headers = content.headers
            },
        )
    }
}
