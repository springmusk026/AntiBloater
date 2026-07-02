package com.spring.musk.antibloater.ui.apps

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spring.musk.antibloater.model.AdbShellManager
import com.spring.musk.antibloater.model.AppCategory
import com.spring.musk.antibloater.model.AppInfo
import com.spring.musk.antibloater.model.AppRepository
import com.spring.musk.antibloater.model.RemovedAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AppsUiState {
    object Loading : AppsUiState()
    object Ready : AppsUiState()
    data class Uninstalling(val current: Int, val total: Int) : AppsUiState()
    data class UninstallComplete(val removedCount: Int, val freedBytes: Long) : AppsUiState()
}

/**
 * AppsViewModel — completely independent app management module.
 *
 * Owns: app list loading, filtering, category selection, batch uninstall.
 * Knows nothing about pairing, dashboard, or any other feature.
 *
 * Performance:
 *  - App list cached in repository (icons loaded once)
 *  - Search debounced 300ms
 *  - All filtering on Default dispatcher
 */
@RequiresApi(Build.VERSION_CODES.O)
class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application.applicationContext)
    private val shellManager = AdbShellManager.getInstance(application.applicationContext)
    private val removedAppsRepo = RemovedAppsRepository(application.applicationContext)

    // State
    private val _uiState = MutableStateFlow<AppsUiState>(AppsUiState.Loading)
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    // Internals
    private var allApps: List<AppInfo> = emptyList()
    private var currentCategory: AppCategory? = null
    private var currentQuery: String = ""
    private var searchJob: Job? = null

    /** If true, disable apps instead of uninstall (reversible without factory reset) */
    private var _useDisableMode = MutableStateFlow(false)
    val useDisableMode: StateFlow<Boolean> = _useDisableMode.asStateFlow()

    fun setDisableMode(enabled: Boolean) {
        _useDisableMode.value = enabled
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AppsUiState.Loading
            allApps = appRepository.getInstalledApps(forceReload = false)
            applyFilters()
            _uiState.value = AppsUiState.Ready
        }
    }

    fun setCategory(category: AppCategory?) {
        currentCategory = category
        viewModelScope.launch(Dispatchers.Default) { applyFilters() }
    }

    fun setSearchQuery(query: String) {
        currentQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(300)
            applyFilters()
        }
    }

    fun uninstallApps(apps: List<AppInfo>) {
        viewModelScope.launch(Dispatchers.IO) {
            val total = apps.size
            val freedBytes = apps.sumOf { it.sizeBytes }
            val disableMode = _useDisableMode.value

            apps.forEachIndexed { index, app ->
                _uiState.value = AppsUiState.Uninstalling(index + 1, total)
                val cmd = if (disableMode) {
                    "pm disable-user --user 0 ${app.packageName}"
                } else {
                    appRepository.getUninstallCommand(app.packageName)
                }
                shellManager.sendCommand(cmd)
                removedAppsRepo.trackRemoval(app.packageName, app.appName)
                delay(300)
            }

            appRepository.invalidateCache()
            allApps = appRepository.getInstalledApps(forceReload = true)
            applyFilters()

            // Show before/after summary
            _uiState.value = AppsUiState.UninstallComplete(total, freedBytes)
            delay(3000)
            _uiState.value = AppsUiState.Ready
        }
    }

    private fun applyFilters() {
        var result = allApps
        if (currentCategory != null) {
            result = appRepository.filterByCategory(result, currentCategory)
        }
        if (currentQuery.isNotBlank()) {
            result = appRepository.filterApps(result, currentQuery)
        }
        _apps.value = result
    }
}
