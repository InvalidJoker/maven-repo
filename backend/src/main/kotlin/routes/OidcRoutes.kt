package de.joker.routes

import de.joker.auth.OidcFlowSession
import de.joker.auth.UserSession
import de.joker.service.OidcService
import de.joker.service.UserService
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Manual OIDC authorization-code flow with PKCE (required by providers like Pocket ID). Mounted
 * only when a provider is configured; endpoints are discovered lazily on the login request.
 */
fun Route.oidcRoutes(oidc: OidcService, users: UserService) {
    get("/auth/oidc/login") {
        if (!oidc.ensureDiscovered()) {
            call.respondRedirect("/#/login?sso=unavailable")
            return@get
        }
        val state = randomToken()
        val verifier = randomToken()
        val redirectUri = call.oidcCallbackUrl()
        call.sessions.set(OidcFlowSession(state, verifier, redirectUri))
        call.respondRedirect(oidc.authorizeUrl(redirectUri, state, codeChallenge(verifier)))
    }

    get("/auth/oidc/callback") {
        val log = call.application.log
        val flow = call.sessions.get<OidcFlowSession>()
        call.sessions.clear<OidcFlowSession>()

        val params = call.request.queryParameters
        val code = params["code"]
        val state = params["state"]

        val providerError = params["error"]
        when {
            providerError != null ->
                log.warn("OIDC callback error from provider: $providerError ${params["error_description"].orEmpty()}")
            flow == null -> log.warn("OIDC callback without a flow cookie (expired, or cookies blocked)")
            code == null || state == null -> log.warn("OIDC callback missing code/state")
            state != flow.state -> log.warn("OIDC callback state mismatch")
        }
        if (providerError != null || flow == null || code == null || state == null || state != flow.state) {
            call.respondRedirect("/#/login?sso=error")
            return@get
        }

        val user = runCatching {
            val token = oidc.exchangeCode(flow.redirectUri, code, flow.codeVerifier)
            users.provisionOidcUser(oidc.fetchUser(token).username)
        }.getOrElse {
            log.error("OIDC token exchange / userinfo failed", it)
            call.respondRedirect("/#/login?sso=error")
            return@get
        }

        call.sessions.set(UserSession(user.id, user.username, user.admin))
        call.respondRedirect("/")
    }
}

/** Public callback URL, derived from the (proxy-aware) request origin. */
private fun ApplicationCall.oidcCallbackUrl(): String {
    val origin = request.origin
    val defaultPort = if (origin.scheme == "https") 443 else 80
    val port = if (origin.serverPort == defaultPort) "" else ":${origin.serverPort}"
    return "${origin.scheme}://${origin.serverHost}$port/auth/oidc/callback"
}

private fun randomToken(): String {
    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun codeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
