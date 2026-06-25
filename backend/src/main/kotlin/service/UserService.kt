package de.joker.service

import de.joker.auth.PasswordHasher
import de.joker.auth.UserDto
import de.joker.database.DatabaseService
import de.joker.database.UserTable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

/** Persistence and authentication for application [UserTable] rows. */
class UserService(
    private val db: DatabaseService,
    private val hasher: PasswordHasher,
) {

    /** Creates a user with a bcrypt-hashed password. */
    suspend fun createUser(username: String, password: String, admin: Boolean = false): UserDto = db.query {
        val row = UserTable.insert {
            it[UserTable.username] = username
            it[passwordHash] = hasher.hash(password)
            it[UserTable.admin] = admin
        }
        UserDto(row[UserTable.id].value, username, admin)
    }

    /** Returns the user when the password matches, otherwise null. */
    suspend fun authenticate(username: String, password: String): UserDto? = db.query {
        UserTable.selectAll()
            .where { UserTable.username eq username }
            .map {
                it[UserTable.passwordHash] to
                    UserDto(it[UserTable.id].value, it[UserTable.username], it[UserTable.admin])
            }
            .singleOrNull()
    }?.let { (hash, user) -> if (hasher.verify(password, hash)) user else null }

    /** Looks up a user by username, or null if none exists. */
    suspend fun findByUsername(username: String): UserDto? = db.query {
        UserTable.selectAll()
            .where { UserTable.username eq username }
            .map { UserDto(it[UserTable.id].value, it[UserTable.username], it[UserTable.admin]) }
            .singleOrNull()
    }

    /** Total number of registered users; used to decide whether to seed an admin. */
    suspend fun count(): Long = db.query { UserTable.selectAll().count() }
}
