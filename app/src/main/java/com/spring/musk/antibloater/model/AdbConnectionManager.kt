package com.spring.musk.antibloater.model

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.spring.musk.antibloater.R
import java.io.PrintStream
import java.util.concurrent.TimeUnit

/**
 * Manages ADB connection lifecycle: pairing, server start, wireless/USB debugging detection.
 * Fixes from original ADB.kt:
 *  - Uses androidx.preference.PreferenceManager (not deprecated android.preference)
 *  - Retry limit on pairing to prevent infinite loops
 *  - Clean separation from shell I/O concerns
 */
@RequiresApi(Build.VERSION_CODES.O)
class AdbConnectionManager private constructor(
    private val context: Context,
    private val shellManager: AdbShellManager
) {
    companion object {
        const val MAX_PAIR_RETRIES = 3
        private const val WAIT_FOR_DEVICE_TIMEOUT_SEC = 20L
        private const val PAIR_TIMEOUT_SEC = 8L

        @Volatile
        private var instance: AdbConnectionManager? = null

        fun getInstance(context: Context): AdbConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: AdbConnectionManager(
                    context.applicationContext,
                    AdbShellManager.getInstance(context)
                ).also { instance = it }
            }
        }
    }

    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

    /** Whether ADB shell has been successfully started */
    @Volatile
    private var isStarted = false

    /** Whether this is a real ADB connection (not just local shell) */
    @Volatile
    private var isAdbConnected = false

    /** Public check for real ADB connection state */
    fun isConnected(): Boolean = isAdbConnected

    /** Prevents concurrent init attempts */
    @Volatile
    private var isInitializing = false

    /**
     * Returns the user-configured output buffer size, or default 16KB.
     */
    fun getOutputBufferSize(): Int {
        val userValue = sharedPrefs.getString(
            context.getString(R.string.buffer_size_key), "16384"
        ) ?: "16384"
        return try {
            userValue.toInt()
        } catch (_: NumberFormatException) {
            AdbShellManager.DEFAULT_BUFFER_SIZE
        }
    }

    /**
     * Whether the device needs to pair (Android 11+ and not previously paired).
     */
    fun needsToPair(): Boolean {
        return !sharedPrefs.getBoolean(context.getString(R.string.paired_key), false) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Mark device as paired in preferences.
     */
    fun setPairedBefore(value: Boolean) {
        sharedPrefs.edit()
            .putBoolean(context.getString(R.string.paired_key), value)
            .apply()
    }

    /**
     * Whether to use auto-shell (ADB wireless) or manual local shell.
     */
    fun isAutoShell(): Boolean {
        return sharedPrefs.getBoolean(context.getString(R.string.auto_shell_key), true)
    }

    /**
     * Disable auto-shell mode (user chose "Skip" in pairing dialog).
     */
    fun disableAutoShell() {
        sharedPrefs.edit()
            .putBoolean(context.getString(R.string.auto_shell_key), false)
            .apply()
    }

    /**
     * Perform ADB pairing. Returns true on success.
     * Also stores the port for later connection attempts (Samsung needs explicit connect).
     */
    fun pair(port: String, pairingCode: String): Boolean {
        val pairShell = shellManager.runAdbCommand(listOf("pair", "localhost:$port"))

        // Brief pause for the pair process to be ready for input
        Thread.sleep(1000)

        // Pipe pairing code
        PrintStream(pairShell.outputStream).apply {
            println(pairingCode)
            flush()
        }

        // Wait for pairing to complete (usually < 3 seconds)
        pairShell.waitFor(PAIR_TIMEOUT_SEC, TimeUnit.SECONDS)
        pairShell.destroyForcibly().waitFor()

        val success = pairShell.exitValue() == 0

        // After successful pairing, try to connect immediately (helps Samsung)
        if (success) {
            // Kill old server
            shellManager.runAdbCommand(listOf("kill-server")).waitFor()
            Thread.sleep(500)

            // Start fresh and connect
            shellManager.runAdbCommand(listOf("start-server")).waitFor()
            // Try connecting on the same port (sometimes works) and default
            shellManager.runAdbCommand(listOf("connect", "localhost:$port"))
                .waitFor(3, TimeUnit.SECONDS)
        } else {
            // Kill server to reset state
            val killShell = shellManager.runAdbCommand(listOf("kill-server"))
            killShell.waitFor(2, TimeUnit.SECONDS)
            killShell.destroyForcibly()
        }

        return success
    }

    /**
     * Initialize the ADB server and start a shell session.
     * Returns true on success, false on failure.
     * Verifies the shell is actually responsive before returning true.
     */
    fun initServer(): Boolean {
        if (isStarted || isInitializing) return isStarted
        isInitializing = true

        try {
            val autoShell = isAutoShell()

            if (autoShell) {
                // Check if wireless debugging / USB debugging is actually enabled
                if (!isDebuggingEnabled()) {
                    shellManager.debug("Debugging is not enabled on this device")
                    return false
                }

                if (!waitForDebugging()) {
                    return false
                }

                shellManager.runAdbCommand(listOf("start-server")).waitFor()
                shellManager.debug("Connecting to device...")

                // Try explicit localhost connection (needed for Samsung)
                // First try without port (uses devices already paired)
                val connectProcess = shellManager.runAdbCommand(listOf("connect", "localhost"))
                connectProcess.waitFor(5, TimeUnit.SECONDS)
                connectProcess.destroyForcibly()

                shellManager.debug("Waiting for device...")
                val waitProcess = shellManager.runAdbCommand(listOf("wait-for-device"))
                val connected = waitProcess.waitFor(WAIT_FOR_DEVICE_TIMEOUT_SEC, TimeUnit.SECONDS)

                if (!connected) {
                    waitProcess.destroyForcibly()
                    shellManager.debug("Your device didn't connect")
                    shellManager.debug("If a reboot doesn't work, please contact support")
                    return false
                }

                // Verify we can actually run a command
                if (!verifyConnection()) {
                    shellManager.debug("Connection verification failed")
                    return false
                }
            }

            // Start the shell process
            if (autoShell) {
                val argList = if (Build.SUPPORTED_ABIS[0] == "arm64-v8a")
                    listOf("-t", "1", "shell")
                else
                    listOf("shell")
                shellManager.startAdbShell(argList)
            } else {
                shellManager.startLocalShell(listOf("sh", "-l"))
            }

            // Setup shell aliases and startup commands
            setupShell(autoShell)

            isStarted = true
            isAdbConnected = autoShell  // Only true for real ADB connection
            return true
        } finally {
            isInitializing = false
        }
    }

    /**
     * Kill the ADB server and reset state for reconnection.
     */
    fun resetServer() {
        isStarted = false
        isAdbConnected = false
        shellManager.runAdbCommand(listOf("kill-server")).waitFor()
        Thread.sleep(1000)
    }

    private fun waitForDebugging(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isWirelessDebuggingEnabled()) {
            shellManager.debug("Wireless debugging is not enabled!")
            shellManager.debug("Settings -> Developer options -> Wireless debugging")
            shellManager.debug("Waiting for wireless debugging...")

            var waitCount = 0
            while (!isWirelessDebuggingEnabled()) {
                Thread.sleep(1000)
                waitCount++
                if (waitCount > 120) { // 2 minute timeout
                    shellManager.debug("Timeout waiting for wireless debugging")
                    return false
                }
            }
        } else if (!isUSBDebuggingEnabled()) {
            shellManager.debug("USB debugging is not enabled!")
            shellManager.debug("Settings -> Developer options -> USB debugging")
            shellManager.debug("Waiting for USB debugging...")

            var waitCount = 0
            while (!isUSBDebuggingEnabled()) {
                Thread.sleep(1000)
                waitCount++
                if (waitCount > 120) {
                    shellManager.debug("Timeout waiting for USB debugging")
                    return false
                }
            }
        }
        return true
    }

    /**
     * Check if any form of debugging is currently enabled.
     */
    fun isDebuggingEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return isWirelessDebuggingEnabled()
        }
        return isUSBDebuggingEnabled()
    }

    /**
     * Verify the ADB connection is actually working by running a test command.
     */
    private fun verifyConnection(): Boolean {
        return try {
            val verifyProcess = shellManager.runAdbCommand(listOf("shell", "echo", "ok"))
            val completed = verifyProcess.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                verifyProcess.destroyForcibly()
                return false
            }
            verifyProcess.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun setupShellSuspend(autoShell: Boolean) {
        val adbPath = "${context.applicationInfo.nativeLibraryDir}/libadb.so"
        shellManager.sendCommand("alias adb=\"$adbPath\"")

        if (autoShell)
            shellManager.sendCommand("echo 'Entered adb shell'")
        else
            shellManager.sendCommand("echo 'Entered non-adb shell'")

        val startupCommand = sharedPrefs.getString(
            context.getString(R.string.startup_command_key),
            "echo 'Success! ※\\(^o^)/※'"
        ) ?: ""
        if (startupCommand.isNotEmpty()) {
            shellManager.sendCommand(startupCommand)
        }
    }

    private fun setupShell(autoShell: Boolean) {
        // Use blocking approach for init sequence since we're already on IO thread
        val adbPath = "${context.applicationInfo.nativeLibraryDir}/libadb.so"
        kotlinx.coroutines.runBlocking {
            shellManager.sendCommand("alias adb=\"$adbPath\"")

            if (autoShell)
                shellManager.sendCommand("echo 'Entered adb shell'")
            else
                shellManager.sendCommand("echo 'Entered non-adb shell'")

            val startupCommand = sharedPrefs.getString(
                context.getString(R.string.startup_command_key),
                "echo 'Success! ※\\(^o^)/※'"
            ) ?: ""
            if (startupCommand.isNotEmpty()) {
                shellManager.sendCommand(startupCommand)
            }
        }
    }

    private fun isWirelessDebuggingEnabled(): Boolean =
        Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1

    private fun isUSBDebuggingEnabled(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
}
