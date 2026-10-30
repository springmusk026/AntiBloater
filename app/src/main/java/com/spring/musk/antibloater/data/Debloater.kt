package com.spring.musk.antibloater.data

import com.spring.musk.antibloater.adb.Connection

/** What happened to one package. [message] is adb's own words when something failed. */
data class Outcome(val pkg: Pkg, val ok: Boolean, val message: String = "")

/**
 * Removes, disables and restores packages through adb, then checks with the package
 * manager that it really happened. Only verified changes go into the history.
 *
 * Removing uses `pm uninstall -k --user 0`: the APK stays on the system partition
 * and the data is kept, so `cmd package install-existing` brings it back as it was.
 */
class Debloater(
    private val connection: Connection,
    private val packages: Packages,
    private val history: History,
    private val prefs: Prefs,
) {
    /** Why [pkg] can't be changed right now, or null if it can. */
    fun blockedReason(pkg: Pkg, protected: Set<String>): String? = when {
        pkg.name in protected -> "Needed to keep the phone (or this connection) working"
        !Packages.isValidName(pkg.name) -> "Unusual package name"
        pkg.removal == Removal.Unsafe && !prefs.allowUnsafe.value -> "Unsafe: can stop the phone from booting"
        else -> null
    }

    suspend fun apply(
        targets: List<Pkg>,
        action: Action,
        onProgress: (done: Int, current: Pkg) -> Unit = { _, _ -> },
    ): List<Outcome> {
        val protected = packages.protected()
        val batch = System.currentTimeMillis()
        val outcomes = targets.mapIndexed { i, pkg ->
            onProgress(i, pkg)
            val blocked = if (action == Action.Restore) null else blockedReason(pkg, protected)
            if (blocked != null) Outcome(pkg, false, blocked) else run(pkg, action)
        }
        onProgress(targets.size, targets.lastOrNull() ?: return outcomes)
        history.add(outcomes.filter { it.ok }.map {
            Event(it.pkg.name, it.pkg.label, action, System.currentTimeMillis(), batch)
        })
        packages.reload()
        return outcomes
    }

    private suspend fun run(pkg: Pkg, action: Action): Outcome {
        val adb = connection.adb
        val name = pkg.name
        val output = when (action) {
            Action.Uninstall -> adb.shell("pm uninstall -k --user 0 $name")
            Action.Disable -> adb.shell("pm disable-user --user 0 $name")
            Action.Restore -> {
                var out = adb.shell("echo")
                if (packages.stateOf(name) == PkgState.Removed) out = adb.shell("cmd package install-existing --user 0 $name")
                if (out.ok && packages.stateOf(name) == PkgState.Disabled) out = adb.shell("pm enable --user 0 $name")
                out
            }
        }
        val expected = when (action) {
            Action.Uninstall -> PkgState.Removed
            Action.Disable -> PkgState.Disabled
            Action.Restore -> PkgState.Installed
        }
        val now = packages.stateOf(name)
        return when {
            now == expected -> Outcome(pkg, true)
            output.ok -> Outcome(pkg, false, "The command ran, but the app is still ${now?.name?.lowercase() ?: "missing"}")
            else -> Outcome(pkg, false, output.text.lineSequence().lastOrNull { it.isNotBlank() } ?: "Failed")
        }
    }
}
