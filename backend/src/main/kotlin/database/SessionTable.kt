package de.joker.database

import org.jetbrains.exposed.v1.core.Table

object SessionTable : Table("sessions") {
    val id = varchar("id", 128)
    val data = text("data")

    override val primaryKey = PrimaryKey(id)
}
