package de.joker.auth

import kotlinx.serialization.Serializable

/** Access level on a repository. [WRITE] implies [READ]. */
@Serializable
enum class Permission {
    READ,
    WRITE;

    fun allows(required: Permission): Boolean = this >= required
}

/** Caller of a registry endpoint, resolved from a session cookie, an access token or a registry bearer token. */
data class RegistryPrincipal(
    val userId: Int,
    val admin: Boolean,
    val tokenId: Int?,
)
