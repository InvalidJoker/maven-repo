package de.joker.database

import de.joker.auth.Permission
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

/** User-owned access tokens used for Gradle/Maven Basic authentication. */
object AccessTokenTable : IntIdTable("access_tokens") {
    val user = reference("user_id", UserTable)
    val name = varchar("name", 128)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
}

/**
 * Optional per-token restriction to a specific repository. When a token has at least one scope
 * it may only access the listed repositories (capped by the owning user's own permission);
 * a token with no scopes inherits the user's full access.
 */
object AccessTokenScopeTable : IntIdTable("access_token_scopes") {
    val token = reference("token_id", AccessTokenTable)
    val repository = reference("repository_id", RepositoryTable)
    val permission = enumerationByName<Permission>("permission", 16)

    init {
        uniqueIndex(token, repository)
    }
}
