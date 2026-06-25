package de.joker

import de.joker.auth.DatabaseSessionStorage
import de.joker.auth.UserSession
import de.joker.config.AuthConfig
import de.joker.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.inject

const val AUTH_SESSION = "auth-session"
const val AUTH_ADMIN = "auth-admin"

suspend fun Application.configureAuth() {
    val authConfig by inject<AuthConfig>()
    val userService by inject<UserService>()
    val sessionStorage by inject<DatabaseSessionStorage>()

    // Hydrate the in-memory session cache from the database so logins survive restarts.
    sessionStorage.loadAll()

    install(Sessions) {
        cookie<UserSession>("user_session", sessionStorage) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.maxAgeInSeconds = authConfig.sessionMaxAgeSeconds
            transform(SessionTransportTransformerMessageAuthentication(authConfig.sessionSecret.toByteArray()))
        }
    }

    install(Authentication) {
        session<UserSession>(AUTH_SESSION) {
            validate { session -> session }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authentication required"))
            }
        }

        session<UserSession>(AUTH_ADMIN) {
            validate { session -> session.takeIf { it.admin } }
            challenge {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Administrator access required"))
            }
        }
    }

    if (userService.count() == 0L) {
        userService.createUser(authConfig.adminUsername, authConfig.adminPassword, admin = true)
        log.info("Seeded initial admin user '${authConfig.adminUsername}'")
    }
}
