package de.joker.auth

import de.joker.model.RepositoryDto
import de.joker.model.RepositoryType
import de.joker.service.AccessControlService
import de.joker.service.AccessTokenService
import de.joker.service.RegistryTokenService
import de.joker.service.RepositoryService
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*

sealed interface AuthResult {
    data class User(val principal: RegistryPrincipal) : AuthResult

    data object Anonymous : AuthResult

    data object None : AuthResult

    data object Invalid : AuthResult
}

sealed interface RepoAccess {
    data class Granted(val repository: RepositoryDto, val principal: RegistryPrincipal?) : RepoAccess

    data class Denied(val reason: Reason, val authenticated: Boolean) : RepoAccess

    enum class Reason { NOT_FOUND, UNAUTHENTICATED, FORBIDDEN }
}

class RepositoryAccess(
    private val repositories: RepositoryService,
    private val tokens: AccessTokenService,
    private val accessControl: AccessControlService,
    private val registryTokens: RegistryTokenService,
) {

    suspend fun authenticate(call: ApplicationCall): AuthResult {
        call.sessions.get<UserSession>()?.let {
            return AuthResult.User(RegistryPrincipal(it.userId, it.admin, tokenId = null))
        }

        val header = call.request.parseAuthorizationHeader() ?: return AuthResult.None
        return when {
            header.authScheme.equals(AuthScheme.Bearer, ignoreCase = true) -> {
                val blob = (header as? HttpAuthHeader.Single)?.blob ?: return AuthResult.Invalid
                val verified = registryTokens.verify(blob) ?: return AuthResult.Invalid
                verified.principal?.let { AuthResult.User(it) } ?: AuthResult.Anonymous
            }

            header.authScheme.equals(AuthScheme.Basic, ignoreCase = true) -> {
                val credentials = call.request.basicAuthenticationCredentials() ?: return AuthResult.Invalid
                tokens.verify(credentials.name, credentials.password)
                    ?.let { AuthResult.User(it) }
                    ?: AuthResult.Invalid
            }

            else -> AuthResult.Invalid
        }
    }

    suspend fun check(
        call: ApplicationCall,
        repository: String,
        required: Permission,
        type: RepositoryType? = null,
    ): RepoAccess {
        val auth = authenticate(call)
        val principal = (auth as? AuthResult.User)?.principal
        val authenticated = principal != null

        val repo = repositories.findByName(repository)
        if (repo == null || (type != null && repo.type != type)) {
            return RepoAccess.Denied(RepoAccess.Reason.NOT_FOUND, authenticated)
        }
        if (!repo.private && required == Permission.READ) return RepoAccess.Granted(repo, principal)
        if (principal == null) return RepoAccess.Denied(RepoAccess.Reason.UNAUTHENTICATED, false)

        val effective = accessControl.effectivePermission(principal, repo.id)
        if (effective == null || !effective.allows(required)) {
            return RepoAccess.Denied(RepoAccess.Reason.FORBIDDEN, true)
        }
        return RepoAccess.Granted(repo, principal)
    }

    /** The caller's permission on [repo], or null when they cannot see it at all. */
    suspend fun effective(call: ApplicationCall, repo: RepositoryDto): Permission? {
        val principal = (authenticate(call) as? AuthResult.User)?.principal
        val granted = principal?.let { accessControl.effectivePermission(it, repo.id) }
        return granted ?: Permission.READ.takeUnless { repo.private }
    }

    /** Every repository of [type] the caller may read. */
    suspend fun readable(call: ApplicationCall, type: RepositoryType): List<RepositoryDto> {
        val principal = (authenticate(call) as? AuthResult.User)?.principal
        return repositories.list()
            .filter { it.type == type }
            .filter { repo ->
                !repo.private ||
                    (principal != null && accessControl.effectivePermission(principal, repo.id) != null)
            }
    }

    suspend fun verifyBasic(call: ApplicationCall): RegistryPrincipal? {
        val credentials = call.request.basicAuthenticationCredentials() ?: return null
        return tokens.verify(credentials.name, credentials.password)
    }

    fun issueToken(principal: RegistryPrincipal?): RegistryTokenService.Issued = registryTokens.issue(principal)
}
