package de.joker.di

import de.joker.auth.DatabaseSessionStorage
import de.joker.auth.PasswordHasher
import de.joker.config.AuthConfig
import de.joker.config.DatabaseConfig
import de.joker.config.StorageConfig
import de.joker.database.DatabaseService
import de.joker.service.AccessControlService
import de.joker.service.AccessTokenService
import de.joker.service.InstanceSettingsService
import de.joker.service.RepositoryBrowserService
import de.joker.service.RepositoryService
import de.joker.service.storage.StorageBackend
import de.joker.service.UserService
import de.joker.service.storage.LocalStorageBackend
import de.joker.service.storage.S3StorageBackend
import io.ktor.server.config.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun appModule(config: ApplicationConfig) = module {
    single { DatabaseConfig.from(config) }
    single { AuthConfig.from(config) }
    single { StorageConfig.from(config) }
    single { PasswordHasher() }
    single {
        InstanceSettingsService(config.propertyOrNull("instance.dataPath")?.getString() ?: "./data/instance")
    }

    single<StorageBackend> {
        when (val storage = get<StorageConfig>()) {
            is StorageConfig.Local -> LocalStorageBackend(storage.path)
            is StorageConfig.S3 -> S3StorageBackend(storage)
        }
    }

    singleOf(::DatabaseService)
    singleOf(::DatabaseSessionStorage)
    singleOf(::UserService)
    singleOf(::RepositoryService)
    singleOf(::AccessTokenService)
    singleOf(::AccessControlService)
    singleOf(::RepositoryBrowserService)
}
