package com.spring.musk.antibloater.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** How safe a package is to remove, from UAD-ng's list. Ordered from safest. */
enum class Removal(val label: String, val help: String) {
    Recommended("Recommended", "Safe to remove. Nothing important depends on it."),
    Advanced("Advanced", "Usually safe, but you may lose a feature you use."),
    Expert("Expert", "Can break things. Only if you know what it does."),
    Unsafe("Unsafe", "Removing it can stop the phone from booting."),
    Unlisted("Not in list", "No one has reviewed this package yet.");
}

/** Where a package comes from, from UAD-ng's list. */
enum class Source(val label: String) {
    Oem("Manufacturer"), Carrier("Carrier"), Google("Google"), Aosp("Android"), Misc("Third party"), Unknown("Unknown");
}

@Serializable
data class Entry(
    val list: String = "",
    val description: String = "",
    val dependencies: List<String> = emptyList(),
    val neededBy: List<String> = emptyList(),
    @SerialName("removal") val removalName: String = "",
) {
    val removal: Removal get() = Removal.entries.firstOrNull { it.name == removalName } ?: Removal.Unlisted
    val source: Source get() = Source.entries.firstOrNull { it.name == list } ?: Source.Unknown

    /** The description without the store links and blank lines many entries carry. */
    val readable: String get() = description
        .replace(Regex("""\s*\(?https?://\S+?\)?(?=\s|$)"""), "")
        .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

/**
 * The Universal Android Debloater (UAD-ng) package list: what thousands of preinstalled
 * packages do and how safe they are to remove. A copy ships in the app; Update fetches
 * the latest one into app storage, which then takes precedence.
 */
class Knowledge(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val downloaded = File(context.filesDir, "uad_lists.json")

    private val _entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val entries: StateFlow<Map<String, Entry>> = _entries.asStateFlow()

    /** When the list in use was downloaded, or null for the copy bundled with the app. */
    val updatedAt: Long? get() = downloaded.takeIf { it.exists() }?.lastModified()

    suspend fun load() = withContext(Dispatchers.IO) {
        val text = runCatching { downloaded.readText() }.getOrNull()
            ?: context.assets.open("uad_lists.json").bufferedReader().use { it.readText() }
        _entries.value = runCatching { parse(text) }.getOrElse {
            downloaded.delete()
            parse(context.assets.open("uad_lists.json").bufferedReader().use { it.readText() })
        }
    }

    /** Downloads the latest list. Returns how many packages it describes. */
    suspend fun update(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(SOURCE).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = parse(text)
            require(parsed.size > 1000) { "The downloaded list looks incomplete" }
            File(context.filesDir, "uad_lists.json.tmp").apply { writeText(text); renameTo(downloaded) }
            _entries.value = parsed
            parsed.size
        }
    }

    private fun parse(text: String): Map<String, Entry> = json.decodeFromString(text)

    companion object {
        const val SOURCE = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/" +
            "universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
    }
}
