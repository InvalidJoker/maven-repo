package de.joker.service

import de.joker.auth.PasswordHasher
import de.joker.auth.UserDto
import de.joker.database.DatabaseService
import de.joker.database.UserTable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.security.SecureRandom
import java.util.Base64

class UserService(
    private val db: DatabaseService,
    private val hasher: PasswordHasher,
) {

    suspend fun createUser(username: String, password: String, admin: Boolean = false): UserDto = db.query {
        val row = UserTable.insert {
            it[UserTable.username] = username
            it[passwordHash] = hasher.hash(password)
            it[UserTable.admin] = admin
        }
        UserDto(row[UserTable.id].value, username, admin)
    }

    suspend fun authenticate(username: String, password: String): UserDto? = db.query {
        UserTable.selectAll()
            .where { UserTable.username eq username }
            .map {
                it[UserTable.passwordHash] to
                    UserDto(it[UserTable.id].value, it[UserTable.username], it[UserTable.admin])
            }
            .singleOrNull()
    }?.let { (hash, user) -> if (hasher.verify(password, hash)) user else null }

    suspend fun findByUsername(username: String): UserDto? = db.query {
        UserTable.selectAll()
            .where { UserTable.username eq username }
            .map { UserDto(it[UserTable.id].value, it[UserTable.username], it[UserTable.admin]) }
            .singleOrNull()
    }

    suspend fun findById(id: Int): UserDto? = db.query {
        UserTable.selectAll()
            .where { UserTable.id eq id }
            .map { UserDto(it[UserTable.id].value, it[UserTable.username], it[UserTable.admin]) }
            .singleOrNull()
    }

    suspend fun update(userId: Int, admin: Boolean?, password: String?): Boolean = db.query {
        UserTable.update({ UserTable.id eq userId }) {
            if (admin != null) it[UserTable.admin] = admin
            if (password != null) it[passwordHash] = hasher.hash(password)
        } > 0
    }

    suspend fun countAdmins(): Long = db.query {
        UserTable.selectAll().where { UserTable.admin eq true }.count()
    }

    suspend fun list(): List<UserDto> = db.query {
        UserTable.selectAll()
            .map { UserDto(it[UserTable.id].value, it[UserTable.username], it[UserTable.admin]) }
            .toList()
    }

    suspend fun count(): Long = db.query { UserTable.selectAll().count() }

    suspend fun provisionOidcUser(username: String): UserDto {
        val name = username.trim().take(64).ifEmpty { "user" }
        return findByUsername(name) ?: createUser(name, randomPassword(), admin = false)
    }

    private fun randomPassword(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
