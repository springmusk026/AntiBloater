package com.spring.musk.antibloater.adb

import android.content.Context
import android.util.Log
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

    suspend fun connect(port: Int): Boolean {
        val out = run(listOf("connect", "127.0.0.1:$port"), timeoutSec = 10)
        val ok = out.text.contains("connected to")
        if (ok) serial = "127.0.0.1:$port"
        return ok && shell("echo ok").text.trim() == "ok"
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

    suspend fun killServer() = run(listOf("kill-server"), timeoutSec = 5)

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
