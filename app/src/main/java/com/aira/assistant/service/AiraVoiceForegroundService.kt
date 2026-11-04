package com.aira.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aira.assistant.MainActivity
import com.aira.assistant.R

/**
 * Foreground Microphone Service (`AiraVoiceForegroundService`) with explicit "Stop Listening"
 * notification control whenever continuous voice conversation is active.
 */
class AiraVoiceForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "aira_voice_session_channel"
        const val NOTIFICATION_ID = 4001
        const val ACTION_STOP_VOICE_SERVICE = "com.aira.assistant.action.STOP_VOICE_SERVICE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_VOICE_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.voice_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val stopIntent = Intent(this, AiraVoiceForegroundService::class.java).apply {
            action = ACTION_STOP_VOICE_SERVICE
        }
        val stopPi = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AIRA Voice Conversation Active")
            .setContentText("Listening in continuous mode. Tap Stop Listening anytime.")
            .setContentIntent(openPi)
            .addAction(0, getString(R.string.stop_listening), stopPi)
            .setOngoing(true)
            .build()
    }
}
