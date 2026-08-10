package de.joker.di

import de.joker.auth.DatabaseSessionStorage
import de.joker.auth.PasswordHasher
import de.joker.config.AuthConfig
import de.joker.config.DatabaseConfig
import de.joker.config.OidcConfig
import de.joker.config.StorageConfig
import de.joker.database.DatabaseService
import de.joker.service.AccessControlService
import de.joker.service.AccessTokenService
import de.joker.service.InstanceSettingsService
import de.joker.service.OidcService
import de.joker.auth.RepositoryAccess
import de.joker.service.MavenBrowserService
import de.joker.service.RegistryTokenService
import de.joker.service.RepositoryService
import de.joker.service.docker.BlobUploadSessions
import de.joker.service.docker.DockerBrowserService
import de.joker.service.docker.DockerRegistryService
import de.joker.service.npm.NpmBrowserService
import de.joker.service.npm.NpmRegistryService
import de.joker.service.proxy.DockerProxyService
import de.joker.service.proxy.MavenProxyService
import de.joker.service.proxy.NpmProxyService
import de.joker.service.proxy.ProxyCache
import de.joker.service.proxy.UpstreamRegistryAuth
import de.joker.service.storage.StorageBackend
import de.joker.service.UserService
import de.joker.service.storage.LocalStorageBackend
import de.joker.service.storage.S3StorageBackend
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.*
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun appModule(config: ApplicationConfig) = module {
    single { DatabaseConfig.from(config) }
    single { AuthConfig.from(config) }
    single { StorageConfig.from(config) }
    single { OidcConfig.from(config) }
    single { PasswordHasher() }
    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    /**
     * Client for upstream registries. Redirects are handled by [ProxyCache] itself so the `Authorization`
     * header is dropped at the CDN handoff, and the engine's request deadline is lifted because a proxied
     * layer or jar can legitimately take minutes.
     */
    single(named(PROXY_CLIENT)) {
        HttpClient(CIO) {
            followRedirects = false
            expectSuccess = false
            install(UserAgent) { agent = "artifact-forge" }
            engine {
                requestTimeout = 0
                endpoint { connectTimeout = 15_000 }
            }
        }
    }
    singleOf(::OidcService)
    single {
        InstanceSettingsService(
            dataPath = config.propertyOrNull("instance.dataPath")?.getString() ?: "./data/instance",
            demo = config.propertyOrNull("instance.demo")?.getString()?.toBoolean() ?: false,
        )
    }

    single<StorageBackend> {
        when (val storage = get<StorageConfig>()) {
            is StorageConfig.Local -> LocalStorageBackend(storage.path)
            is StorageConfig.S3 -> S3StorageBackend(storage)
        }
    }

    single {
        BlobUploadSessions(config.propertyOrNull("storage.uploadPath")?.getString() ?: "./data/uploads")
    }

    singleOf(::DatabaseService)
    singleOf(::DatabaseSessionStorage)
    singleOf(::UserService)
    singleOf(::RepositoryService)
    singleOf(::AccessTokenService)
    singleOf(::AccessControlService)
    singleOf(::RegistryTokenService)
    singleOf(::RepositoryAccess)
    singleOf(::MavenBrowserService)
    singleOf(::DockerRegistryService)
    singleOf(::DockerBrowserService)
    singleOf(::NpmRegistryService)
    singleOf(::NpmBrowserService)

    single { ProxyCache(get(named(PROXY_CLIENT)), get()) }
    single { UpstreamRegistryAuth(get(named(PROXY_CLIENT))) }
    singleOf(::MavenProxyService)
    singleOf(::DockerProxyService)
    singleOf(::NpmProxyService)
}

private const val PROXY_CLIENT = "proxy"
