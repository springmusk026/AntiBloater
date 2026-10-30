package com.spring.musk.antibloater.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

enum class TrackerKind(val label: String) {
    Ads("Ads"), Analytics("Analytics"), Attribution("Attribution"), Crash("Crash reporting"), Engagement("Marketing")
}

data class Tracker(val name: String, val kind: TrackerKind, val prefixes: List<String>)

data class AppTrackers(val pkg: String, val label: String, val trackers: List<Tracker>)

/**
 * Finds tracking SDKs inside apps by the classes they ship, like Exodus Privacy does.
 * Every dex file starts with a sorted table of all its strings, class names included,
 * so each signature is a binary search instead of a scan through the whole file.
 */
object Trackers {
    val known = listOf(
        Tracker("Google AdMob", TrackerKind.Ads, listOf("Lcom/google/android/gms/ads/")),
        Tracker("Google Firebase Analytics", TrackerKind.Analytics, listOf("Lcom/google/firebase/analytics/")),
        Tracker("Google Analytics", TrackerKind.Analytics, listOf("Lcom/google/android/gms/analytics/")),
        Tracker("Google Crashlytics", TrackerKind.Crash, listOf("Lcom/google/firebase/crashlytics/", "Lcom/crashlytics/")),
        Tracker("Facebook Analytics", TrackerKind.Analytics, listOf("Lcom/facebook/appevents/")),
        Tracker("Facebook Ads", TrackerKind.Ads, listOf("Lcom/facebook/ads/")),
        Tracker("Facebook Login", TrackerKind.Analytics, listOf("Lcom/facebook/login/")),
        Tracker("AppsFlyer", TrackerKind.Attribution, listOf("Lcom/appsflyer/")),
        Tracker("Adjust", TrackerKind.Attribution, listOf("Lcom/adjust/sdk/")),
        Tracker("Branch", TrackerKind.Attribution, listOf("Lio/branch/")),
        Tracker("Kochava", TrackerKind.Attribution, listOf("Lcom/kochava/")),
        Tracker("Singular", TrackerKind.Attribution, listOf("Lcom/singular/sdk/")),
        Tracker("Mixpanel", TrackerKind.Analytics, listOf("Lcom/mixpanel/")),
        Tracker("Amplitude", TrackerKind.Analytics, listOf("Lcom/amplitude/")),
        Tracker("Segment", TrackerKind.Analytics, listOf("Lcom/segment/analytics/")),
        Tracker("Flurry", TrackerKind.Analytics, listOf("Lcom/flurry/")),
        Tracker("Heap", TrackerKind.Analytics, listOf("Lcom/heapanalytics/")),
        Tracker("Yandex AppMetrica", TrackerKind.Analytics, listOf("Lcom/yandex/metrica/", "Lio/appmetrica/")),
        Tracker("Huawei Analytics", TrackerKind.Analytics, listOf("Lcom/huawei/hms/analytics/")),
        Tracker("Umeng", TrackerKind.Analytics, listOf("Lcom/umeng/")),
        Tracker("comScore", TrackerKind.Analytics, listOf("Lcom/comscore/")),
        Tracker("Nielsen", TrackerKind.Analytics, listOf("Lcom/nielsen/")),
        Tracker("Microsoft App Center", TrackerKind.Analytics, listOf("Lcom/microsoft/appcenter/")),
        Tracker("Smartlook", TrackerKind.Analytics, listOf("Lcom/smartlook/")),
        Tracker("Unity Ads", TrackerKind.Ads, listOf("Lcom/unity3d/ads/", "Lcom/unity3d/services/")),
        Tracker("AppLovin", TrackerKind.Ads, listOf("Lcom/applovin/")),
        Tracker("ironSource", TrackerKind.Ads, listOf("Lcom/ironsource/")),
        Tracker("Chartboost", TrackerKind.Ads, listOf("Lcom/chartboost/")),
        Tracker("InMobi", TrackerKind.Ads, listOf("Lcom/inmobi/")),
        Tracker("Vungle / Liftoff", TrackerKind.Ads, listOf("Lcom/vungle/")),
        Tracker("AdColony", TrackerKind.Ads, listOf("Lcom/adcolony/")),
        Tracker("Pangle (ByteDance)", TrackerKind.Ads, listOf("Lcom/bytedance/sdk/openadsdk/")),
        Tracker("Mintegral", TrackerKind.Ads, listOf("Lcom/mbridge/msdk/")),
        Tracker("MoPub", TrackerKind.Ads, listOf("Lcom/mopub/")),
        Tracker("Tapjoy", TrackerKind.Ads, listOf("Lcom/tapjoy/")),
        Tracker("Digital Turbine (Fyber)", TrackerKind.Ads, listOf("Lcom/fyber/")),
        Tracker("Start.io", TrackerKind.Ads, listOf("Lcom/startapp/")),
        Tracker("Criteo", TrackerKind.Ads, listOf("Lcom/criteo/")),
        Tracker("Taboola", TrackerKind.Ads, listOf("Lcom/taboola/")),
        Tracker("Outbrain", TrackerKind.Ads, listOf("Lcom/outbrain/")),
        Tracker("OneSignal", TrackerKind.Engagement, listOf("Lcom/onesignal/")),
        Tracker("Braze", TrackerKind.Engagement, listOf("Lcom/braze/", "Lcom/appboy/")),
        Tracker("CleverTap", TrackerKind.Engagement, listOf("Lcom/clevertap/")),
        Tracker("MoEngage", TrackerKind.Engagement, listOf("Lcom/moengage/")),
        Tracker("Sentry", TrackerKind.Crash, listOf("Lio/sentry/")),
        Tracker("Bugsnag", TrackerKind.Crash, listOf("Lcom/bugsnag/")),
        Tracker("Instabug", TrackerKind.Crash, listOf("Lcom/instabug/")),
        Tracker("Tencent Bugly", TrackerKind.Crash, listOf("Lcom/tencent/bugly/")),
        Tracker("New Relic", TrackerKind.Crash, listOf("Lcom/newrelic/agent/")),
        Tracker("Datadog", TrackerKind.Crash, listOf("Lcom/datadog/android/")),
    )

