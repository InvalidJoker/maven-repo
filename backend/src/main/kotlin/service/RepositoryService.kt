package de.joker.service

import de.joker.auth.Permission
import de.joker.database.DatabaseService
import de.joker.database.RepositoryPermissionTable
import de.joker.database.RepositoryTable
import de.joker.database.UserTable
import de.joker.model.CreateRepositoryRequest
import de.joker.model.RepositoryDto
import de.joker.model.RepositoryPermissionDto
import de.joker.model.ScopeDto
import de.joker.model.UpdateRepositoryRequest
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
import org.jetbrains.exposed.v1.r2dbc.update
import org.jetbrains.exposed.v1.r2dbc.upsert

data class ResolvedScope(val repoId: Int, val repoName: String, val permission: Permission)

class RepositoryService(private val db: DatabaseService) {

    suspend fun create(request: CreateRepositoryRequest): RepositoryDto = db.query {
        val row = RepositoryTable.insert {
            it[name] = request.name
            it[private] = request.private
            it[type] = request.type
            it[mode] = request.mode
            it[remoteUrls] = request.remoteUrls.joinToString("\n").takeIf { urls -> urls.isNotEmpty() }
            it[cacheTtlSeconds] = request.cacheTtlSeconds
        }
        RepositoryDto(
            id = row[RepositoryTable.id].value,
            name = request.name,
            private = request.private,
            type = request.type,
            mode = request.mode,
            remoteUrls = request.remoteUrls,
            cacheTtlSeconds = request.cacheTtlSeconds,
        )
    }

    suspend fun update(repoId: Int, request: UpdateRepositoryRequest) {
        db.query {
            RepositoryTable.update({ RepositoryTable.id eq repoId }) {
                request.private?.let { value -> it[private] = value }
                request.remoteUrls?.let { value -> it[remoteUrls] = value.joinToString("\n") }
                request.cacheTtlSeconds?.let { value -> it[cacheTtlSeconds] = value }
            }
        }
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

    suspend fun userPermission(userId: Int, repoId: Int): Permission? = db.query {
        RepositoryPermissionTable.selectAll()
            .where {
                (RepositoryPermissionTable.user eq userId) and (RepositoryPermissionTable.repository eq repoId)
            }
            .map { it[RepositoryPermissionTable.permission] }
            .singleOrNull()
    }

    suspend fun listPublic(): List<UserRepositoryDto> = db.query {
        RepositoryTable.selectAll()
            .where { RepositoryTable.private eq false }
            .map { it.toRepositoryDto().forUser(Permission.READ) }
            .toList()
    }

    suspend fun listForUser(userId: Int, admin: Boolean): List<UserRepositoryDto> = db.query {
        val repos = RepositoryTable.selectAll().map { it.toRepositoryDto() }.toList()

        if (admin) {
            repos.map { it.forUser(Permission.WRITE) }
        } else {
            val grants = RepositoryPermissionTable.selectAll()
                .where { RepositoryPermissionTable.user eq userId }
                .map { it[RepositoryPermissionTable.repository].value to it[RepositoryPermissionTable.permission] }
                .toList()
                .toMap()

            repos.mapNotNull { repo ->
                val permission = grants[repo.id] ?: Permission.READ.takeUnless { repo.private }
                permission?.let { repo.forUser(it) }
            }
        }
    }

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
        type = this[RepositoryTable.type],
        mode = this[RepositoryTable.mode],
        remoteUrls = this[RepositoryTable.remoteUrls]?.lines()?.filter { it.isNotBlank() }.orEmpty(),
        cacheTtlSeconds = this[RepositoryTable.cacheTtlSeconds],
    )
}

fun RepositoryDto.forUser(permission: Permission) =
    UserRepositoryDto(name, private, permission, type, mode, remoteUrls, cacheTtlSeconds)
