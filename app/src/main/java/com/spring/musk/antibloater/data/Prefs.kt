package com.spring.musk.antibloater.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _removeAction = MutableStateFlow(
        Action.entries.firstOrNull { it.name == prefs.getString("action", null) } ?: Action.Uninstall)
    /** What "Remove" does: uninstall for this user, or only disable. Both can be undone. */
    val removeAction: StateFlow<Action> = _removeAction.asStateFlow()

    private val _allowUnsafe = MutableStateFlow(prefs.getBoolean("unsafe", false))
    /** Unsafe packages can stop the phone from booting; they stay locked unless this is on. */
    val allowUnsafe: StateFlow<Boolean> = _allowUnsafe.asStateFlow()

    fun setRemoveAction(action: Action) {
        _removeAction.value = action
        prefs.edit().putString("action", action.name).apply()
    }

    fun setAllowUnsafe(value: Boolean) {
        _allowUnsafe.value = value
        prefs.edit().putBoolean("unsafe", value).apply()
    }
}
