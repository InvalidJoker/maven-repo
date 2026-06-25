package de.joker.di

import de.joker.auth.DatabaseSessionStorage
import de.joker.auth.PasswordHasher
import de.joker.config.AuthConfig
import de.joker.config.DatabaseConfig
import de.joker.database.DatabaseService
import de.joker.service.AccessControlService
import de.joker.service.AccessTokenService
import de.joker.service.RepositoryBrowserService
import de.joker.service.RepositoryService
import de.joker.service.RepositoryStorageService
import de.joker.service.UserService
import io.ktor.server.config.*
import org.koin.dsl.module

/** Root Koin module wiring together application-wide singletons. */
fun appModule(config: ApplicationConfig) = module {
    single { DatabaseConfig.from(config) }
    single { AuthConfig.from(config) }
    single { DatabaseService(get()) }
    single { DatabaseSessionStorage(get()) }
    single { PasswordHasher() }
    single { UserService(get(), get()) }
    single { RepositoryService(get()) }
    single { AccessTokenService(get()) }
    single { AccessControlService(get(), get()) }
    single {
        RepositoryStorageService(
            config.propertyOrNull("repository.storagePath")?.getString() ?: "./data/repositories",
        )
    }
    single { RepositoryBrowserService(get()) }
}
