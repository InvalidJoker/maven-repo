package de.joker.auth

import kotlinx.serialization.Serializable

/** Access level on a repository. [WRITE] implies [READ]. */
@Serializable
enum class Permission {
    READ,
    WRITE;

    fun allows(required: Permission): Boolean = this >= required
}

data class MavenPrincipal(
    val userId: Int,
    val admin: Boolean,
    val tokenId: Int?,
)
