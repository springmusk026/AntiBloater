package com.spring.musk.antibloater.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spring.musk.antibloater.app
import com.spring.musk.antibloater.data.Action
import com.spring.musk.antibloater.data.Event
import com.spring.musk.antibloater.data.Outcome
import com.spring.musk.antibloater.data.Pkg
import com.spring.musk.antibloater.data.PkgState
import com.spring.musk.antibloater.data.Profile
import com.spring.musk.antibloater.data.Removal
import com.spring.musk.antibloater.data.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which packages the Apps list shows. */
enum class Shown(val label: String) { Installed("On phone"), Off("Removed"), All("All") }

data class Filters(
    val query: String = "",
    val level: Removal? = Removal.Recommended,
    val shown: Shown = Shown.Installed,
    val userApps: Boolean = false,
)

/** A batch of changes in progress or just finished. */
data class Work(
    val action: Action,
    val total: Int,
    val done: Int = 0,
    val current: String = "",
    val outcomes: List<Outcome>? = null,
) {
    val finished get() = outcomes != null
}

data class HomeState(
    val device: String,
    val loading: Boolean,
    /** Installed packages UAD-ng recommends removing. */
    val recommended: List<Pkg>,
    val removed: Int,
    /** Packages we removed that are back, usually after a system update. */
    val cameBack: List<Pkg>,
    val recent: List<Event>,
    val changes: Int,
) {
    val recommendedBySource get() = recommended.groupingBy { it.source }.eachCount()
        .toList().sortedByDescending { it.second }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    val connection = app.connection
    val status = connection.status
    val history = app.history.events
    val prefs = app.prefs

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _work = MutableStateFlow<Work?>(null)
    val work: StateFlow<Work?> = _work.asStateFlow()

    val packages: StateFlow<List<Pkg>?> = app.packages.all

    val home: StateFlow<HomeState> = combine(app.packages.all, app.history.events) { all, events ->
        homeState(all, events)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, homeState(null, emptyList()))

    val visible: StateFlow<List<Pkg>> = combine(app.packages.all, _filters) { all, f -> filter(all.orEmpty(), f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Counts per level for the chips, under the current "shown" and search filters. */
    val levelCounts: StateFlow<Map<Removal?, Int>> = combine(app.packages.all, _filters) { all, f ->
        val base = filter(all.orEmpty(), f.copy(level = null))
        base.groupingBy<Pkg, Removal?> { it.removal }.eachCount() + (null to base.size)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setFilters(f: Filters) { _filters.value = f }

    fun showLevel(level: Removal?, shown: Shown = Shown.Installed) {
        _filters.value = Filters(level = level, shown = shown)
        _selection.value = emptySet()
    }

    fun toggle(pkg: String) {
        _selection.value = _selection.value.let { if (pkg in it) it - pkg else it + pkg }
    }

    fun selectAll(pkgs: List<Pkg>) { _selection.value = pkgs.map { it.name }.toSet() }
    fun clearSelection() { _selection.value = emptySet() }

    fun run(targets: List<Pkg>, action: Action) = run(mapOf(action to targets))

    /** Runs each action on its packages one by one, reporting progress and verified results. */
    fun run(groups: Map<Action, List<Pkg>>) {
        val total = groups.values.sumOf { it.size }
        if (total == 0 || _work.value?.finished == false) return
        _work.value = Work(groups.keys.first(), total)
        viewModelScope.launch {
            val outcomes = mutableListOf<Outcome>()
            for ((action, targets) in groups) {
                val offset = outcomes.size
                outcomes += app.debloater.apply(targets, action) { done, pkg ->
                    _work.value = _work.value?.copy(done = offset + done, current = pkg.label)
                }
            }
            _selection.value = _selection.value - outcomes.filter { it.ok }.map { it.pkg.name }.toSet()
            _work.value = _work.value?.copy(done = total, outcomes = outcomes)
        }
    }

    fun dismissWork() { _work.value = null }

    fun blockedReason(pkg: Pkg): String? = app.debloater.blockedReason(pkg, app.packages.protected())

    fun reapply(pkgs: List<Pkg>) {
        val intended = app.history.intended()
        run(pkgs.groupBy { intended[it.name]?.action ?: Action.Uninstall })
    }

    /** Restores everything removed in one batch (an "undo"). */
    fun undoBatch(batch: Long) {
        val names = history.value.filter { it.batch == batch && it.action != Action.Restore }.map { it.pkg }.toSet()
        run(packages.value.orEmpty().filter { it.name in names && it.state != PkgState.Installed }, Action.Restore)
    }

    fun exportProfile(): String = app.history.exportProfile()

    /** Packages from a profile that are on this phone and not already in the profile's state. */
    fun profileTargets(profile: Profile): Map<Action, List<Pkg>> {
        val byName = packages.value.orEmpty().associateBy { it.name }
        return profile.packages.mapNotNull { item ->
            val pkg = byName[item.name] ?: return@mapNotNull null
            val already = (item.action == Action.Uninstall && pkg.state == PkgState.Removed) ||
                (item.action == Action.Disable && pkg.state == PkgState.Disabled)
            if (already) null else item.action to pkg
        }.groupBy({ it.first }, { it.second })
    }

    fun refresh() {
        connection.refresh()
        viewModelScope.launch { app.packages.reload() }
    }

    private fun homeState(all: List<Pkg>?, events: List<Event>): HomeState {
        val list = all.orEmpty()
        val intended = events.groupBy { it.pkg }.mapValues { (_, e) -> e.maxBy { it.at }.action }
        return HomeState(
            device = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            loading = all == null,
            recommended = list.filter { it.state == PkgState.Installed && it.removal == Removal.Recommended && it.system },
            removed = list.count { it.state != PkgState.Installed },
            cameBack = list.filter { it.state == PkgState.Installed && intended[it.name].let { a -> a == Action.Uninstall || a == Action.Disable } },
            recent = events.take(4),
            changes = events.size,
        )
    }

    companion object {
        fun filter(all: List<Pkg>, f: Filters): List<Pkg> {
            val q = f.query.trim().lowercase()
            return all.filter { pkg ->
                (f.userApps || pkg.system) &&
                    (f.level == null || pkg.removal == f.level) &&
                    when (f.shown) {
                        Shown.Installed -> pkg.state == PkgState.Installed
                        Shown.Off -> pkg.state != PkgState.Installed
                        Shown.All -> true
                    } &&
                    (q.isEmpty() || pkg.label.lowercase().contains(q) || pkg.name.contains(q))
            }
        }

        val sourceOrder = listOf(Source.Oem, Source.Carrier, Source.Google, Source.Misc, Source.Aosp, Source.Unknown)
    }
}
