package com.spring.musk.antibloater.adb

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** What a command printed and how it exited. */
data class Output(val exitCode: Int, val text: String) {
    val ok get() = exitCode == 0
}

/**
 * Runs the bundled adb client (libadb.so, from LADB) against this phone's own
 * wireless debugging. Every call is a separate process, so each command gets its
 * own exit code and output instead of sharing one long-lived shell.
 */
class Adb(context: Context) {
    private val binary = "${context.applicationInfo.nativeLibraryDir}/libadb.so"
    private val home = context.filesDir.path
    private val tmp = context.cacheDir.path

    /** The device we're connected to, e.g. "127.0.0.1:37145". */
    @Volatile var serial: String? = null

    suspend fun pair(port: Int, code: String) = run(listOf("pair", "127.0.0.1:$port", code), timeoutSec = 15)

    /** Result of [connect]: connected, rejected (needs pairing again), or something else went wrong. */
    enum class Connect { Ok, Rejected, Failed }

    suspend fun connect(port: Int): Connect {
        var result = tryConnect(port)
        if (result == Connect.Failed) {
            // A server left over from an earlier run can hang every call; start a fresh one and retry once
            restartServer()
            result = tryConnect(port)
        }
        return result
    }

    private suspend fun tryConnect(port: Int): Connect {
        if (!run(listOf("start-server"), timeoutSec = 20).ok) return Connect.Failed
        val out = run(listOf("connect", "127.0.0.1:$port"), timeoutSec = 30)
        val text = out.text.lowercase()
        if (text.contains("authenticat") || text.contains("unauthorized")) return Connect.Rejected
        if (!text.contains("connected to")) return Connect.Failed
        serial = "127.0.0.1:$port"
        val echo = shell("echo ok", timeoutSec = 10)
        return when {
            echo.text.trim() == "ok" -> Connect.Ok
            echo.text.lowercase().contains("unauthorized") -> Connect.Rejected
            else -> Connect.Failed
        }
    }

    /** Stops our adb server, killing it outright if it doesn't answer. Only ever our own processes. */
    suspend fun restartServer() = withContext(Dispatchers.IO) {
        if (!run(listOf("kill-server"), timeoutSec = 5).ok) {
            File("/proc").listFiles()?.forEach { dir ->
                val pid = dir.name.toIntOrNull() ?: return@forEach
                val cmd = runCatching { File(dir, "cmdline").readText() }.getOrNull() ?: return@forEach
                if (pid != Process.myPid() && cmd.startsWith(binary)) {
                    Log.i(TAG, "killing stuck adb server $pid")
                    Process.killProcess(pid)
                }
            }
        }
        serial = null
    }

    suspend fun disconnect() {
        run(listOf("disconnect"), timeoutSec = 5)
        serial = null
    }

    /** Runs a command in `adb shell`. The exit code is the remote command's. */
    suspend fun shell(command: String, timeoutSec: Long = 30): Output {
        val target = serial ?: return Output(-1, "Not connected")
        return run(listOf("-s", target, "shell", command), timeoutSec)
    }


    private suspend fun run(args: List<String>, timeoutSec: Long): Output = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(listOf(binary) + args)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = home
                    environment()["TMPDIR"] = tmp
                    // Our own server port, so we never share a server (and keys) with LADB or Shizuku
                    environment()["ANDROID_ADB_SERVER_PORT"] = SERVER_PORT
                }
                .start()
        } catch (e: Exception) {
            return@withContext Output(-1, e.message ?: "Couldn't start adb")
        }
        coroutineScope {
            val text = async(Dispatchers.IO) {
                runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("")
            }
            val exited = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!exited) process.destroyForcibly()
            // A server the client forked may hold the pipe open; don't wait on it forever
            val output = withTimeoutOrNull(2_000) { text.await() } ?: run {
                runCatching { process.inputStream.close() }
                text.await()
            }
            (if (exited) Output(process.exitValue(), output.trim()) else Output(-1, "Timed out after ${timeoutSec}s"))
                .also { Log.i(TAG, "adb ${args.joinToString(" ").replace(Regex("pair (\\S+) \\d{6}"), "pair $1 ******")} → ${it.exitCode}: ${it.text.take(200)}") }
        }
    }

    companion object {
        private const val SERVER_PORT = "15037"
        const val TAG = "AntiBloater"
    }
}
