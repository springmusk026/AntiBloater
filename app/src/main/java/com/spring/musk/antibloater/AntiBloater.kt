package com.spring.musk.antibloater

import android.app.Application
import android.content.Context
import com.spring.musk.antibloater.adb.Adb
import com.spring.musk.antibloater.adb.Connection
import com.spring.musk.antibloater.data.Debloater
import com.spring.musk.antibloater.data.History
import com.spring.musk.antibloater.data.Knowledge
import com.spring.musk.antibloater.data.Packages
import com.spring.musk.antibloater.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the app-wide objects. Small enough that a DI framework would be more code than it saves. */
class AntiBloater : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val knowledge by lazy { Knowledge(this) }
    val packages by lazy { Packages(this, knowledge) }
    val history by lazy { History(this) }
    val prefs by lazy { Prefs(this) }
    val connection by lazy { Connection(this, Adb(this), scope) }
    val debloater by lazy { Debloater(connection, packages, history, prefs) }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            knowledge.load()
            packages.reload()
        }
    }
}

val Context.app get() = applicationContext as AntiBloater
