package com.spring.musk.antibloater.ui.presets

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spring.musk.antibloater.model.AdbShellManager
import com.spring.musk.antibloater.model.AppRepository
import com.spring.musk.antibloater.model.OemPresets
import com.spring.musk.antibloater.model.RemovedAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PresetUiState {
    object Idle : PresetUiState()
    data class Applying(val current: Int, val total: Int) : PresetUiState()
    data class Done(val count: Int) : PresetUiState()
}

@RequiresApi(Build.VERSION_CODES.O)
class PresetsViewModel(application: Application) : AndroidViewModel(application) {

    private val shellManager = AdbShellManager.getInstance(application.applicationContext)
    private val appRepository = AppRepository(application.applicationContext)
    private val removedAppsRepo = RemovedAppsRepository(application.applicationContext)

    private val _state = MutableStateFlow<PresetUiState>(PresetUiState.Idle)
    val state: StateFlow<PresetUiState> = _state.asStateFlow()

    val presets: List<OemPresets.Preset> = OemPresets.all
    val detectedPreset: OemPresets.Preset? = OemPresets.detectPreset()

    /**
     * Apply a preset — uninstalls only packages that are actually installed.
     */
    fun applyPreset(preset: OemPresets.Preset) {
        viewModelScope.launch(Dispatchers.IO) {
            val installedApps = appRepository.getInstalledApps(forceReload = false)
            val installedPackages = installedApps.map { it.packageName }.toSet()

            // Only remove packages from preset that are actually on device
            val toRemove = preset.packages.filter { it in installedPackages }

            if (toRemove.isEmpty()) {
                _state.value = PresetUiState.Done(0)
                return@launch
            }

            val total = toRemove.size
            toRemove.forEachIndexed { index, pkg ->
                _state.value = PresetUiState.Applying(index + 1, total)
                val appName = installedApps.find { it.packageName == pkg }?.appName ?: pkg
                shellManager.sendCommand("pm uninstall -k --user 0 $pkg")
                removedAppsRepo.trackRemoval(pkg, appName)
                delay(300)
            }

            appRepository.invalidateCache()
            _state.value = PresetUiState.Done(total)
        }
    }

    fun resetState() {
        _state.value = PresetUiState.Idle
    }
}
