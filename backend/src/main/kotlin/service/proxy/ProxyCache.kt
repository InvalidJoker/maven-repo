package de.joker.service.proxy

import de.joker.service.storage.StorageBackend
import de.joker.service.storage.StorageObject
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class ProxyOutcome {
    SERVED,
    NOT_FOUND,
    UPSTREAM_ERROR,

    /** The transfer broke after the response had already started; the caller must not respond again. */
    ABORTED,
}

class UpstreamResponse(val status: HttpStatusCode, val headers: Headers, val body: ByteArray) {
    val successful: Boolean get() = status.isSuccess()
}

/**
 * Supplies the `Authorization` header for upstream requests. It is asked once before the request and, when the
 * upstream answers `401`, again with that response's `WWW-Authenticate` value — which is the whole Docker
 * registry token handshake.
 */
fun interface UpstreamAuth {
    suspend fun header(url: String, challenge: String?): String?

    companion object {
        val None = UpstreamAuth { _, _ -> null }
    }
}

private const val MAX_REDIRECTS = 5

/** Upstream 404s are remembered briefly: Maven clients ask for `.sha256`, `.asc` and `.module` files constantly. */
private val MISS_TTL: Duration = Duration.ofMinutes(5)
private const val MAX_MISSES = 20_000

/**
 * The caching half of a proxy repository: content that has been fetched once is stored in the repository like a
 * published artifact, so a second request is served locally and the upstream is only consulted for what is
 * missing or expired.
 *
 * Redirects are followed by hand (the client has `followRedirects` off) because upstream registries hand blobs
 * off to a CDN, and forwarding the `Authorization` header to that other host both breaks the CDN's own
 * signature check and leaks the credential.
 */
class ProxyCache(private val client: HttpClient, private val storage: StorageBackend) {

    private val logger = LoggerFactory.getLogger(ProxyCache::class.java)
    private val misses = ConcurrentHashMap<String, Instant>()

    /** The cached copy of [path], or null when it is absent or older than [ttl] (a null [ttl] never expires). */
    suspend fun cached(repository: String, path: String, ttl: Duration? = null): StorageObject? {
        if (!fresh(repository, path, ttl)) return null
        return storage.read(repository, path)
    }

    suspend fun cachedBytes(repository: String, path: String, ttl: Duration? = null): ByteArray? =
        cached(repository, path, ttl)?.use { it.stream.readBytes() }

    suspend fun fresh(repository: String, path: String, ttl: Duration?): Boolean {
        val entry = storage.stat(repository, path) ?: return false
        if (ttl == null) return true
        val at = entry.lastModified ?: return false
        return at.plus(ttl).isAfter(Instant.now())
    }

    suspend fun store(repository: String, path: String, bytes: ByteArray) {
        storage.write(repository, path, bytes.inputStream())
        forgetMiss(repository, path)
    }

    fun missed(repository: String, path: String): Boolean {
        val until = misses["$repository|$path"] ?: return false
        if (until.isAfter(Instant.now())) return true
        forgetMiss(repository, path)
        return false
    }

    private fun rememberMiss(repository: String, path: String) {
        if (misses.size > MAX_MISSES) misses.clear()
        misses["$repository|$path"] = Instant.now().plus(MISS_TTL)
    }

    private fun forgetMiss(repository: String, path: String) {
        misses.remove("$repository|$path")
    }

    /** Fetches a document small enough to hold in memory. Returns null when the upstream is unreachable. */
    suspend fun fetch(
        url: String,
        auth: UpstreamAuth = UpstreamAuth.None,
        method: HttpMethod = HttpMethod.Get,
        accept: List<String> = emptyList(),
    ): UpstreamResponse? = try {
        request(url, method, auth, accept) { response ->
            val body = if (method == HttpMethod.Head) ByteArray(0) else response.readRawBytes()
            UpstreamResponse(response.status, response.headers, body)
        }
    } catch (e: Exception) {
        logger.warn("Upstream request to {} failed: {}", url, e.toString())
        null
    }

