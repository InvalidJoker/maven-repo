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
