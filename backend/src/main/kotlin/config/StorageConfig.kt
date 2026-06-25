package de.joker.config

import io.ktor.server.config.*

sealed interface StorageConfig {

    data class Local(
        val path: String,
    ) : StorageConfig

    data class S3(
        val bucket: String,
        val region: String,
        val accessKeyId: String,
        val secretAccessKey: String,
        val endpoint: String?,
    ) : StorageConfig

    companion object {
        fun from(config: ApplicationConfig): StorageConfig {
            val storage = config.config("storage")

            return when (
                val type = storage.property("type").getString().lowercase()
            ) {
                "local", "filesystem", "file" -> {
                    val local = storage.config("local")

                    Local(
                        path = local.property("path").getString(),
                    )
                }

                "s3" -> {
                    val s3 = storage.config("s3")

                    S3(
                        bucket = s3.property("bucket").getString(),
                        region = s3.property("region").getString(),
                        accessKeyId = s3.property("accessKeyId").getString(),
                        secretAccessKey = s3.property("secretAccessKey").getString(),
                        endpoint = s3.propertyOrNull("endpoint")?.getString()?.takeIf { it.isNotBlank() },
                    )
                }

                else -> throw IllegalArgumentException(
                    "Unknown storage.type '$type'. Supported values: 'local', 's3'."
                )
            }
        }
    }
}