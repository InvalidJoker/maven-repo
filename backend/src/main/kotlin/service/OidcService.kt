package de.joker.service

import de.joker.config.OidcConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
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

    /** Whether OIDC is configured (endpoints are discovered lazily, on demand). */
    val enabled: Boolean get() = config.enabled

    val buttonLabel: String get() = config.buttonLabel

    /** Best-effort discovery at startup; failures are retried lazily on the next login. */
    suspend fun initialize() {
        if (config.enabled) discover()
    }

    /** Ensures the provider endpoints are known, discovering on demand. Returns false if unavailable. */
    suspend fun ensureDiscovered(): Boolean {
        if (!config.enabled) return false
        if (metadata != null) return true
        return discover()
    }

    private suspend fun discover(): Boolean =
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
            log.info("OIDC endpoints discovered for issuer ${config.issuer}")
        }.onFailure {
            log.error("OIDC discovery failed for issuer ${config.issuer} (will retry on next login): ${it.message}")
        }.isSuccess

    /** Builds the authorization-code + PKCE redirect URL. */
    fun authorizeUrl(redirectUri: String, state: String, codeChallenge: String): String {
        val m = metadata ?: error("OIDC not initialized")
        return URLBuilder(m.authorizationEndpoint).apply {
            parameters.append("response_type", "code")
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", redirectUri)
            parameters.append("scope", config.scopes.joinToString(" "))
            parameters.append("state", state)
            parameters.append("code_challenge", codeChallenge)
            parameters.append("code_challenge_method", "S256")
        }.buildString()
    }

    /** Exchanges an authorization code (with PKCE verifier) for an access token. */
    suspend fun exchangeCode(redirectUri: String, code: String, codeVerifier: String): String {
        val m = metadata ?: error("OIDC not initialized")
        val response = http.submitForm(
            url = m.tokenEndpoint,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("code_verifier", codeVerifier)
            },
        )
        if (!response.status.isSuccess()) {
            error("token endpoint ${m.tokenEndpoint} returned ${response.status}: ${response.bodyAsText()}")
        }
        val json: JsonObject = response.body()
        return json["access_token"]?.jsonPrimitive?.contentOrNull
            ?: error("token response missing access_token: $json")
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
