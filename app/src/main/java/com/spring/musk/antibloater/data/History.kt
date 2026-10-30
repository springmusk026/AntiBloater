package com.spring.musk.antibloater.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import java.io.File

enum class Action(val verb: String, val past: String) {
    Uninstall("Remove", "Removed"),
    Disable("Disable", "Disabled"),
    Restore("Restore", "Restored"),
}

/** One thing AntiBloater did, kept so it can be undone and re-applied after updates. */
@Serializable
data class Event(val pkg: String, val label: String, val action: Action, val at: Long, val batch: Long)

/** Everything AntiBloater changed on this phone, newest first. Stored in app storage only. */
class History(private val context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    fun add(new: List<Event>) {
        if (new.isEmpty()) return
        _events.value = (new + _events.value).take(LIMIT)
        file.writeText(json.encodeToString(_events.value))
    }

    /** The last action per package, i.e. what the phone should look like. */
    fun intended(): Map<String, Event> =
        _events.value.groupBy { it.pkg }.mapValues { (_, list) -> list.maxBy { it.at } }

    /** A profile of what's removed or disabled, to apply on another phone or after a reset. */
    fun exportProfile(): String {
        val packages = intended().values.filter { it.action != Action.Restore }.sortedBy { it.label.lowercase() }
        return json.encodeToString(Profile(device = "${Build.MANUFACTURER} ${Build.MODEL}",
            exportedAt = System.currentTimeMillis(),
            packages = packages.map { Profile.Item(it.pkg, it.label, it.action) }))
    }

    private fun load(): List<Event> {
        runCatching { return json.decodeFromString<List<Event>>(file.readText()) }
        return migrateV1()
    }

    /** 1.x kept a list of removed apps in shared preferences; carry it over once. */
    private fun migrateV1(): List<Event> {
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val old = prefs.getString("removed_apps_history", null) ?: return emptyList()
        val events = runCatching {
            val array = JSONArray(old)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val at = o.optLong("at", 0)
                Event(o.getString("pkg"), o.optString("name", o.getString("pkg")), Action.Uninstall, at, at)
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.at }
        file.writeText(json.encodeToString(events))
        prefs.edit().remove("removed_apps_history").apply()
        return events
    }

    companion object {
        private const val LIMIT = 5000
    }
}

@Serializable
data class Profile(
    val app: String = "AntiBloater",
    val format: Int = 2,
    val device: String = "",
    val exportedAt: Long = 0,
    val packages: List<Item> = emptyList(),
) {
    @Serializable
    data class Item(val name: String, val label: String = name, val action: Action = Action.Uninstall)

    companion object {
        /** Reads a profile from this version or from 1.x (`{"apps":[{"pkg":…}]}`). */
        fun parse(text: String): Profile? = runCatching {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(text).jsonObject
            if ("packages" in root) json.decodeFromJsonElement(serializer(), root)
            else Profile(device = root["device"]?.jsonPrimitive?.contentOrNull ?: "",
                packages = root["apps"]!!.jsonArray.map {
                    val o = it as JsonObject
                    val pkg = o["pkg"]!!.jsonPrimitive.content
                    Item(pkg, o["name"]?.jsonPrimitive?.contentOrNull ?: pkg)
                })
        }.getOrNull()?.takeIf { it.packages.isNotEmpty() }
    }
}
