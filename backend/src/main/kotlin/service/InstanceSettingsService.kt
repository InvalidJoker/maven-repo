package de.joker.service

import de.joker.model.InstanceSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Instance branding (display name + optional custom icon), persisted as files under the data
 * folder rather than the database. The icon doubles as the browser favicon.
 */
class InstanceSettingsService(dataPath: String) {

    private val dir = File(dataPath, "instance").apply { mkdirs() }
    private val settingsFile = File(dir, "settings.json")
    private val iconFile = File(dir, "icon")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Serializable
    private data class Stored(
        val name: String = "Maven Repository",
        val iconContentType: String? = null,
        val iconUrl: String? = null,
    )

    data class Icon(val bytes: ByteArray, val contentType: String)

    @Synchronized
    private fun load(): Stored =
        runCatching { json.decodeFromString(Stored.serializer(), settingsFile.readText()) }.getOrDefault(Stored())

    @Synchronized
    private fun store(value: Stored) = settingsFile.writeText(json.encodeToString(Stored.serializer(), value))

    fun settings(): InstanceSettings {
        val stored = load()
        val iconUrl = when {
            stored.iconUrl != null -> stored.iconUrl
            // Version the uploaded-icon URL by mtime so the favicon refreshes after a re-upload.
            stored.iconContentType != null && iconFile.exists() -> "/api/instance/icon?v=${iconFile.lastModified()}"
            else -> null
        }
        return InstanceSettings(stored.name, iconUrl)
    }

    @Synchronized
    fun updateName(name: String) = store(load().copy(name = name))

    @Synchronized
    fun setIcon(bytes: ByteArray, contentType: String) {
        iconFile.writeBytes(bytes)
        store(load().copy(iconContentType = contentType, iconUrl = null))
    }

    @Synchronized
    fun setIconUrl(url: String) {
        iconFile.delete()
        store(load().copy(iconUrl = url, iconContentType = null))
    }

    @Synchronized
    fun clearIcon() {
        iconFile.delete()
        store(load().copy(iconContentType = null, iconUrl = null))
    }

    fun icon(): Icon? {
        val type = load().iconContentType ?: return null
        if (!iconFile.exists()) return null
        return Icon(iconFile.readBytes(), type)
    }
}
