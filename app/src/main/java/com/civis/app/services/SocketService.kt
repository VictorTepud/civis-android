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
import com.civis.app.utils.OfflineSyncManager
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
import com.civis.app.utils.appGson
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class SocketService : Service() {

    private val gson = appGson
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = NotificationHelper.CHANNEL_SERVICE

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        startForeground(NOTIFICATION_ID, createNotification("Civis activo"))
        setupSocketListeners()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: return

        SocketManager.on("message_$currentUserId") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            try {
                val message = gson.fromJson(data.toString(), Message::class.java)
                CoroutineScope(Dispatchers.IO).launch {
                    OfflineSyncManager.saveReceivedMessage(message)
                }
                // Usar NotificationHelper en lugar de la notificación básica
                val conversationId = message.conversationId
                val senderName = message.sender?.name ?: "Usuario"
                val senderAvatar = message.sender?.avatar ?: ""
                val senderId = message.senderId
                val content = message.content ?: "Multimedia"

                NotificationHelper.showChatMessage(
                    context = this@SocketService,
                    conversationId = conversationId,
                    senderId = senderId,
                    senderName = senderName,
                    senderAvatar = senderAvatar,
                    content = content
                )
            } catch (e: Exception) {
                // Ignorar errores
            }
        }

        SocketManager.on("incoming_call") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val type = data.optString("type", "voice")
            val callerName = data.optString("callerName", "Llamada entrante")
            showCallNotification(callerName, type)
        }
    }

    private fun showCallNotification(callerName: String, callType: String) {
        NotificationHelper.createChannels(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CALLS)
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
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""
        SocketManager.off("message_$currentUserId")
        SocketManager.off("incoming_call")
    }
}
