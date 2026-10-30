package com.spring.musk.antibloater.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.spring.musk.antibloater.R
import com.spring.musk.antibloater.app
import com.spring.musk.antibloater.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a notification up while the user is in Settings, like Shizuku: it finds the
 * pairing port by itself, so the only thing to type is the 6-digit code, right in
 * the notification. Stops once paired.
 */
class PairingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Pairing", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lets you enter the wireless debugging pairing code without leaving Settings"
                setShowBadge(false)
            })
        val first = build(Status.Pairing(port = null))
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(ID, first)

        scope.launch {
            app.connection.status.collect { status ->
                when (status) {
                    is Status.Pairing -> manager.notify(ID, build(status))
                    Status.Connecting -> manager.notify(ID, build(status))
                    Status.Connected -> {
                        manager.notify(DONE_ID, done())
                        stopSelf()
                    }
                    else -> stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(status: Status): Notification {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(Notification.Action.Builder(null, "Cancel", broadcast(ACTION_CANCEL, 2)).build())
        when {
            status is Status.Pairing && status.busy ->
                builder.setContentTitle("Pairing…")
            status is Status.Pairing && status.port != null -> {
                val input = RemoteInput.Builder(KEY_CODE).setLabel("6-digit code").build()
                val reply = Notification.Action.Builder(null, "Enter code", broadcast(ACTION_CODE, 1, mutable = true))
                    .addRemoteInput(input)
                    .build()
                builder.setContentTitle(status.error ?: "Pairing code ready")
                    .setContentText("Tap “Enter code” and type the 6 digits shown in Settings")
                    .addAction(reply)
            }
            status is Status.Pairing ->
                builder.setContentTitle("Waiting for the pairing code")
                    .setStyle(Notification.BigTextStyle().bigText(
                        "In Wireless debugging, tap “Pair device with pairing code”. You can enter the code here without leaving Settings."))
                    .setContentText("In Wireless debugging, tap “Pair device with pairing code”")
            else -> builder.setContentTitle("Connecting…")
        }
        return builder.build()
    }

    private fun done() = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle("Paired")
        .setContentText("Tap to go back to AntiBloater")
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .setTimeoutAfter(60_000)
        .build()

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE)

    private fun broadcast(action: String, request: Int, mutable: Boolean = false) = PendingIntent.getBroadcast(
        this, request, Intent(this, Reply::class.java).setAction(action),
        if (mutable) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_IMMUTABLE)

    /** Receives the code typed into the notification, or Cancel. */
    class Reply : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val connection = context.app.connection
            if (intent.action == ACTION_CANCEL) return connection.cancelPairing()
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString() ?: return
            // The foreground service keeps the process alive while this runs
            context.app.scope.launch { connection.submitCode(code) }
        }
    }

    companion object {
        private const val CHANNEL = "pairing"
        private const val ID = 1
        private const val DONE_ID = 2
        private const val KEY_CODE = "code"
        private const val ACTION_CODE = "com.spring.musk.antibloater.PAIR_CODE"
        private const val ACTION_CANCEL = "com.spring.musk.antibloater.PAIR_CANCEL"
    }
}
