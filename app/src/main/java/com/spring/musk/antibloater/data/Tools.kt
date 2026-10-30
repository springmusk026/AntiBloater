package com.spring.musk.antibloater.data

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.spring.musk.antibloater.adb.Adb
import com.spring.musk.antibloater.adb.Output

// ─── Permission audit ─────────────────────────────────────────────────────────

/** A sensitive permission, grouped the way Android's own settings name it. */
enum class Sensitive(val label: String, val permissions: List<String>) {
    Location("Location", listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
    BackgroundLocation("Location all the time", listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
    Camera("Camera", listOf(Manifest.permission.CAMERA)),
    Microphone("Microphone", listOf(Manifest.permission.RECORD_AUDIO)),
    Contacts("Contacts", listOf(Manifest.permission.READ_CONTACTS)),
    CallLog("Call logs", listOf(Manifest.permission.READ_CALL_LOG)),
    Sms("SMS", listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)),
    Phone("Phone", listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE)),
    Calendar("Calendar", listOf(Manifest.permission.READ_CALENDAR)),
    Media("Photos and videos", listOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", Manifest.permission.READ_EXTERNAL_STORAGE)),
    Sensors("Body sensors", listOf(Manifest.permission.BODY_SENSORS)),
}

/** An app that currently holds [permissions] (granted ones only) from a [Sensitive] group. */
data class Grant(val pkg: String, val label: String, val system: Boolean, val group: Sensitive, val permissions: List<String>)

object Permissions {
    fun granted(pm: PackageManager): List<Grant> {
        @Suppress("DEPRECATION")
        val packages = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        return packages.flatMap { info -> grants(pm, info) }
    }

    private fun grants(pm: PackageManager, info: PackageInfo): List<Grant> {
        val requested = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags ?: return emptyList()
        val held = requested.filterIndexed { i, _ -> flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }.toSet()
        val app = info.applicationInfo ?: return emptyList()
        val label = pm.getApplicationLabel(app).toString()
        val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
        return Sensitive.entries.mapNotNull { group ->
            val granted = group.permissions.filter { it in held }
            if (granted.isEmpty()) null else Grant(info.packageName, label, system, group, granted)
        }
    }

    suspend fun revoke(adb: Adb, grant: Grant): Output {
        var last = Output(0, "")
        for (permission in grant.permissions) {
            last = adb.shell("pm revoke ${grant.pkg} $permission")
            if (!last.ok) return last
        }
        return last
    }
}

// ─── Background apps ──────────────────────────────────────────────────────────

/** An app that asks to start when the phone boots. [restricted] is null until adb tells us. */
data class Starter(val pkg: String, val label: String, val system: Boolean, val restricted: Boolean?)

/**
 * Apps that start at boot, and Android's "restrict background activity" switch for them
 * (the RUN_ANY_IN_BACKGROUND app op). Restricted apps don't run in the background unless
 * you open them, which also stops most of them running at boot.
 */
object Background {
    private const val OP = "RUN_ANY_IN_BACKGROUND"

    fun starters(pm: PackageManager, restricted: Set<String>?): List<Starter> {
        @Suppress("DEPRECATION")
        val receivers = pm.queryBroadcastReceivers(Intent(Intent.ACTION_BOOT_COMPLETED), 0)
        return receivers.mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .map { Starter(it.packageName, pm.getApplicationLabel(it).toString(),
                it.flags and ApplicationInfo.FLAG_SYSTEM != 0, restricted?.contains(it.packageName)) }
            .sortedWith(compareBy({ it.system }, { it.label.lowercase() }))
    }

    /** Packages whose background activity is restricted, in one adb call. */
    suspend fun restricted(adb: Adb): Set<String>? {
        val out = adb.shell("cmd appops query-op $OP ignore")
        if (!out.ok) return null
        return out.text.lines().map { it.trim() }.filter { Packages.isValidName(it) }.toSet()
    }

    suspend fun setRestricted(adb: Adb, pkg: String, restricted: Boolean) =
        adb.shell("cmd appops set $pkg $OP ${if (restricted) "ignore" else "allow"}")
}

// ─── Animation speed ──────────────────────────────────────────────────────────

object Animations {
    private val keys = listOf(
        Settings.Global.WINDOW_ANIMATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.ANIMATOR_DURATION_SCALE,
    )

    /** The scale now in use, or null if the three settings differ. */
    fun current(resolver: ContentResolver): Float? =
        keys.map { Settings.Global.getFloat(resolver, it, 1f) }.distinct().singleOrNull()

    suspend fun set(adb: Adb, scale: Float): Output =
        adb.shell(keys.joinToString(" && ") { "settings put global $it $scale" })
}
