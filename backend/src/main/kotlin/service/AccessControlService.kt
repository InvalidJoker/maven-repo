package de.joker.service

import de.joker.auth.MavenPrincipal
import de.joker.auth.Permission

class AccessControlService(
    private val repositories: RepositoryService,
    private val tokens: AccessTokenService,
) {
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
