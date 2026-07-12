package de.joker.service

import de.joker.config.OidcConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * OpenID Connect helper: discovers provider endpoints from the issuer, exposes Ktor OAuth server
 * settings for the authorization-code flow, and resolves the authenticated user from userinfo.
 */
class OidcService(private val config: OidcConfig, private val http: HttpClient) {

    private val log = LoggerFactory.getLogger(OidcService::class.java)

    private data class Metadata(
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val userinfoEndpoint: String,
    )

    data class OidcUser(val subject: String, val username: String)

    @Volatile
    private var metadata: Metadata? = null

    /** Whether OIDC is configured and its endpoints were discovered successfully. */
    val ready: Boolean get() = config.enabled && metadata != null

    val buttonLabel: String get() = config.buttonLabel

    /** Fetches the provider's discovery document. Failures disable OIDC without stopping the app. */
    suspend fun initialize() {
        if (!config.enabled) return
        runCatching {
            val url = config.issuer.trimEnd('/') + "/.well-known/openid-configuration"
            val doc: JsonObject = http.get(url).body()

            Metadata(
                authorizationEndpoint = doc.getString("authorization_endpoint"),
                tokenEndpoint = doc.getString("token_endpoint"),
                userinfoEndpoint = doc.getString("userinfo_endpoint"),
            )
        }.onSuccess {
            metadata = it
            log.info("OIDC enabled: discovered endpoints for issuer ${config.issuer}")
        }.onFailure {
            log.error("OIDC discovery failed for issuer ${config.issuer}; SSO disabled", it)
        }
    }

    fun serverSettings(): OAuthServerSettings.OAuth2ServerSettings? {
        val m = metadata ?: return null
        return OAuthServerSettings.OAuth2ServerSettings(
            name = "oidc",
            authorizeUrl = m.authorizationEndpoint,
            accessTokenUrl = m.tokenEndpoint,
            requestMethod = HttpMethod.Post,
            clientId = config.clientId,
            clientSecret = config.clientSecret,
            defaultScopes = config.scopes,
        )
    }

    /** Resolves the user identity from the userinfo endpoint. */
    suspend fun fetchUser(accessToken: String): OidcUser {
        val m = metadata ?: error("OIDC not initialized")
        val info: JsonObject = http.get(m.userinfoEndpoint) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.body()

        val subject = info["sub"]?.jsonPrimitive?.contentOrNull ?: error("userinfo missing 'sub'")
        val username = info["preferred_username"]?.jsonPrimitive?.contentOrNull
            ?: info["email"]?.jsonPrimitive?.contentOrNull?.substringBefore('@')
            ?: info["name"]?.jsonPrimitive?.contentOrNull
            ?: subject
        return OidcUser(subject, username)
    }

    private fun JsonObject.getString(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: error("discovery document missing '$key'")
}
