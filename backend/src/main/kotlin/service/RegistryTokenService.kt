package de.joker.service

import de.joker.auth.RegistryPrincipal
import de.joker.config.AuthConfig
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Short-lived bearer tokens for the Docker registry auth flow: `/v2/` challenges with a `Bearer` realm, the client
 * exchanges its access token for one of these at `/v2/token` and replays it on every request.
 *
 * The token only identifies the caller — permissions are re-checked per request — so it is a signed payload rather
 * than a stored grant. Anonymous tokens carry no user and exist so public pulls can complete the same handshake.
 */
class RegistryTokenService(config: AuthConfig) {

    private val key = SecretKeySpec(config.sessionSecret.toByteArray(), ALGORITHM)

    class Issued(val token: String, val expiresInSeconds: Long, val issuedAt: Instant)

    /** A verified token. A null [principal] is a valid token for an anonymous caller. */
    class Verified(val principal: RegistryPrincipal?)

    fun issue(principal: RegistryPrincipal?): Issued {
        val issuedAt = Instant.now()
        val expiry = issuedAt.epochSecond + TTL_SECONDS
        val payload = listOf(
            principal?.userId?.toString() ?: "-",
            if (principal?.admin == true) "1" else "0",
            principal?.tokenId?.toString() ?: "-",
            expiry.toString(),
        ).joinToString(":")

        val encoded = payload.toByteArray().encode()
        return Issued("$encoded.${sign(encoded)}", TTL_SECONDS, issuedAt)
    }

    fun verify(token: String): Verified? {
        val (encoded, signature) = token.split('.', limit = 2).takeIf { it.size == 2 } ?: return null
        if (!constantTimeEquals(sign(encoded), signature)) return null

        val parts = runCatching { Base64.getUrlDecoder().decode(encoded).decodeToString() }
            .getOrNull()?.split(':') ?: return null
        if (parts.size != 4) return null
        if ((parts[3].toLongOrNull() ?: return null) < Instant.now().epochSecond) return null

        val userId = parts[0].toIntOrNull() ?: return Verified(null)
        return Verified(RegistryPrincipal(userId, admin = parts[1] == "1", tokenId = parts[2].toIntOrNull()))
    }

    private fun sign(value: String): String =
        Mac.getInstance(ALGORITHM).apply { init(key) }.doFinal(value.toByteArray()).encode()

    private fun ByteArray.encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

    private fun constantTimeEquals(a: String, b: String): Boolean =
        a.length == b.length && a.indices.fold(0) { acc, i -> acc or (a[i].code xor b[i].code) } == 0

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val TTL_SECONDS = 300L
    }
}
