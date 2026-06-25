package de.joker

import de.joker.config.DatabaseConfig
import de.joker.database.DatabaseService
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

/**
 * Eagerly establishes the database connection on startup so misconfiguration fails fast,
 * and is the single place to register schema creation as tables are added.
 */
fun Application.configureDatabases() {
    val config by inject<DatabaseConfig>()
    val databaseService by inject<DatabaseService>()

    val target = when (val c = config) {
        is DatabaseConfig.H2 -> "H2 (embedded, file=${c.filePath})"
        is DatabaseConfig.Postgres -> "PostgreSQL (${c.host}:${c.port}/${c.database})"
    }
    log.info("Initializing database: $target")

    // Touch the connection so any failure surfaces during boot rather than on first request.
    databaseService.database

    // Register schema creation here as tables are introduced, e.g.:
    // launch { databaseService.initSchema(Artifacts, Repositories) }
}