    /**
     * Streams [url] to the caller and into the cache at the same time, so the first client to ask for an
     * artifact pays for the download but does not wait for it to be stored first.
     */
    suspend fun stream(
        call: ApplicationCall,
        repository: String,
        path: String,
        url: String,
        contentType: ContentType,
        auth: UpstreamAuth = UpstreamAuth.None,
        accept: List<String> = emptyList(),
        headers: Map<String, String> = emptyMap(),
    ): ProxyOutcome {
        var started = false
        return try {
            request(url, HttpMethod.Get, auth, accept) { response ->
                when {
                    response.status == HttpStatusCode.NotFound -> {
                        rememberMiss(repository, path)
                        ProxyOutcome.NOT_FOUND
                    }

                    !response.status.isSuccess() -> {
                        logger.warn("Upstream {} answered {}", url, response.status)
                        ProxyOutcome.UPSTREAM_ERROR
                    }

                    else -> {
                        headers.forEach { (name, value) -> call.response.header(name, value) }
                        tee(call, repository, path, response, contentType) { started = true }
                        forgetMiss(repository, path)
                        ProxyOutcome.SERVED
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Upstream download of {} failed: {}", url, e.toString())
            if (started) ProxyOutcome.ABORTED else ProxyOutcome.UPSTREAM_ERROR
        }
    }

    private suspend fun tee(
        call: ApplicationCall,
        repository: String,
        path: String,
        response: HttpResponse,
        contentType: ContentType,
        onStart: () -> Unit,
    ) {
        val temp = withContext(Dispatchers.IO) { File.createTempFile("proxy", ".part") }
        try {
            onStart()
            call.respondBytesWriter(contentType, HttpStatusCode.OK, response.contentLength()) {
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                withContext(Dispatchers.IO) { temp.outputStream() }.use { file ->
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read == 0) continue
                        withContext(Dispatchers.IO) { file.write(buffer, 0, read) }
                        writeFully(buffer, 0, read)
                    }
                }
                flush()
            }
            withContext(Dispatchers.IO) { temp.inputStream() }.use { storage.write(repository, path, it) }
        } finally {
            withContext(Dispatchers.IO) { temp.delete() }
        }
    }

    private sealed interface Step<out T> {
        data class Done<T>(val value: T) : Step<T>
        data class Follow(val url: String) : Step<Nothing>
        data class Challenged(val challenge: String?) : Step<Nothing>
    }

    private suspend fun <T> request(
        url: String,
        method: HttpMethod,
        auth: UpstreamAuth,
        accept: List<String>,
        block: suspend (HttpResponse) -> T,
    ): T {
        var target = url
        var authorization = auth.header(target, null)
        var redirects = 0
        var challenged = false

        while (true) {
            val step = client.prepareRequest(target) {
                this.method = method
                accept.forEach { header(HttpHeaders.Accept, it) }
                authorization?.let { header(HttpHeaders.Authorization, it) }
            }.execute { response ->
                val location = response.headers[HttpHeaders.Location]
                when {
                    response.status.value in 300..399 && location != null && redirects < MAX_REDIRECTS ->
                        Step.Follow(URLBuilder(target).takeFrom(location).buildString())

                    response.status == HttpStatusCode.Unauthorized && !challenged ->
                        Step.Challenged(response.headers[HttpHeaders.WWWAuthenticate])

                    else -> Step.Done(block(response))
                }
            }

            when (step) {
                is Step.Done -> return step.value

                is Step.Follow -> {
                    redirects++
                    // A redirect off the registry's own host is the CDN handoff: it carries its own signature
                    // and must not see our credential.
                    if (Url(step.url).hostWithPort != Url(target).hostWithPort) authorization = null
                    target = step.url
                }

                // Retried once with whatever the challenge produced; a second 401 falls through to the caller.
                is Step.Challenged -> {
                    challenged = true
                    authorization = auth.header(target, step.challenge)
                }
            }
        }
    }
}

/** Joins an upstream base URL with a repository-relative path. */
fun upstreamUrl(remote: String, path: String): String =
    remote.trimEnd('/') + "/" + path.trimStart('/')
