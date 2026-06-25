package de.joker.database

import de.joker.config.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.io.File

/**
 * Owns the Exposed [R2dbcDatabase] connection and provides the entry point for all
 * database access. Inject this via Koin wherever persistence is needed.
 */
class DatabaseService(config: DatabaseConfig) {

    val database: R2dbcDatabase = connect(config)

    /** Runs [block] inside a suspending transaction against the configured database. */
    suspend fun <T> query(block: suspend R2dbcTransaction.() -> T): T =
        suspendTransaction(database) { block() }

    /** Creates any missing tables for the given Exposed [tables]. Safe to call on every boot. */
    suspend fun initSchema(vararg tables: Table) {
        if (tables.isEmpty()) return
        query { SchemaUtils.create(*tables) }
    }

    private fun connect(config: DatabaseConfig): R2dbcDatabase {
        if (config is DatabaseConfig.H2) {
            // H2 will not create missing parent directories for a file database itself.
            File(config.filePath).absoluteFile.parentFile?.mkdirs()
        }
        return R2dbcDatabase.connect(
            url = config.url,
            user = config.user,
            password = config.password,
        )
    }
}
