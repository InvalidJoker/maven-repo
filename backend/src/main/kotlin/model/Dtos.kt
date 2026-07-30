package de.joker.model

import de.joker.auth.Permission
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Artifact format a repository holds. Maven artifacts and OCI/Docker images use different layouts and protocols. */
@Serializable
enum class RepositoryType { MAVEN, DOCKER }

@Serializable
data class RepositoryDto(val id: Int, val name: String, val private: Boolean, val type: RepositoryType)

@Serializable
data class CreateRepositoryRequest(
    val name: String,
    val private: Boolean = false,
    val type: RepositoryType = RepositoryType.MAVEN,
)

@Serializable
data class GrantPermissionRequest(val username: String, val permission: Permission)

@Serializable
data class CreateUserRequest(val username: String, val password: String, val admin: Boolean = false)

@Serializable
data class UpdateUserRequest(val admin: Boolean? = null, val password: String? = null)

@Serializable
data class RepositoryPermissionDto(val username: String, val permission: Permission)

@Serializable
data class UserRepositoryDto(
    val name: String,
    val private: Boolean,
    val permission: Permission,
    val type: RepositoryType,
)

@Serializable
enum class AccentColor { EMERALD, INDIGO, BLUE, VIOLET, ROSE, AMBER }

@Serializable
data class InstanceSettings(
    val name: String,
    val iconUrl: String? = null,
    val accent: AccentColor = AccentColor.EMERALD,
    val demo: Boolean = false,
    val oidc: Boolean = false,
    val oidcLabel: String? = null,
)

@Serializable
data class UpdateInstanceRequest(val name: String)

@Serializable
data class SetIconUrlRequest(val url: String)

@Serializable
data class SetAccentRequest(val accent: AccentColor)

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
enum class EntryKind {
    PACKAGE,
    VERSION,
    FOLDER,
    FILE,
}

@Serializable
data class BrowseEntry(
    val name: String,
    val directory: Boolean,
    val size: Long? = null,
    val kind: EntryKind = EntryKind.FILE,
)

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

@Serializable
data class DockerImageDto(val name: String, val tags: Int, val lastPushed: String? = null)

@Serializable
data class DockerTagDto(val tag: String, val digest: String? = null, val pushedAt: String? = null)

@Serializable
data class DockerLayerDto(val digest: String, val size: Long, val mediaType: String)

@Serializable
data class DockerPlatformDto(
    val digest: String,
    val os: String? = null,
    val architecture: String? = null,
    val variant: String? = null,
)

@Serializable
data class DockerManifestDto(
    val image: String,
    val reference: String,
    val digest: String,
    val mediaType: String,
    val manifestSize: Long,
    val totalSize: Long,
    val created: String? = null,
    val os: String? = null,
    val architecture: String? = null,
    val layers: List<DockerLayerDto> = emptyList(),
    val platforms: List<DockerPlatformDto> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
)
