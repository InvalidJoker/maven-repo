package de.joker.di

import de.joker.config.DatabaseConfig
import de.joker.database.DatabaseService
import io.ktor.server.config.*
import org.koin.dsl.module

/** Root Koin module wiring together application-wide singletons. */
fun appModule(config: ApplicationConfig) = module {
    single { DatabaseConfig.from(config) }
    single { DatabaseService(get()) }
}
