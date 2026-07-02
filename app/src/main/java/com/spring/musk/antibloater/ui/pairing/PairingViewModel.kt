package com.spring.musk.antibloater.ui.pairing

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spring.musk.antibloater.model.AdbConnectionManager
import com.spring.musk.antibloater.model.AdbShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PairingState {
    object Checking : PairingState()
    object Required : PairingState()
    object Connecting : PairingState()
    object Connected : PairingState()
    data class Failed(val message: String) : PairingState()
}

/**
 * PairingViewModel — owns the entire pairing + connection lifecycle.
 * Completely independent module. Knows nothing about apps, dashboard, etc.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PairingViewModel(application: Application) : AndroidViewModel(application) {

    val shellManager = AdbShellManager.getInstance(application.applicationContext)
    val connectionManager = AdbConnectionManager.getInstance(application.applicationContext)

    private val _state = MutableStateFlow<PairingState>(PairingState.Checking)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private var retryCount = 0

    /**
     * Check if connection is already possible (debugging on + previously paired).
     */
    fun checkConnectionStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = PairingState.Checking

            if (!connectionManager.isDebuggingEnabled()) {
                _state.value = PairingState.Required
                return@launch
            }

            if (connectionManager.needsToPair()) {
                _state.value = PairingState.Required
                return@launch
            }

            // Try to connect directly
            connect()
        }
    }

    /**
     * Attempt pairing with port + code.
     */
    fun pair(port: String, code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = PairingState.Connecting

            val success = connectionManager.pair(port, code)
            if (success) {
                retryCount = 0
                connectionManager.setPairedBefore(true)
                connect()
            } else {
                retryCount++
                if (retryCount >= AdbConnectionManager.MAX_PAIR_RETRIES) {
                    _state.value = PairingState.Failed(
                        "Too many failed attempts. Please restart and try again."
                    )
                } else {
                    _state.value = PairingState.Failed("Pairing failed. Check port and code.")
                }
            }
        }
    }

    /**
     * Skip pairing — goes to dashboard in disconnected state.
     * Does NOT permanently disable auto-shell so next launch will try again.
     */
    fun skip() {
        _state.value = PairingState.Connected // Navigate forward but not truly connected
    }

    /**
     * Attempt to start ADB server and connect.
     */
    private fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = PairingState.Connecting

            if (!connectionManager.isDebuggingEnabled()) {
                _state.value = PairingState.Required
                return@launch
            }

            val success = connectionManager.initServer()
            if (success) {
                _state.value = PairingState.Connected
            } else {
                _state.value = PairingState.Required
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Don't destroy shell here — it's shared with AppsViewModel via singleton
    }
}
