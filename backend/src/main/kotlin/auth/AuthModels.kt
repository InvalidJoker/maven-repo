package de.joker.auth

import kotlinx.serialization.Serializable

/** Credentials submitted to `POST /auth/login`. */
@Serializable
data class LoginRequest(val username: String, val password: String)

/** Public-facing user representation (never includes the password hash). */
@Serializable
data class UserDto(val id: Int, val username: String)

/** Data persisted in the (signed) session cookie. */
@Serializable
data class UserSession(val userId: Int, val username: String)
