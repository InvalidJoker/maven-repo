package de.joker.config

import io.ktor.server.config.*

sealed interface DatabaseConfig {
    val url: String
    val user: String
    val password: String

    data class H2(val filePath: String) : DatabaseConfig {
        override val url: String get() = "r2dbc:h2:file:///$filePath"
        override val user: String = "root"
        override val password: String = ""
    }

    data class Postgres(
        val host: String,
        val port: Int,
        val database: String,
        override val user: String,
        override val password: String,
    ) : DatabaseConfig {
        override val url: String get() = "r2dbc:postgresql://$host:$port/$database"
    }

    companion object {
        fun from(config: ApplicationConfig): DatabaseConfig {
            val database = config.config("database")
            return when (val type = database.property("type").getString().lowercase()) {
                "h2", "sqlite" -> H2(
                    filePath = database.property("h2.file").getString(),
                )

                "postgres", "postgresql" -> {
                    val postgres = database.config("postgres")
                    Postgres(
                        host = postgres.property("host").getString(),
                        port = postgres.property("port").getString().toInt(),
                        database = postgres.property("database").getString(),
                        user = postgres.property("user").getString(),
                        password = postgres.property("password").getString(),
                    )
                }

                else -> throw IllegalArgumentException(
                    "Unknown database.type '$type'. Supported values: 'h2', 'postgres'."
                )
            }
        }
    }
}
