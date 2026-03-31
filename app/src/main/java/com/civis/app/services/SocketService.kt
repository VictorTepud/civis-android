package com.civis.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.civis.app.R
import com.civis.app.data.model.Message
import com.civis.app.ui.main.MainActivity
import com.civis.app.utils.SocketManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class SocketService : Service() {

    private val gson = Gson()
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "civis_socket_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Civis activo"))
        setupSocketListeners()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Civis Servicio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la conexión activa"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Civis")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun setupSocketListeners() {
        SocketManager.on("new_message") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val message = gson.fromJson(data.toString(), Message::class.java)
            showNotification(message)
        }

        SocketManager.on("incoming_call") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val type = data.optString("type", "voice")
            val callerName = data.optString("callerName", "Llamada entrante")
            showCallNotification(callerName, type)
        }

        SocketManager.on("user_typing") { args ->
            // Handled by ChatActivity if active
        }

        SocketManager.on("message_read") { args ->
            // Handled by ChatActivity if active
        }

        SocketManager.on("user_online") { _ ->
            // Update presence
        }

        SocketManager.on("user_offline") { _ ->
            // Update presence
        }
    }

    private fun showNotification(message: Message) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(message.sender?.name ?: "Nuevo mensaje")
            .setContentText(message.content ?: "Multimedia")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(message.id.hashCode(), notification)
    }

    private fun showCallNotification(callerName: String, callType: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Llamada $callType entrante")
            .setContentText(callerName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2002, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.off("new_message")
        SocketManager.off("incoming_call")
        SocketManager.off("user_typing")
        SocketManager.off("message_read")
        SocketManager.off("user_online")
        SocketManager.off("user_offline")
    }
}
