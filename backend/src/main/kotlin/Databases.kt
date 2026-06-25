package de.joker

import de.joker.config.DatabaseConfig
import de.joker.database.DatabaseService
import de.joker.database.UserTable
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

/**
 * Connects to the configured database, failing fast on misconfiguration, and creates any
 * missing tables. This is the single place where Exposed tables are registered for creation.
 */
suspend fun Application.configureDatabases() {
    val config by inject<DatabaseConfig>()
    val databaseService by inject<DatabaseService>()

    val target = when (val c = config) {
        is DatabaseConfig.H2 -> "H2 (embedded, file=${c.filePath})"
        is DatabaseConfig.Postgres -> "PostgreSQL (${c.host}:${c.port}/${c.database})"
    }
    log.info("Initializing database: $target")

    databaseService.initSchema(UserTable)
}
