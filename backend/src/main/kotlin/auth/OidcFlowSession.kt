package de.joker.auth

import kotlinx.serialization.Serializable

/** Short-lived cookie holding the in-flight OIDC login state and PKCE code verifier. */
@Serializable
data class OidcFlowSession(val state: String, val codeVerifier: String, val redirectUri: String)
