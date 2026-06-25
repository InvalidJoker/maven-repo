package de.joker

import de.joker.config.DatabaseConfig
import de.joker.database.AccessTokenScopeTable
import de.joker.database.AccessTokenTable
import de.joker.database.DatabaseService
import de.joker.database.RepositoryPermissionTable
import de.joker.database.RepositoryTable
import de.joker.database.SessionTable
import de.joker.database.UserTable
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

suspend fun Application.configureDatabases() {
    val config by inject<DatabaseConfig>()
    val databaseService by inject<DatabaseService>()

    val target = when (val c = config) {
        is DatabaseConfig.H2 -> "H2 (embedded, file=${c.filePath})"
        is DatabaseConfig.Postgres -> "PostgreSQL (${c.host}:${c.port}/${c.database})"
    }
    log.info("Initializing database: $target")

    databaseService.initSchema(
        UserTable,
        RepositoryTable,
        RepositoryPermissionTable,
        AccessTokenTable,
        AccessTokenScopeTable,
        SessionTable,
    )
}
