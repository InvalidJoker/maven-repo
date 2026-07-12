package de.joker.auth

import kotlinx.serialization.Serializable

@Serializable
data class OidcFlowSession(val state: String, val codeVerifier: String, val redirectUri: String)
