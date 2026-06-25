package de.joker.auth

import at.favre.lib.crypto.bcrypt.BCrypt

/** Hashes and verifies passwords using bcrypt. */
class PasswordHasher(private val cost: Int = 12) {

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(cost, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}