    /** Scans the apps you installed. Calls [progress] with (done, total) as it goes. */
    fun scan(pm: PackageManager, progress: (Int, Int) -> Unit): List<AppTrackers> {
        @Suppress("DEPRECATION")
        val apps = pm.getInstalledApplications(0).filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
        return apps.mapIndexedNotNull { i, app ->
            progress(i, apps.size)
            val found = scan(app)
            if (found.isEmpty()) null
            else AppTrackers(app.packageName, pm.getApplicationLabel(app).toString(), found)
        }.also { progress(apps.size, apps.size) }
            .sortedWith(compareByDescending<AppTrackers> { it.trackers.size }.thenBy { it.label.lowercase() })
    }

    fun scan(app: ApplicationInfo): List<Tracker> {
        val found = mutableSetOf<Tracker>()
        val apks = listOfNotNull(app.sourceDir) + (app.splitSourceDirs?.toList() ?: emptyList())
        for (apk in apks) runCatching {
            ZipFile(File(apk)).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                    .forEach { entry ->
                        val dex = zip.getInputStream(entry).use { it.readBytes() }
                        known.filterNot { it in found }.filterTo(found) { t -> t.prefixes.any { hasPrefix(dex, it) } }
                    }
            }
        }
        return known.filter { it in found }
    }

    /** Binary search of the dex string table for a string starting with [prefix]. */
    internal fun hasPrefix(dex: ByteArray, prefix: String): Boolean {
        if (dex.size < 0x70) return false
        val buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
        val count = buf.getInt(0x38)
        val idsOff = buf.getInt(0x3C)
        if (count <= 0 || idsOff <= 0 || idsOff + count * 4L > dex.size) return false
        val target = prefix.toByteArray(Charsets.US_ASCII)
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = compareAt(dex, stringStart(dex, buf.getInt(idsOff + mid * 4)), target)
            when {
                cmp == 0 -> return true
                cmp < 0 -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return false
    }

    /** Skips the ULEB128 length in front of a string's bytes. */
    private fun stringStart(dex: ByteArray, offset: Int): Int {
        var p = offset
        while (p < dex.size && dex[p].toInt() and 0x80 != 0) p++
        return p + 1
    }

    /** Compares the string at [start] against [prefix]; 0 means it starts with it. */
    private fun compareAt(dex: ByteArray, start: Int, prefix: ByteArray): Int {
        for (i in prefix.indices) {
            val p = start + i
            val b = if (p < dex.size) dex[p].toInt() and 0xFF else 0
            if (b == 0) return -1 // string ended first: it sorts before the prefix
            val diff = b - (prefix[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return 0
    }
}
