package de.joker.service

import de.joker.auth.MavenPrincipal
import de.joker.auth.Permission

/** Resolves the effective [Permission] a [MavenPrincipal] has on a repository. */
class AccessControlService(
    private val repositories: RepositoryService,
    private val tokens: AccessTokenService,
) {

    /**
     * Returns the effective permission, or null if the principal has no access.
     *
     * Admins always have full access. A session principal uses the user's granted permission.
     * A token principal is additionally restricted by its scopes: an unscoped token inherits the
     * user's permission, while a scoped token is limited to its listed repositories and capped at
     * the lower of the user's and the scope's level.
     */
    suspend fun effectivePermission(principal: MavenPrincipal, repoId: Int): Permission? {
        if (principal.admin) return Permission.WRITE

        val userPermission = repositories.userPermission(principal.userId, repoId) ?: return null

        val tokenId = principal.tokenId ?: return userPermission

        val scopes = tokens.scopesFor(tokenId)
        if (scopes.isEmpty()) return userPermission

        val scopedPermission = scopes[repoId] ?: return null
        return minOf(userPermission, scopedPermission)
    }
}
