package com.spring.musk.antibloater.addon

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class InstalledAppsManager(private val context: Context) {

    fun getAllInstalledApps(): List<ApplicationInfo> {
        val packageManager: PackageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        return installedApps
    }
    fun getAllInstalledAppNames(): List<String> {
        val packageManager: PackageManager = context.packageManager
        val appsList = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val appNames = mutableListOf<String>()
        for (appInfo in appsList) {
            appNames.add(packageManager.getApplicationLabel(appInfo).toString())
        }
        return appNames
    }
}
