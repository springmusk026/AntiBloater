package com.spring.musk.antibloater.ui.tracker

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrackerResult(
    val appName: String,
    val packageName: String,
    val trackers: List<String>,
    val score: Int // 0-100 privacy score (100 = clean)
)

class TrackerDetectorViewModel(application: Application) : AndroidViewModel(application) {

    private val pm = application.packageManager

    private val _results = MutableStateFlow<List<TrackerResult>>(emptyList())
    val results: StateFlow<List<TrackerResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _averageScore = MutableStateFlow(100)
    val averageScore: StateFlow<Int> = _averageScore.asStateFlow()

    /** Known tracker/ad SDK package signatures */
    private val trackerSignatures = mapOf(
        "com.google.android.gms.ads" to "Google Ads",
        "com.google.firebase.analytics" to "Firebase Analytics",
        "com.facebook.ads" to "Facebook Ads",
        "com.facebook.appevents" to "Facebook Analytics",
        "com.appsflyer" to "AppsFlyer",
        "com.adjust.sdk" to "Adjust",
        "io.branch" to "Branch.io",
        "com.mixpanel" to "Mixpanel",
        "com.amplitude" to "Amplitude",
        "com.segment" to "Segment",
        "com.crashlytics" to "Crashlytics",
        "com.flurry" to "Flurry",
        "com.mopub" to "MoPub Ads",
        "com.unity3d.ads" to "Unity Ads",
        "com.chartboost" to "Chartboost",
        "com.inmobi" to "InMobi Ads",
        "com.applovin" to "AppLovin",
        "com.ironsource" to "IronSource",
        "com.bytedance.sdk" to "ByteDance/TikTok SDK",
        "com.tencent.bugly" to "Tencent Bugly",
        "com.umeng" to "Umeng Analytics",
        "com.xiaomi.analytics" to "Xiaomi Analytics"
    )

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            @Suppress("DEPRECATION")
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }

            val results = packages
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // User apps only
                .mapNotNull { appInfo ->
                    val meta = appInfo.metaData
                    val foundTrackers = mutableListOf<String>()

                    // Check meta-data keys for tracker signatures
                    if (meta != null) {
                        for (key in meta.keySet()) {
                            for ((sig, name) in trackerSignatures) {
                                if (key.contains(sig, ignoreCase = true)) {
                                    if (name !in foundTrackers) foundTrackers.add(name)
                                }
                            }
                        }
                    }

                    // Check source dir path for known SDKs (heuristic)
                    val sourceDir = appInfo.sourceDir ?: ""
                    val pkgName = appInfo.packageName
                    for ((sig, name) in trackerSignatures) {
                        if (pkgName.contains(sig.substringBefore('.'), ignoreCase = true)) {
                            // Skip — this IS the tracker app itself
                        }
                    }

                    val score = when {
                        foundTrackers.isEmpty() -> 100
                        foundTrackers.size <= 2 -> 70
                        foundTrackers.size <= 4 -> 40
                        else -> 20
                    }

                    if (foundTrackers.isNotEmpty()) {
                        TrackerResult(
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            packageName = appInfo.packageName,
                            trackers = foundTrackers,
                            score = score
                        )
                    } else null
                }
                .sortedBy { it.score }

            _results.value = results
            _averageScore.value = if (results.isEmpty()) 100
                else results.map { it.score }.average().toInt()
            _isLoading.value = false
        }
    }
}
