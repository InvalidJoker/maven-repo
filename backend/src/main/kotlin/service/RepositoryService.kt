package de.joker.service

import de.joker.auth.Permission
import de.joker.database.DatabaseService
import de.joker.database.RepositoryPermissionTable
import de.joker.database.RepositoryTable
import de.joker.database.UserTable
import de.joker.model.RepositoryDto
import de.joker.model.RepositoryPermissionDto
import de.joker.model.ScopeDto
import de.joker.model.UserRepositoryDto
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

/** A scope already resolved against the repository table (carries the repository id). */
data class ResolvedScope(val repoId: Int, val repoName: String, val permission: Permission)

/** Manages repositories and the admin-granted user permissions on them. */
class RepositoryService(private val db: DatabaseService) {

    suspend fun create(name: String, private: Boolean): RepositoryDto = db.query {
        val row = RepositoryTable.insert {
            it[RepositoryTable.name] = name
            it[RepositoryTable.private] = private
        }
        RepositoryDto(row[RepositoryTable.id].value, name, private)
    }

    suspend fun list(): List<RepositoryDto> = db.query {
        RepositoryTable.selectAll().map { it.toRepositoryDto() }.toList()
    }

    suspend fun findByName(name: String): RepositoryDto? = db.query {
        RepositoryTable.selectAll()
            .where { RepositoryTable.name eq name }
            .map { it.toRepositoryDto() }
            .singleOrNull()
    }

    suspend fun grant(repoId: Int, userId: Int, permission: Permission) {
        db.query {
            RepositoryPermissionTable.upsert(
                RepositoryPermissionTable.repository,
                RepositoryPermissionTable.user,
            ) {
                it[repository] = repoId
                it[user] = userId
                it[RepositoryPermissionTable.permission] = permission
            }
        }
    }

    suspend fun revoke(repoId: Int, userId: Int) {
        db.query {
            RepositoryPermissionTable.deleteWhere {
                (repository eq repoId) and (user eq userId)
            }
        }
    }

    suspend fun listPermissions(repoId: Int): List<RepositoryPermissionDto> = db.query {
        (RepositoryPermissionTable innerJoin UserTable).selectAll()
            .where { RepositoryPermissionTable.repository eq repoId }
            .map { RepositoryPermissionDto(it[UserTable.username], it[RepositoryPermissionTable.permission]) }
            .toList()
    }

    /** Effective permission a user has been granted on a repository, or null if none. */
    suspend fun userPermission(userId: Int, repoId: Int): Permission? = db.query {
        RepositoryPermissionTable.selectAll()
            .where {
                (RepositoryPermissionTable.user eq userId) and (RepositoryPermissionTable.repository eq repoId)
            }
            .map { it[RepositoryPermissionTable.permission] }
            .singleOrNull()
    }

    /** Public repositories, visible to anonymous visitors (read-only). */
    suspend fun listPublic(): List<UserRepositoryDto> = db.query {
        RepositoryTable.selectAll()
            .where { RepositoryTable.private eq false }
            .map { UserRepositoryDto(it[RepositoryTable.name], false, Permission.READ) }
            .toList()
    }

    /**
     * Repositories visible to a user with their effective permission: admins see everything as
     * writable, other users see repositories they were granted plus public ones as read-only.
     */
    suspend fun listForUser(userId: Int, admin: Boolean): List<UserRepositoryDto> = db.query {
        val repos = RepositoryTable.selectAll()
            .map { Triple(it[RepositoryTable.id].value, it[RepositoryTable.name], it[RepositoryTable.private]) }
            .toList()

        if (admin) {
            repos.map { (_, name, private) -> UserRepositoryDto(name, private, Permission.WRITE) }
        } else {
            val grants = RepositoryPermissionTable.selectAll()
                .where { RepositoryPermissionTable.user eq userId }
                .map { it[RepositoryPermissionTable.repository].value to it[RepositoryPermissionTable.permission] }
                .toList()
                .toMap()

            repos.mapNotNull { (id, name, private) ->
                val permission = grants[id] ?: Permission.READ.takeUnless { private }
                permission?.let { UserRepositoryDto(name, private, it) }
            }
        }
    }

    /** Resolves repository names in [scopes] to ids; returns null if any name is unknown. */
    suspend fun resolveScopes(scopes: List<ScopeDto>): List<ResolvedScope>? {
        val resolved = ArrayList<ResolvedScope>(scopes.size)
        for (scope in scopes) {
            val repo = findByName(scope.repository) ?: return null
            resolved += ResolvedScope(repo.id, repo.name, scope.permission)
        }
        return resolved
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRepositoryDto() = RepositoryDto(
        id = this[RepositoryTable.id].value,
        name = this[RepositoryTable.name],
        private = this[RepositoryTable.private],
    )
}
