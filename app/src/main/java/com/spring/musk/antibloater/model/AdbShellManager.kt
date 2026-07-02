package com.spring.musk.antibloater.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintStream

/**
 * Manages the ADB shell process with thread-safe command execution.
 * Singleton — all features share the same shell connection.
 */
class AdbShellManager private constructor(private val context: Context) {

    companion object {
        const val DEFAULT_BUFFER_SIZE = 1024 * 16
        const val OUTPUT_BUFFER_DELAY_MS = 100L

        @Volatile
        private var instance: AdbShellManager? = null

        fun getInstance(context: Context): AdbShellManager {
            return instance ?: synchronized(this) {
                instance ?: AdbShellManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val adbPath = "${context.applicationInfo.nativeLibraryDir}/libadb.so"

    /** Where shell output is redirected */
    val outputBufferFile: File = File.createTempFile("buffer", ".txt").also {
        it.deleteOnExit()
    }

    /** The active shell process */
    private var shellProcess: Process? = null
    private val shellProcessLock = Any()

    /** Mutex for serializing command writes — prevents interleaved/garbled commands */
    private val writeMutex = Mutex()

    /**
     * Send a command to the shell process with mutex synchronization.
     * Safe to call from multiple coroutines concurrently.
     */
    suspend fun sendCommand(command: String) {
        writeMutex.withLock {
            val process = synchronized(shellProcessLock) { shellProcess } ?: return
            val outputStream = process.outputStream ?: return
            withContext(Dispatchers.IO) {
                PrintStream(outputStream).apply {
                    println(command)
                    flush()
                }
            }
        }
    }

    /**
     * Write a debug/status message directly to the output buffer file.
     * Thread-safe via synchronized block on the file.
     */
    fun debug(msg: String) {
        synchronized(outputBufferFile) {
            if (outputBufferFile.exists()) {
                outputBufferFile.appendText("* $msg${System.lineSeparator()}")
            }
        }
    }

    /**
     * Start an ADB shell process with output redirected to the buffer file.
     */
    fun startAdbShell(args: List<String>): Process {
        val commandList = args.toMutableList().also {
            it.add(0, adbPath)
        }
        val process = createProcess(commandList, redirect = true)
        synchronized(shellProcessLock) {
            shellProcess = process
        }
        return process
    }

    /**
     * Start a local (non-ADB) shell process with output redirected.
     */
    fun startLocalShell(args: List<String>): Process {
        val process = createProcess(args, redirect = true)
        synchronized(shellProcessLock) {
            shellProcess = process
        }
        return process
    }

    /**
     * Run an ADB command (non-interactive, no output redirect).
     * Used for: start-server, wait-for-device, kill-server, pair.
     */
    fun runAdbCommand(args: List<String>): Process {
        val commandList = args.toMutableList().also {
            it.add(0, adbPath)
        }
        return createProcess(commandList, redirect = false)
    }

    /**
     * Wait for the shell process to terminate.
     * Returns when the process exits.
     */
    fun waitForShellDeath() {
        val process = synchronized(shellProcessLock) { shellProcess }
        process?.waitFor()
    }

    /**
     * Check if shell process is alive.
     */
    fun isShellAlive(): Boolean {
        val process = synchronized(shellProcessLock) { shellProcess }
        return process?.isAlive == true
    }

    /**
     * Destroy the current shell process.
     */
    fun destroyShell() {
        synchronized(shellProcessLock) {
            shellProcess?.destroyForcibly()
            shellProcess = null
        }
    }

    private fun createProcess(command: List<String>, redirect: Boolean): Process {
        val processBuilder = ProcessBuilder(command)
            .directory(context.filesDir)
            .apply {
                if (redirect) {
                    redirectErrorStream(true)
                    redirectOutput(outputBufferFile)
                }
                environment().apply {
                    put("HOME", context.filesDir.path)
                    put("TMPDIR", context.cacheDir.path)
                }
            }
        return processBuilder.start()
    }
}
