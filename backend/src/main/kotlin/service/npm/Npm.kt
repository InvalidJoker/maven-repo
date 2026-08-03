package de.joker.service.npm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import java.util.Base64

// https://github.com/npm/validate-npm-package-name — optional `@scope/`, lowercase and URL-safe.
private val PACKAGE_NAME = Regex("^(@[a-z0-9\\-~][a-z0-9\\-._~]*/)?[a-z0-9\\-~][a-z0-9\\-._~]*$")
private val TARBALL_NAME = Regex("^[A-Za-z0-9@][A-Za-z0-9._\\-]*\\.tgz$")

fun isValidPackageName(name: String): Boolean = name.length <= 214 && PACKAGE_NAME.matches(name)

fun isValidTarballName(file: String): Boolean = TARBALL_NAME.matches(file)

/** Storage paths, relative to the repository root. A scoped name simply nests one level deeper. */
object NpmLayout {
    const val PACKAGES = "packages"

    fun packageRoot(name: String): String = "$PACKAGES/$name"

    fun packument(name: String): String = "${packageRoot(name)}/packument.json"

    fun tarballs(name: String): String = "${packageRoot(name)}/-"

    fun tarball(name: String, file: String): String = "${tarballs(name)}/$file"
}

/**
 * What we persist per package. Version manifests are kept **verbatim** as published — npm puts arbitrary fields
 * in them (bin, scripts, engines, peerDependencies, …) and clients read them back — with only `dist` rewritten.
 */
@Serializable
data class StoredPackument(
    val name: String,
    val distTags: Map<String, String> = emptyMap(),
    val versions: Map<String, JsonObject> = emptyMap(),
    val time: Map<String, String> = emptyMap(),
) {
    val latest: String? get() = distTags["latest"] ?: versions.keys.maxWithOrNull(VERSION_ORDER)

    fun tagsOf(version: String): List<String> =
        distTags.filterValues { it == version }.keys.sorted()
}

internal val npmJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Tarball name npm itself generates: the unscoped package name plus the version. */
fun tarballNameFor(name: String, version: String): String = "${name.substringAfterLast('/')}-$version.tgz"

fun sha1Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

fun sha512Integrity(bytes: ByteArray): String =
    "sha512-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(bytes))

/**
 * Compares semver-ish versions numerically per segment so `1.10.0` sorts above `1.9.0`. Pre-release suffixes
 * fall back to string order, which is enough for picking a latest version to display.
 */
val VERSION_ORDER: Comparator<String> = Comparator { a, b ->
    val left = a.split('.', '-')
    val right = b.split('.', '-')
    for (i in 0 until maxOf(left.size, right.size)) {
        val x = left.getOrNull(i) ?: "0"
        val y = right.getOrNull(i) ?: "0"
        val xi = x.toIntOrNull()
        val yi = y.toIntOrNull()
        val cmp = if (xi != null && yi != null) xi.compareTo(yi) else x.compareTo(y)
        if (cmp != 0) return@Comparator cmp
    }
    0
}
