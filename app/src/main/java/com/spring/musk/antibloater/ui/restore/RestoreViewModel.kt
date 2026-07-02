package com.spring.musk.antibloater.ui.restore

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spring.musk.antibloater.model.AdbShellManager
import com.spring.musk.antibloater.model.RemovedAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class RestoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RemovedAppsRepository(application.applicationContext)
    private val shellManager = AdbShellManager.getInstance(application.applicationContext)

    private val _apps = MutableStateFlow<List<RemovedAppsRepository.RemovedApp>>(emptyList())
    val apps: StateFlow<List<RemovedAppsRepository.RemovedApp>> = _apps.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    fun load() {
        _apps.value = repo.getAll()
    }

    fun restore(app: RemovedAppsRepository.RemovedApp) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRestoring.value = true
            shellManager.sendCommand(repo.getRestoreCommand(app.packageName))
            kotlinx.coroutines.delay(500)
            repo.trackRestored(app.packageName)
            _apps.value = repo.getAll()
            _isRestoring.value = false
        }
    }
}
