package com.spring.musk.antibloater.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spring.musk.antibloater.app
import com.spring.musk.antibloater.data.Animations
import com.spring.musk.antibloater.data.AppTrackers
import com.spring.musk.antibloater.data.Background
import com.spring.musk.antibloater.data.Grant
import com.spring.musk.antibloater.data.Permissions
import com.spring.musk.antibloater.data.Starter
import com.spring.musk.antibloater.data.Trackers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrackerScan(val done: Int = 0, val total: Int = 0, val results: List<AppTrackers>? = null) {
    val running get() = results == null && total > 0
}

class ToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    private val pm = application.packageManager
    private val adb get() = app.connection.adb

    private val _grants = MutableStateFlow<List<Grant>?>(null)
    val grants: StateFlow<List<Grant>?> = _grants.asStateFlow()

    private val _scan = MutableStateFlow(TrackerScan())
    val scan: StateFlow<TrackerScan> = _scan.asStateFlow()

    private val _starters = MutableStateFlow<List<Starter>?>(null)
    val starters: StateFlow<List<Starter>?> = _starters.asStateFlow()

    private val _scale = MutableStateFlow(Animations.current(application.contentResolver))
    val scale: StateFlow<Float?> = _scale.asStateFlow()

    /** One-line result of the last action, shown as a snackbar. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    /** One live fact per tool for the Tools hub. */
    fun facts(): Map<Tool, String> = buildMap {
        _grants.value?.let { g ->
            val n = g.filter { !it.system && it.group in setOf(
                com.spring.musk.antibloater.data.Sensitive.Location, com.spring.musk.antibloater.data.Sensitive.Camera,
                com.spring.musk.antibloater.data.Sensitive.Microphone) }.map { it.pkg }.toSet().size
            put(Tool.Permissions, "${Format.count(n, "app")} · location, camera, mic")
        }
        put(Tool.Trackers, _scan.value.results?.let { "${Format.count(it.size, "app")} with trackers · of ${_scan.value.total}" }
            ?: "Not scanned yet")
        _starters.value?.let { put(Tool.Background, "${Format.count(it.count { s -> !s.system }, "app")} start at boot") }
        put(Tool.Animations, when (val v = _scale.value) { null -> "Settings differ"; 1f -> "Now 1×, the default"; 0f -> "Now off"; else -> "Now ${v}×" })
    }

    fun loadGrants() = viewModelScope.launch(Dispatchers.IO) { _grants.value = Permissions.granted(pm) }

    fun revoke(grant: Grant) = viewModelScope.launch {
        val out = Permissions.revoke(adb, grant)
        _notice.value = if (out.ok) "${grant.group.label} taken away from ${grant.label}" else out.text.ifBlank { "Couldn't revoke it" }
        loadGrants()
    }

    fun scanTrackers() {
        if (_scan.value.running) return
        _scan.value = TrackerScan(total = 1)
        viewModelScope.launch(Dispatchers.IO) {
            val results = Trackers.scan(pm) { done, total -> _scan.value = TrackerScan(done, total) }
            _scan.value = _scan.value.copy(results = results)
        }
    }

    fun loadStarters() = viewModelScope.launch {
        val restricted = if (app.connection.status.value.isConnected) Background.restricted(adb) else null
        _starters.value = withContext(Dispatchers.IO) { Background.starters(pm, restricted) }
    }

    fun setRestricted(starter: Starter, restricted: Boolean) = viewModelScope.launch {
        val out = Background.setRestricted(adb, starter.pkg, restricted)
        if (!out.ok) _notice.value = out.text.ifBlank { "Couldn't change it" }
        loadStarters()
    }

    fun setScale(value: Float) = viewModelScope.launch {
        val out = Animations.set(adb, value)
        if (out.ok) _scale.value = value else _notice.value = out.text.ifBlank { "Couldn't change it" }
    }
}
