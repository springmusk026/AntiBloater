package com.spring.musk.antibloater.ui.bootapps

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BootApp(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean
)

class BootAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val pm = application.packageManager

    private val _bootApps = MutableStateFlow<List<BootApp>>(emptyList())
    val bootApps: StateFlow<List<BootApp>> = _bootApps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
            @Suppress("DEPRECATION")
            val receivers: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryBroadcastReceivers(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                pm.queryBroadcastReceivers(intent, PackageManager.GET_META_DATA)
            }

            val seen = mutableSetOf<String>()
            val apps = receivers.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (!seen.add(pkg)) return@mapNotNull null

                val appInfo = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (_: Exception) {
                    return@mapNotNull null
                }

                BootApp(
                    packageName = pkg,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }.sortedBy { it.appName.lowercase() }

            _bootApps.value = apps
            _isLoading.value = false
        }
    }
}
