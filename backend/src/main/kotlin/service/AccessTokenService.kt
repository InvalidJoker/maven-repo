package de.joker.service

import de.joker.auth.MavenPrincipal
import de.joker.auth.Permission
import de.joker.database.AccessTokenScopeTable
import de.joker.database.AccessTokenTable
import de.joker.database.DatabaseService
import de.joker.database.UserTable
import de.joker.model.CreatedTokenDto
import de.joker.model.ScopeDto
import de.joker.model.TokenDto
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class AccessTokenService(private val db: DatabaseService) {

    private val random = SecureRandom()

    suspend fun listForUser(userId: Int): List<TokenDto> = db.query {
        val tokenRows = AccessTokenTable.selectAll()
            .where { AccessTokenTable.user eq userId }
            .map { it[AccessTokenTable.id].value to it[AccessTokenTable.name] }
            .toList()

        tokenRows.map { (id, name) ->
            val scopes = (AccessTokenScopeTable innerJoin de.joker.database.RepositoryTable).selectAll()
                .where { AccessTokenScopeTable.token eq id }
                .map { ScopeDto(it[de.joker.database.RepositoryTable.name], it[AccessTokenScopeTable.permission]) }
                .toList()
            TokenDto(id, name, scopes)
        }
    }

    suspend fun create(userId: Int, name: String, scopes: List<ResolvedScope>): CreatedTokenDto {
        val rawToken = generateToken()
        val tokenId = db.query {
            val row = AccessTokenTable.insert {
                it[user] = userId
                it[AccessTokenTable.name] = name
                it[tokenHash] = hash(rawToken)
            }
            val id = row[AccessTokenTable.id].value
            insertScopes(id, scopes)
            id
        }
        val info = TokenDto(tokenId, name, scopes.map { ScopeDto(it.repoName, it.permission) })
        return CreatedTokenDto(rawToken, info)
    }

    suspend fun update(userId: Int, tokenId: Int, name: String, scopes: List<ResolvedScope>): Boolean = db.query {
        val owns = AccessTokenTable.selectAll()
            .where { (AccessTokenTable.id eq tokenId) and (AccessTokenTable.user eq userId) }
            .map { it[AccessTokenTable.id].value }
            .singleOrNull() != null
        if (!owns) return@query false

        AccessTokenTable.update({ AccessTokenTable.id eq tokenId }) { it[AccessTokenTable.name] = name }
        AccessTokenScopeTable.deleteWhere { token eq tokenId }
        insertScopes(tokenId, scopes)
        true
    }

    suspend fun delete(userId: Int, tokenId: Int): Boolean = db.query {
        AccessTokenScopeTable.deleteWhere { token eq tokenId }
        AccessTokenTable.deleteWhere {
            (AccessTokenTable.id eq tokenId) and (AccessTokenTable.user eq userId)
        } > 0
    }

    /** Validates Basic-auth credentials (username + raw token) into a principal, or null. */
    suspend fun verify(username: String, rawToken: String): MavenPrincipal? = db.query {
        val hashed = hash(rawToken)
        (AccessTokenTable innerJoin UserTable).selectAll()
            .where { (UserTable.username eq username) and (AccessTokenTable.tokenHash eq hashed) }
            .map { MavenPrincipal(it[UserTable.id].value, it[UserTable.admin], it[AccessTokenTable.id].value) }
            .singleOrNull()
    }

    /** Repository-id -> permission scopes for a token; empty means unrestricted. */
    suspend fun scopesFor(tokenId: Int): Map<Int, Permission> = db.query {
        AccessTokenScopeTable.selectAll()
            .where { AccessTokenScopeTable.token eq tokenId }
            .map { it[AccessTokenScopeTable.repository].value to it[AccessTokenScopeTable.permission] }
            .toList()
            .toMap()
    }

    private suspend fun insertScopes(tokenId: Int, scopes: List<ResolvedScope>) {
        for (scope in scopes) {
            AccessTokenScopeTable.insert {
                it[token] = tokenId
                it[repository] = scope.repoId
                it[permission] = scope.permission
            }
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
