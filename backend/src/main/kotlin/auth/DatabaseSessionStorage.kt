package de.joker.auth

import de.joker.database.DatabaseService
import de.joker.database.SessionTable
import io.ktor.server.sessions.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert
import java.util.concurrent.ConcurrentHashMap

/**
 * Ktor [SessionStorage] that persists sessions in the database while serving reads from an
 * in-memory cache. The cache is hydrated from the database on startup via [loadAll], so
 * sessions survive restarts yet every read avoids a round-trip to the database.
 */
class DatabaseSessionStorage(private val db: DatabaseService) : SessionStorage {

    private val cache = ConcurrentHashMap<String, String>()

    /** Loads all persisted sessions into the in-memory cache. Call once on startup. */
    suspend fun loadAll() {
        val rows = db.query {
            SessionTable.selectAll()
                .map { it[SessionTable.id] to it[SessionTable.data] }
                .toList()
        }
        rows.forEach { (id, data) -> cache[id] = data }
    }

    override suspend fun write(id: String, value: String) {
        cache[id] = value
        db.query {
            SessionTable.upsert {
                it[SessionTable.id] = id
                it[data] = value
            }
        }
    }

    override suspend fun read(id: String): String {
        cache[id]?.let { return it }

        val value = db.query {
            SessionTable.selectAll()
                .where { SessionTable.id eq id }
                .map { it[SessionTable.data] }
                .singleOrNull()
        } ?: throw NoSuchElementException("Session $id not found")

        cache[id] = value
        return value
    }

    override suspend fun invalidate(id: String) {
        cache.remove(id)
        db.query {
            SessionTable.deleteWhere { SessionTable.id eq id }
        }
    }
}
