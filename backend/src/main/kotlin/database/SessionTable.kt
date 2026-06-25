package de.joker.database

import org.jetbrains.exposed.v1.core.Table

/** Persisted server-side sessions, keyed by the opaque session id stored in the cookie. */
object SessionTable : Table("sessions") {
    val id = varchar("id", 128)
    val data = text("data")

    override val primaryKey = PrimaryKey(id)
}
