package de.joker.service

import de.joker.model.AccentColor
import de.joker.model.InstanceSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class InstanceSettingsService(dataPath: String) {
    private val settingsFile = File(dataPath, "settings.json")
    private val iconFile = File(dataPath, "icon")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        settingsFile.parentFile.mkdirs()
        if (!settingsFile.exists()) {
            store(Stored())
        }
    }

    @Serializable
    private data class Stored(
        val name: String = "Maven Repository",
        val iconContentType: String? = null,
        val iconUrl: String? = null,
        val accent: AccentColor = AccentColor.EMERALD,
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
            stored.iconContentType != null && iconFile.exists() -> "/api/instance/icon?v=${iconFile.lastModified()}"
            else -> null
        }
        return InstanceSettings(stored.name, iconUrl, stored.accent)
    }

    @Synchronized
    fun updateName(name: String) = store(load().copy(name = name))

    @Synchronized
    fun setAccent(accent: AccentColor) = store(load().copy(accent = accent))

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
