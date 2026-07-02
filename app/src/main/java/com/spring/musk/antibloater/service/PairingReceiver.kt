package com.spring.musk.antibloater.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

/**
 * Receives the inline reply from the pairing notification.
 * Parses "port code" format and broadcasts to the app via a local callback.
 */
class PairingReceiver : BroadcastReceiver() {

    companion object {
        /** Callback set by MainActivity/ViewModel to receive pairing input */
        var onPairingInput: ((port: String, code: String) -> Unit)? = null
        var onDismiss: (() -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PairingNotificationManager.ACTION_PAIR -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val input = remoteInput?.getCharSequence(PairingNotificationManager.KEY_PAIRING_INPUT)?.toString()

                if (input != null) {
                    // Parse "port code" or "port:code" format
                    val parts = input.trim().split(Regex("[\\s:]+"), limit = 2)
                    if (parts.size == 2) {
                        val port = parts[0].trim()
                        val code = parts[1].trim()
                        onPairingInput?.invoke(port, code)
                    } else if (parts.size == 1 && parts[0].length > 6) {
                        // Try splitting: first 5 chars = port, rest = code
                        val port = parts[0].take(5)
                        val code = parts[0].drop(5)
                        onPairingInput?.invoke(port, code)
                    }
                }

                // Dismiss notification after receiving input
                PairingNotificationManager(context).dismiss()
            }

            PairingNotificationManager.ACTION_DISMISS -> {
                PairingNotificationManager(context).dismiss()
                onDismiss?.invoke()
            }
        }
    }
}
