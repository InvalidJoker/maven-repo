package de.joker.database

import de.joker.auth.Permission
import de.joker.model.DEFAULT_CACHE_TTL_SECONDS
import de.joker.model.RepositoryMode
import de.joker.model.RepositoryType
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object RepositoryTable : IntIdTable("repositories") {
    val name = varchar("name", 128).uniqueIndex()
    val private = bool("private").default(false)
    val type = enumerationByName<RepositoryType>("type", 16).default(RepositoryType.MAVEN)
    val mode = enumerationByName<RepositoryMode>("mode", 16).default(RepositoryMode.HOSTED)

    /** Upstreams of a proxy repository, one per line and in the order they are consulted. */
    val remoteUrls = text("remote_urls").nullable()

    /** How long mutable upstream documents (metadata, packuments, tags) stay usable before being refetched. */
    val cacheTtlSeconds = long("cache_ttl_seconds").default(DEFAULT_CACHE_TTL_SECONDS)
}

object RepositoryPermissionTable : IntIdTable("repository_permissions") {
    val repository = reference("repository_id", RepositoryTable)
    val user = reference("user_id", UserTable)
    val permission = enumerationByName<Permission>("permission", 16)

    init {
        uniqueIndex(repository, user)
    }
}
