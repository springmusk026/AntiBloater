package com.spring.musk.antibloater.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    /** "just now", "5m ago", "3h ago", "yesterday", then a date. */
    fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = (now - at) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            minutes < 48 * 60 -> "yesterday"
            else -> date(at)
        }
    }

    fun date(at: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(at))

    fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

    fun thousands(n: Int): String = "%,d".format(n)
}
