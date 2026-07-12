package de.joker.config

import io.ktor.server.config.*

data class OidcConfig(
    val enabled: Boolean,
    val issuer: String,
    val clientId: String,
    val clientSecret: String,
    val scopes: List<String>,
    val buttonLabel: String,
) {
    companion object {
        fun from(config: ApplicationConfig): OidcConfig {
            val oidc = config.config("auth.oidc")
            val issuer = oidc.propertyOrNull("issuer")?.getString()?.trim().orEmpty()
            val clientId = oidc.propertyOrNull("clientId")?.getString()?.trim().orEmpty()
            val clientSecret = oidc.propertyOrNull("clientSecret")?.getString()?.trim().orEmpty()
            val scopes = oidc.propertyOrNull("scopes")?.getString()
                ?.split(' ', ',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("openid", "profile", "email")
            val label = oidc.propertyOrNull("buttonLabel")?.getString()?.trim()
                ?.ifEmpty { null } ?: "Sign in with SSO"

            return OidcConfig(
                enabled = issuer.isNotEmpty() && clientId.isNotEmpty() && clientSecret.isNotEmpty(),
                issuer = issuer,
                clientId = clientId,
                clientSecret = clientSecret,
                scopes = scopes,
                buttonLabel = label,
            )
        }
    }
}
