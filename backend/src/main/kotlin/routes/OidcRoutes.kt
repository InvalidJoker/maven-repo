package de.joker.routes

import de.joker.AUTH_OIDC
import de.joker.auth.UserSession
import de.joker.service.OidcService
import de.joker.service.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

/**
 * OIDC login flow, mounted only when a provider is configured. `/auth/oidc/login` starts the
 * authorization-code flow (Ktor redirects to the provider); `/auth/oidc/callback` receives the
 * exchanged token, resolves the user and establishes a session.
 */
fun Route.oidcRoutes(oidc: OidcService, users: UserService) {
    authenticate(AUTH_OIDC) {
        // Ktor's oauth provider redirects to the identity provider here.
        get("/auth/oidc/login") {}

        get("/auth/oidc/callback") {
            val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
            if (principal == null) {
                call.respondRedirect("/#/login")
                return@get
            }
            val oidcUser = oidc.fetchUser(principal.accessToken)
            val user = users.provisionOidcUser(oidcUser.username)
            call.sessions.set(UserSession(user.id, user.username, user.admin))
            call.respondRedirect("/")
        }
    }
}
