package de.joker.service.proxy

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class UpstreamToken(
    val token: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 300,
)

/**
 * The pull half of the Docker token handshake, played against an *upstream* registry: the registry answers
 * `401` with a `Bearer realm=…,service=…,scope=…` challenge, the realm hands out a short-lived token for that
 * scope, and the request is retried with it. Docker Hub, ghcr.io and quay.io all require this even for public
 * images, which is why anonymous tokens are enough here.
 */
class UpstreamRegistryAuth(private val client: HttpClient) {

    private class Issued(val header: String, val expiresAt: Instant)

    private val logger = LoggerFactory.getLogger(UpstreamRegistryAuth::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val tokens = ConcurrentHashMap<String, Issued>()

    fun forImage(remote: String, image: String): UpstreamAuth =
        UpstreamAuth { _, challenge -> header(remote, image, challenge) }

    private suspend fun header(remote: String, image: String, challenge: String?): String? {
        val key = "$remote|$image"
        val cached = tokens[key]?.takeIf { it.expiresAt.isAfter(Instant.now()) }
        if (challenge == null) return cached?.header

        val parsed = runCatching { parseAuthorizationHeader(challenge) }.getOrNull() as? HttpAuthHeader.Parameterized
        if (parsed == null || !parsed.authScheme.equals(AuthScheme.Bearer, ignoreCase = true)) return null
        val realm = parsed.parameter("realm") ?: return null

        val url = URLBuilder(realm).apply {
            parsed.parameter("service")?.let { parameters.append("service", it) }
            parameters.append("scope", parsed.parameter("scope") ?: "repository:$image:pull")
        }.buildString()

        val issued = runCatching {
            val response = client.get(url)
            if (!response.status.isSuccess()) return@runCatching null
            json.decodeFromString(UpstreamToken.serializer(), response.readRawBytes().decodeToString())
        }.getOrElse {
            logger.warn("Upstream token request to {} failed: {}", url, it.toString())
            null
        } ?: return null

        val value = issued.token ?: issued.accessToken ?: return null
        // Expire a little early so a token cannot go stale between the check and the request that uses it.
        val ttl = (issued.expiresIn - 30).coerceAtLeast(30)
        tokens[key] = Issued("Bearer $value", Instant.now().plusSeconds(ttl))
        return "Bearer $value"
    }
}
