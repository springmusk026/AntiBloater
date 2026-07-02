package com.spring.musk.antibloater.ui.permissions

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PermissionGroup(
    val permissionName: String,
    val friendlyName: String,
    val apps: List<String>, // app names
    val count: Int
)

data class AppPermissions(
    val appName: String,
    val packageName: String,
    val dangerousPermissions: List<String>
)

class PermissionAuditViewModel(application: Application) : AndroidViewModel(application) {

    private val pm = application.packageManager

    private val _permissionGroups = MutableStateFlow<List<PermissionGroup>>(emptyList())
    val permissionGroups: StateFlow<List<PermissionGroup>> = _permissionGroups.asStateFlow()

    private val _totalDangerous = MutableStateFlow(0)
    val totalDangerous: StateFlow<Int> = _totalDangerous.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val dangerousPermissions = mapOf(
        Manifest.permission.CAMERA to "Camera",
        Manifest.permission.RECORD_AUDIO to "Microphone",
        Manifest.permission.ACCESS_FINE_LOCATION to "Precise Location",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Approximate Location",
        Manifest.permission.READ_CONTACTS to "Contacts",
        Manifest.permission.READ_CALL_LOG to "Call Log",
        Manifest.permission.READ_SMS to "SMS",
        Manifest.permission.SEND_SMS to "Send SMS",
        Manifest.permission.READ_PHONE_STATE to "Phone State",
        Manifest.permission.CALL_PHONE to "Make Calls",
        Manifest.permission.READ_EXTERNAL_STORAGE to "Storage (Read)",
        Manifest.permission.WRITE_EXTERNAL_STORAGE to "Storage (Write)",
        Manifest.permission.BODY_SENSORS to "Body Sensors",
        Manifest.permission.ACCESS_BACKGROUND_LOCATION to "Background Location"
    )

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            val permMap = mutableMapOf<String, MutableList<String>>() // permission -> app names

            @Suppress("DEPRECATION")
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }

            for (pkg in packages) {
                val perms = pkg.requestedPermissions ?: continue
                val appName = pm.getApplicationLabel(pkg.applicationInfo).toString()

                for (perm in perms) {
                    if (perm in dangerousPermissions) {
                        permMap.getOrPut(perm) { mutableListOf() }.add(appName)
                    }
                }
            }

            val groups = permMap.map { (perm, apps) ->
                PermissionGroup(
                    permissionName = perm,
                    friendlyName = dangerousPermissions[perm] ?: perm.substringAfterLast('.'),
                    apps = apps.sorted(),
                    count = apps.size
                )
            }.sortedByDescending { it.count }

            _permissionGroups.value = groups
            _totalDangerous.value = groups.sumOf { it.count }
            _isLoading.value = false
        }
    }
}
