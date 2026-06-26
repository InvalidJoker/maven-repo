package de.joker.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class UserDto(val id: Int, val username: String, val admin: Boolean)

@Serializable
data class UserSession(val userId: Int, val username: String, val admin: Boolean)
