package de.joker.auth

import kotlinx.serialization.Serializable

/** Access level on a repository. [WRITE] implies [READ]. */
@Serializable
enum class Permission {
    READ,
    WRITE;

    /** Whether this level satisfies the [required] level. */
    fun allows(required: Permission): Boolean = this >= required
}

/**
 * An authenticated actor for a Maven request.
 *
 * @property tokenId set when the request authenticated via an access token (not a session),
 * in which case the token's scopes further restrict the effective permission.
 */
data class MavenPrincipal(
    val userId: Int,
    val admin: Boolean,
    val tokenId: Int?,
)
