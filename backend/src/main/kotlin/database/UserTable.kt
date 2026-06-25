package de.joker.database

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object UserTable : IntIdTable("users") {
    val username = varchar("username", 64).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val admin = bool("admin").default(false)
}
