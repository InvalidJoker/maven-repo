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
import java.security.SecureRandom
import java.util.Base64

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
        val password = generatePassword()
        userService.createUser(authConfig.adminUsername, password, admin = true)
        log.warn(
            """
            |
            |============================================================
            | Generated initial admin account — store this now, it is
            | shown only once and cannot be recovered:
            |
            |   username: ${authConfig.adminUsername}
            |   password: $password
            |
            | Sign in and change it (or create your own users) right away.
            |============================================================
            """.trimMargin(),
        )
    }
}

/** Generates a random URL-safe password for the seeded admin account. */
private fun generatePassword(): String {
    val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
