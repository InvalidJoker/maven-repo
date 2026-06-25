package de.joker.config

import io.ktor.server.config.*

data class AuthConfig(
    val sessionSecret: String,
    val sessionMaxAgeSeconds: Long,
    val adminUsername: String,
) {
    companion object {
        fun from(config: ApplicationConfig): AuthConfig {
            val auth = config.config("auth")
            return AuthConfig(
                sessionSecret = auth.property("session.secret").getString(),
                sessionMaxAgeSeconds = auth.propertyOrNull("session.maxAgeSeconds")
                    ?.getString()?.toLong() ?: (7 * 24 * 60 * 60),
                adminUsername = auth.property("admin.username").getString(),
            )
        }
    }
}
