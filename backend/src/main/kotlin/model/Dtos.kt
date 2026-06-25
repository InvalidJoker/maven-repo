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

/** A repository as seen by a specific user, including their effective permission on it. */
@Serializable
data class UserRepositoryDto(val name: String, val private: Boolean, val permission: Permission)

/** A repository restriction on an access token, identified by repository name. */
@Serializable
data class ScopeDto(val repository: String, val permission: Permission)

@Serializable
data class CreateTokenRequest(val name: String, val scopes: List<ScopeDto> = emptyList())

@Serializable
data class UpdateTokenRequest(val name: String, val scopes: List<ScopeDto> = emptyList())

@Serializable
data class TokenDto(val id: Int, val name: String, val scopes: List<ScopeDto>)

/** Returned once on creation; [token] is the secret and is never retrievable again. */
@Serializable
data class CreatedTokenDto(val token: String, val info: TokenDto)

/** A single entry (file or directory) in a repository directory listing. */
@Serializable
data class BrowseEntry(val name: String, val directory: Boolean, val size: Long? = null)

/** Maven coordinates for an artifact "folder" that holds version subdirectories. */
@Serializable
data class ArtifactInfo(
    val groupId: String,
    val artifactId: String,
    val versions: List<String>,
    val latestVersion: String,
)

/** Maven coordinates for a single published version directory. */
@Serializable
data class VersionInfo(val groupId: String, val artifactId: String, val version: String)

/**
 * A directory listing in the repository browser. [artifact] is set when the directory holds
 * versions (install steps target the latest), [version] when it is a concrete version directory.
 */
@Serializable
data class BrowseResponse(
    val repository: String,
    val path: String,
    val entries: List<BrowseEntry>,
    val artifact: ArtifactInfo? = null,
    val version: VersionInfo? = null,
)
