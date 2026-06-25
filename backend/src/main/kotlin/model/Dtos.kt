package de.joker.model

import de.joker.auth.Permission
import kotlinx.serialization.Serializable

@Serializable
data class RepositoryDto(val id: Int, val name: String, val private: Boolean)

@Serializable
data class CreateRepositoryRequest(val name: String, val private: Boolean = false)

@Serializable
data class GrantPermissionRequest(val username: String, val permission: Permission)

@Serializable
data class RepositoryPermissionDto(val username: String, val permission: Permission)

@Serializable
data class UserRepositoryDto(val name: String, val private: Boolean, val permission: Permission)

@Serializable
data class ScopeDto(val repository: String, val permission: Permission)

@Serializable
data class CreateTokenRequest(val name: String, val scopes: List<ScopeDto> = emptyList())

@Serializable
data class UpdateTokenRequest(val name: String, val scopes: List<ScopeDto> = emptyList())

@Serializable
data class TokenDto(val id: Int, val name: String, val scopes: List<ScopeDto>)

@Serializable
data class CreatedTokenDto(val token: String, val info: TokenDto)

@Serializable
data class BrowseEntry(val name: String, val directory: Boolean, val size: Long? = null)

@Serializable
data class ArtifactInfo(
    val groupId: String,
    val artifactId: String,
    val versions: List<String>,
    val latestVersion: String,
)

@Serializable
data class VersionInfo(val groupId: String, val artifactId: String, val version: String)

@Serializable
data class SearchResultDto(
    val path: String,
    val groupId: String,
    val artifactId: String,
    val latestVersion: String,
)

@Serializable
data class BrowseResponse(
    val repository: String,
    val path: String,
    val entries: List<BrowseEntry>,
    val artifact: ArtifactInfo? = null,
    val version: VersionInfo? = null,
)
