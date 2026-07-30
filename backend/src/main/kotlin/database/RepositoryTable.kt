package de.joker.database

import de.joker.auth.Permission
import de.joker.model.RepositoryType
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object RepositoryTable : IntIdTable("repositories") {
    val name = varchar("name", 128).uniqueIndex()
    val private = bool("private").default(false)
    val type = enumerationByName<RepositoryType>("type", 16).default(RepositoryType.MAVEN)
}

object RepositoryPermissionTable : IntIdTable("repository_permissions") {
    val repository = reference("repository_id", RepositoryTable)
    val user = reference("user_id", UserTable)
    val permission = enumerationByName<Permission>("permission", 16)

    init {
        uniqueIndex(repository, user)
    }
}
