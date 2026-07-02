package com.spring.musk.antibloater.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spring.musk.antibloater.model.AppRepository
import com.spring.musk.antibloater.model.AppStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * DashboardViewModel — only responsible for loading stats.
 * Lightweight. Knows nothing about pairing, shell, or uninstall.
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application.applicationContext)

    private val _stats = MutableStateFlow(AppStats(0, 0, 0, 0, 0L))
    val stats: StateFlow<AppStats> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            appRepository.getInstalledApps(forceReload = false)
            _stats.value = appRepository.getStats()
            _isLoading.value = false
        }
    }
}
