package com.qigai.neon.scheduled

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.qigai.neon.R

class HandsForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neon 正在干活")
            .setContentText(intent?.getStringExtra("status")?.takeIf { it.isNotBlank() } ?: "切到别的应用也会继续")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        androidx.core.app.ServiceCompat.startForeground(
            this, NOTIF_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Neon 后台执行", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        const val ACTION_STOP = "neon.fg.stop"

        /** Keeps the process and its network alive while a turn runs, so switching apps does not cut the stream. */
        fun keepAlive(context: android.content.Context, on: Boolean, status: String = "") {
            runCatching {
                val i = Intent(context, HandsForegroundService::class.java)
                if (on) androidx.core.content.ContextCompat.startForegroundService(context, i.putExtra("status", status))
                else context.stopService(i)
            }
        }
        private const val CHANNEL_ID = "neon_agent_fg"
        private const val NOTIF_ID = 2001
    }
}
