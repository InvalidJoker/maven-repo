package de.joker.config

import io.ktor.server.config.*

/**
 * Authentication settings parsed from the `auth` block of `application.yaml`.
 *
 * @property sessionSecret key used to sign session cookies; override in production.
 * @property sessionMaxAgeSeconds lifetime of the session cookie.
 * @property adminUsername / [adminPassword] seeded on first boot when no users exist.
 */
data class AuthConfig(
    val sessionSecret: String,
    val sessionMaxAgeSeconds: Long,
    val adminUsername: String,
    val adminPassword: String,
) {
    companion object {
        fun from(config: ApplicationConfig): AuthConfig {
            val auth = config.config("auth")
            return AuthConfig(
                sessionSecret = auth.property("session.secret").getString(),
                sessionMaxAgeSeconds = auth.propertyOrNull("session.maxAgeSeconds")
                    ?.getString()?.toLong() ?: (7 * 24 * 60 * 60),
                adminUsername = auth.property("admin.username").getString(),
                adminPassword = auth.property("admin.password").getString(),
            )
        }
    }
}
