package com.spring.musk.antibloater.adb

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Where we are on the way to a working adb connection, in the order a user meets them. */
sealed interface Status {
    data object Checking : Status
    /** Android 10 and older have no wireless debugging: a computer has to run `adb tcpip 5555` once. */
    data object NeedsComputer : Status
    data object NeedsDeveloperOptions : Status
    data object NeedsWifi : Status
    data object NeedsWirelessDebugging : Status
    data object NeedsPairing : Status
    /** Waiting for the user to open "Pair device with pairing code" ([port] null) and type the code. */
    data class Pairing(val port: Int?, val error: String? = null, val busy: Boolean = false) : Status
    data object Connecting : Status
    data object Connected : Status
    data class Failed(val message: String) : Status

    val isConnected get() = this == Connected
}

/**
 * Owns the adb connection for the whole app. Watches the developer settings and
 * Wi-Fi so the status updates by itself while the user flips switches in Settings.
 */
class Connection(private val context: Context, val adb: Adb, private val scope: CoroutineScope) {
    private val _status = object {
        private val flow = MutableStateFlow<Status>(Status.Checking)
        var value: Status
            get() = flow.value
            set(v) { if (v != flow.value) android.util.Log.i(Adb.TAG, "status ${flow.value} → $v"); flow.value = v }
        fun asStateFlow() = flow.asStateFlow()
    }
    val status: StateFlow<Status> = _status.asStateFlow()

    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    private var paired: Boolean
        get() = prefs.getBoolean("paired", false)
        set(value) = prefs.edit().putBoolean("paired", value).apply()

    private val lock = Mutex()
    private var pairingJob: Job? = null

    init {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        listOf(WIRELESS_DEBUGGING, Settings.Global.ADB_ENABLED, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
            .forEach { context.contentResolver.registerContentObserver(Settings.Global.getUriFor(it), false, observer) }
        context.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
            })
        refresh()
    }

    /** Re-checks the settings and connects if everything is in place. */
    fun refresh() {
        scope.launch {
            lock.withLock {
                val current = _status.value
                if (current is Status.Pairing && blocker() == null) return@withLock
                val blocker = blocker()
                if (blocker != null) {
                    if (current.isConnected) adb.disconnect()
                    _status.value = blocker
                    return@withLock
                }
                if (current.isConnected && adb.shell("echo ok").ok) return@withLock
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    _status.value = Status.Connecting
                    _status.value = if (adb.connect(LEGACY_PORT) == Adb.Connect.Ok) Status.Connected else Status.NeedsComputer
                    return@withLock
                }
                if (!paired) {
                    _status.value = Status.NeedsPairing
                    return@withLock
                }
                _status.value = Status.Connecting
                _status.value = connectWireless()
            }
        }
    }

    /** Starts looking for the pairing service; the user enters the code in the notification or the app. */
    fun startPairing() {
        _status.value = Status.Pairing(port = null)
        pairingJob?.cancel()
        pairingJob = scope.launch {
            Discovery.ports(context, Discovery.PAIRING).collect { port ->
                val now = _status.value
                if (now is Status.Pairing) _status.value = now.copy(port = port, error = null)
            }
        }
        context.startForegroundService(Intent(context, PairingService::class.java))
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        context.stopService(Intent(context, PairingService::class.java))
        if (_status.value is Status.Pairing) _status.value = Status.NeedsPairing
        refresh()
    }

    /** Pairs with the code from Settings. Returns true when paired and connected. */
    suspend fun submitCode(code: String): Boolean {
        val pairing = _status.value as? Status.Pairing ?: return false
        val digits = code.filter { it.isDigit() }
        if (digits.length != 6) {
            _status.value = pairing.copy(error = "The code has 6 digits")
            return false
        }
        val port = pairing.port ?: run {
            _status.value = pairing.copy(error = "Open “Pair device with pairing code” first")
            return false
        }
        _status.value = pairing.copy(busy = true, error = null)
        adb.restartServer()
        val result = adb.pair(port, digits)
        if (!result.ok || !result.text.contains("Successfully paired")) {
            _status.value = pairing.copy(error = "That code didn't work. Check it and try again.", busy = false)
            return false
        }
        paired = true
        pairingJob?.cancel()
        _status.value = Status.Connecting
        val connected = lock.withLock { connectWireless().also { _status.value = it } }
        context.stopService(Intent(context, PairingService::class.java))
        return connected.isConnected
    }

    /** Disconnects and deletes the pairing key, so the phone has to be paired again. */
    suspend fun forget() {
        adb.disconnect()
        adb.restartServer()
        File(context.filesDir, ".android").deleteRecursively()
        paired = false
        refresh()
    }

    private suspend fun connectWireless(): Status {
        // The connect port changes every time wireless debugging is switched on
        val port = withTimeoutOrNull(8_000) {
            Discovery.ports(context, Discovery.CONNECT).filterNotNull().first()
        }
        android.util.Log.i(Adb.TAG, "connect port from mDNS: $port")
        port ?: return Status.Failed("Couldn't find wireless debugging on this phone. Turn it off and on again.")
        return when (adb.connect(port)) {
            Adb.Connect.Ok -> Status.Connected
            Adb.Connect.Rejected -> {
                // Wireless debugging was reset, or "Revoke authorizations" was used
                paired = false
                Status.NeedsPairing
            }
            Adb.Connect.Failed -> Status.Failed("Couldn't reach wireless debugging. Tap Try again, or switch Wireless debugging off and on.")
        }
    }

    private fun blocker(): Status? {
        val resolver = context.contentResolver
        if (Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 1)
            return Status.NeedsDeveloperOptions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return if (Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1) null else Status.NeedsComputer
        if (!onWifi()) return Status.NeedsWifi
        if (Settings.Global.getInt(resolver, WIRELESS_DEBUGGING, 0) != 1) return Status.NeedsWirelessDebugging
        return null
    }

    private fun onWifi(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        // A VPN on top of Wi-Fi still counts; wireless debugging itself switches off without Wi-Fi
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    companion object {
        private const val WIRELESS_DEBUGGING = "adb_wifi_enabled"
        private const val LEGACY_PORT = 5555

        /** Opens Developer options, scrolled to Wireless debugging where the system supports it. */
        fun wirelessDebuggingIntent(): Intent =
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun aboutPhoneIntent(): Intent =
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
