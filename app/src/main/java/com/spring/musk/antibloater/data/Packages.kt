package com.spring.musk.antibloater.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class PkgState { Installed, Disabled, Removed }

/** A package on this phone, with what UAD-ng knows about it. */
data class Pkg(
    val name: String,
    val label: String,
    val system: Boolean,
    val state: PkgState,
    val entry: Entry?,
) {
    val removal get() = entry?.removal ?: Removal.Unlisted
    val source get() = entry?.source ?: if (system) Source.Unknown else Source.Misc
}

/**
 * Every package the phone knows about for this user, including ones removed with
 * `pm uninstall --user 0` (they stay on the system partition and can come back).
 * Reading this needs no adb, so the list works before pairing.
 */
class Packages(private val context: Context, private val knowledge: Knowledge) {
    private val pm = context.packageManager

    private val _all = MutableStateFlow<List<Pkg>?>(null)
    /** Null until the first load finishes. */
    val all: StateFlow<List<Pkg>?> = _all.asStateFlow()

    suspend fun reload() = withContext(Dispatchers.IO) {
        val entries = knowledge.entries.value
        _all.value = installed()
            .filter { it.packageName != context.packageName }
            .map { info ->
                Pkg(
                    name = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                    system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    state = stateOf(info),
                    entry = entries[info.packageName],
                )
            }
            .sortedWith(compareBy({ it.removal.ordinal }, { it.label.lowercase() }))
    }

    /** Asks the system again, so callers can check what a command actually did. */
    fun stateOf(name: String): PkgState? =
        runCatching {
            @Suppress("DEPRECATION")
            stateOf(
                if (Build.VERSION.SDK_INT >= 33) pm.getApplicationInfo(name, PackageManager.ApplicationInfoFlags.of(FLAGS.toLong()))
                else pm.getApplicationInfo(name, FLAGS)
            )
        }.getOrNull()

    /** Packages that must never be touched: the ones keeping the phone usable and adb alive. */
    fun protected(): Set<String> {
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
        val keyboard = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')
        return CORE + listOfNotNull(home, keyboard, context.packageName)
    }

    private fun stateOf(info: ApplicationInfo) = when {
        info.flags and ApplicationInfo.FLAG_INSTALLED == 0 -> PkgState.Removed
        !info.enabled -> PkgState.Disabled
        else -> PkgState.Installed
    }

    @Suppress("DEPRECATION")
    private fun installed(): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= 33) pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(FLAGS.toLong()))
        else pm.getInstalledApplications(FLAGS)

    companion object {
        private const val FLAGS = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS

        private val CORE = setOf(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.android.shell", "com.android.providers.settings", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

        private val NAME = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

        /** Package names go into shell commands; only real ones get through. */
        fun isValidName(name: String) = NAME.matches(name)
    }
}
