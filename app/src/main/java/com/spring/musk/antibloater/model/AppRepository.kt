package com.spring.musk.antibloater.model

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * App category for dashboard display and filtering.
 */
enum class AppCategory {
    BLOATWARE,  // System app identified as likely bloatware (shown first)
    SYSTEM,     // System app (not bloat)
    USER        // Installed by the user
}

/**
 * Safety rating for uninstall risk.
 */
enum class SafetyRating {
    SAFE,       // Safe to remove, no impact on device
    CAUTION,    // May affect some functionality
    DANGEROUS   // Core system component, DO NOT remove
}

/**
 * Data class representing an installed app for the UI layer.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val safety: SafetyRating,
    val sizeBytes: Long,
    val icon: Drawable?,
    val applicationInfo: ApplicationInfo
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfo) return false
        return packageName == other.packageName &&
                appName == other.appName &&
                category == other.category &&
                safety == other.safety &&
                sizeBytes == other.sizeBytes
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + appName.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + safety.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        return result
    }

    /** Formatted size string */
    val sizeFormatted: String get() {
        return when {
            sizeBytes < 1024 -> "${sizeBytes} B"
            sizeBytes < 1024 * 1024 -> "${"%.1f".format(sizeBytes / 1024.0)} KB"
            sizeBytes < 1024 * 1024 * 1024 -> "${"%.1f".format(sizeBytes / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(sizeBytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}

/**
 * Dashboard statistics.
 */
data class AppStats(
    val totalApps: Int,
    val systemApps: Int,
    val userApps: Int,
    val bloatwareApps: Int,
    val bloatwareSizeBytes: Long
) {
    val bloatwareSizeFormatted: String get() {
        return when {
            bloatwareSizeBytes < 1024 * 1024 -> "${"%.1f".format(bloatwareSizeBytes / 1024.0)} KB"
            bloatwareSizeBytes < 1024L * 1024 * 1024 -> "${"%.0f".format(bloatwareSizeBytes / (1024.0 * 1024.0))} MB"
            else -> "${"%.1f".format(bloatwareSizeBytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}

/**
 * Repository for installed apps with caching and categorization.
 *
 * Performance optimizations:
 *  - Icons loaded once and cached in AppInfo objects
 *  - App list cached after first load
 *  - Filtering uses pre-built lowercase index
 */
class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val selfPackageName: String = context.packageName

    /** Cached app list — only rebuilt on explicit reload */
    private var cachedApps: List<AppInfo>? = null
    private var cachedStats: AppStats? = null

    // ─── Known Bloatware Database ─────────────────────────────────────────

    private val bloatwarePrefixes = setOf(
        "com.facebook.", "com.samsung.android.game.",
        "com.samsung.android.app.spage", "com.samsung.android.bixby.",
        "com.samsung.android.visionintelligence", "com.samsung.android.arzone",
        "com.samsung.android.aremoji", "com.samsung.android.forest",
        "com.microsoft.skydrive", "com.microsoft.appmanager", "com.microsoft.office.",
        "com.google.android.apps.magazines", "com.google.android.apps.tachyon",
        "com.google.android.videos", "com.google.android.music",
        "com.google.android.apps.youtube.music", "com.google.android.googlequicksearchbox",
        "com.qualcomm.qti.qms.service", "com.miui.analytics", "com.miui.msa.global",
        "com.xiaomi.glgm", "com.xiaomi.midrop", "com.oneplus.membership",
        "com.heytap.", "com.coloros.", "com.oppo.market",
        "com.tencent.", "com.baidu.",
        "com.netflix.partner.", "com.spotify.music", "com.amazon.appmanager",
        "flipboard.app", "com.linkedin.android"
    )

    private val bloatwareExact = setOf(
        "com.facebook.katana", "com.facebook.orca", "com.facebook.services",
        "com.facebook.system", "com.facebook.appmanager", "com.instagram.android",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.king.candycrushsaga", "com.booking.com", "com.tripadvisor.tripadvisor"
    )

    /** Critical system packages — removing these can brick the device */
    private val dangerousPrefixes = setOf(
        "com.android.systemui", "com.android.settings", "com.android.phone",
        "com.android.server.", "com.android.providers.", "com.android.launcher",
        "com.android.inputmethod", "com.android.keyguard", "com.android.packageinstaller",
        "com.android.bluetooth", "com.android.nfc", "com.android.wifi",
        "com.android.emergency", "com.android.dialer", "com.android.contacts",
        "com.android.messaging", "com.android.camera", "com.android.documentsui",
        "com.android.vending", "android", "com.google.android.gms",
        "com.google.android.gsf", "com.google.android.setupwizard",
        "com.google.android.packageinstaller", "com.google.android.permissioncontroller"
    )

    // ─── Public API ───────────────────────────────────────────────────────

    /**
     * Get all installed apps with cached icons. Uses cache if available.
     */
    fun getInstalledApps(forceReload: Boolean = false): List<AppInfo> {
        if (!forceReload && cachedApps != null) return cachedApps!!

        val installedApps = getInstalledApplications()

        val apps = installedApps
            .filter { it.packageName != selfPackageName }
            .map { appInfo ->
                val icon = try {
                    packageManager.getApplicationIcon(appInfo)
                } catch (_: Exception) {
                    null
                }
                val category = categorizeApp(appInfo)
                val size = try {
                    java.io.File(appInfo.sourceDir).length()
                } catch (_: Exception) {
                    0L
                }
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    category = category,
                    safety = classifySafety(appInfo, category),
                    sizeBytes = size,
                    icon = icon,
                    applicationInfo = appInfo
                )
            }
            .sortedWith(compareBy({ it.category.ordinal }, { it.appName.lowercase() }))

        cachedApps = apps
        cachedStats = AppStats(
            totalApps = apps.size,
            systemApps = apps.count { it.category == AppCategory.SYSTEM },
            userApps = apps.count { it.category == AppCategory.USER },
            bloatwareApps = apps.count { it.category == AppCategory.BLOATWARE },
            bloatwareSizeBytes = apps.filter { it.category == AppCategory.BLOATWARE }.sumOf { it.sizeBytes }
        )
        return apps
    }

    /**
     * Get cached stats (call getInstalledApps first).
     */
    fun getStats(): AppStats = cachedStats ?: AppStats(0, 0, 0, 0, 0L)

    /**
     * Filter apps by search query. Runs on pre-loaded list, no IPC calls.
     */
    fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) return apps
        val lowerQuery = query.lowercase()
        return apps.filter {
            it.appName.lowercase().contains(lowerQuery) ||
                    it.packageName.contains(lowerQuery)
        }
    }

    /**
     * Filter apps by category.
     */
    fun filterByCategory(apps: List<AppInfo>, category: AppCategory?): List<AppInfo> {
        if (category == null) return apps
        return apps.filter { it.category == category }
    }

    /**
     * Invalidate cache (after uninstall, etc.).
     */
    fun invalidateCache() {
        cachedApps = null
        cachedStats = null
    }

    /**
     * Generate the ADB uninstall command for a package.
     */
    fun getUninstallCommand(packageName: String): String {
        return "pm uninstall -k --user 0 $packageName"
    }

    // ─── Private ──────────────────────────────────────────────────────────

    private fun categorizeApp(appInfo: ApplicationInfo): AppCategory {
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (!isSystemApp) return AppCategory.USER
        if (appInfo.packageName in bloatwareExact) return AppCategory.BLOATWARE
        if (bloatwarePrefixes.any { appInfo.packageName.startsWith(it) }) return AppCategory.BLOATWARE
        return AppCategory.SYSTEM
    }

    private fun classifySafety(appInfo: ApplicationInfo, category: AppCategory): SafetyRating {
        // User-installed apps are always safe to remove
        if (category == AppCategory.USER) return SafetyRating.SAFE

        // Known bloatware is safe
        if (category == AppCategory.BLOATWARE) return SafetyRating.SAFE

        // Check if it's a critical system component
        if (dangerousPrefixes.any { appInfo.packageName.startsWith(it) || appInfo.packageName == it }) {
            return SafetyRating.DANGEROUS
        }

        // Other system apps: caution
        return SafetyRating.CAUTION
    }

    @Suppress("DEPRECATION")
    private fun getInstalledApplications(): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }
    }
}
