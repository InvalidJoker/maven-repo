package de.joker.database

import de.joker.config.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.io.File

class DatabaseService(config: DatabaseConfig) {

    val database: R2dbcDatabase = connect(config)

    suspend fun <T> query(block: suspend R2dbcTransaction.() -> T): T =
        suspendTransaction(database) { block() }

    /** Creates missing tables and columns. There are no migrations, so existing columns are never altered. */
    suspend fun initSchema(vararg tables: Table) {
        if (tables.isEmpty()) return
        query { SchemaUtils.createMissingTablesAndColumns(*tables) }
    }

    private fun connect(config: DatabaseConfig): R2dbcDatabase {
        if (config is DatabaseConfig.H2) {
            File(config.filePath).absoluteFile.parentFile?.mkdirs()
        }
        return R2dbcDatabase.connect(
            url = config.url,
            user = config.user,
            password = config.password,
        )
    }
}
