package com.spring.musk.antibloater.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.spring.musk.antibloater.R

/**
 * Manages the pairing notification with inline reply.
 *
 * Flow:
 * 1. User taps "Open Settings" → we show this notification + launch Settings
 * 2. User sees pairing code in Settings, pulls down notification shade
 * 3. User types "port code" (e.g. "37149 482604") in the reply field
 * 4. BroadcastReceiver picks it up → triggers pairing in ViewModel
 */
class PairingNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "pairing_channel"
        const val NOTIFICATION_ID = 1001
        const val KEY_PAIRING_INPUT = "key_pairing_input"
        const val ACTION_PAIR = "com.spring.musk.antibloater.ACTION_PAIR"
        const val ACTION_DISMISS = "com.spring.musk.antibloater.ACTION_DISMISS"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pairing Assistant",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows pairing input while you're in Settings"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show the pairing notification with inline reply.
     */
    fun showPairingNotification() {
        // Remote input for the reply action
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_INPUT)
            .setLabel("Enter: port code (e.g. 37149 482604)")
            .build()

        // Intent for when user submits the reply
        val pairIntent = Intent(ACTION_PAIR).apply {
            setPackage(context.packageName)
        }
        val pairPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            pairIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Reply action
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Enter Code",
            pairPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        // Dismiss action
        val dismissIntent = Intent(ACTION_DISMISS).apply {
            setPackage(context.packageName)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ready to pair")
            .setContentText("Type the port and code separated by a space")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("In Settings, tap \"Pair device with pairing code\". Then type the port and 6-digit code here, separated by a space.\n\nExample: 37149 482604")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", dismissPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Dismiss the pairing notification.
     */
    fun dismiss() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
