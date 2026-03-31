package com.civis.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.civis.app.R

class CallService : Service() {

    private val NOTIFICATION_ID = 1002
    private val CHANNEL_ID = "civis_call_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callType = intent?.getStringExtra("call_type") ?: "voice"
        val callerName = intent?.getStringExtra("caller_name") ?: "Llamada"
        val statusText = when (callType) {
            "video" -> "Videollamada en curso: $callerName"
            else -> "Llamada en curso: $callerName"
        }
        startForeground(NOTIFICATION_ID, createNotification(statusText))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Llamada Civis",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Llamada en curso"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Civis")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
