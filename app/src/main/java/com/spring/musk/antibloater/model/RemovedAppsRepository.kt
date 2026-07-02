package com.spring.musk.antibloater.model

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks removed apps so they can be restored later.
 * Stores history in SharedPreferences as JSON.
 */
class RemovedAppsRepository(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val key = "removed_apps_history"

    data class RemovedApp(
        val packageName: String,
        val appName: String,
        val removedAt: Long
    )

    /**
     * Record an app as removed.
     */
    fun trackRemoval(packageName: String, appName: String) {
        val list = getAll().toMutableList()
        // Don't duplicate
        if (list.none { it.packageName == packageName }) {
            list.add(RemovedApp(packageName, appName, System.currentTimeMillis()))
            save(list)
        }
    }

    /**
     * Remove from history (after successful restore).
     */
    fun trackRestored(packageName: String) {
        val list = getAll().filter { it.packageName != packageName }
        save(list)
    }

    /**
     * Get all previously removed apps.
     */
    fun getAll(): List<RemovedApp> {
        val json = prefs.getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RemovedApp(
                    packageName = obj.getString("pkg"),
                    appName = obj.getString("name"),
                    removedAt = obj.optLong("at", 0)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Get the ADB command to restore an app.
     */
    fun getRestoreCommand(packageName: String): String {
        return "cmd package install-existing --user 0 $packageName"
    }

    private fun save(list: List<RemovedApp>) {
        val arr = JSONArray()
        list.forEach { app ->
            arr.put(JSONObject().apply {
                put("pkg", app.packageName)
                put("name", app.appName)
                put("at", app.removedAt)
            })
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    /**
     * Export current removal history as JSON string (for sharing/backup).
     */
    fun exportProfile(): String {
        val arr = JSONArray()
        getAll().forEach { app ->
            arr.put(JSONObject().apply {
                put("pkg", app.packageName)
                put("name", app.appName)
                put("at", app.removedAt)
            })
        }
        return JSONObject().apply {
            put("version", 1)
            put("device", android.os.Build.MODEL)
            put("manufacturer", android.os.Build.MANUFACTURER)
            put("exported_at", System.currentTimeMillis())
            put("apps", arr)
        }.toString(2)
    }

    /**
     * Import a profile JSON — returns list of package names to uninstall.
     */
    fun importProfile(json: String): List<Pair<String, String>> {
        return try {
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("apps")
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                Pair(item.getString("pkg"), item.getString("name"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
