package com.civis.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.ui.chat.ChatActivity
import com.civis.app.utils.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sistema de notificaciones de chat estilo WhatsApp:
 * - UNA notificación por conversación (se actualiza con cada mensaje)
 * - MessagingStyle con historial de mensajes
 * - Input de respuesta inline (RemoteInput)
 * - Tocar abre el chat directamente
 */
object NotificationHelper {

    const val CHANNEL_MESSAGES = "civis_messages"
    const val CHANNEL_CALLS = "civis_calls"
    const val CHANNEL_SERVICE = "civis_socket_channel"
    const val REPLY_KEY = "civis_reply_key"

    private var currentOpenConversationId: String? = null

    /** Datos acumulados por conversación */
    private data class ConversationData(
        var senderName: String,
        var senderId: String,
        var senderAvatar: String?,
        val messages: MutableList<NotificationCompat.MessagingStyle.Message> = mutableListOf(),
        var largeIcon: Bitmap? = null
    )

    private val conversationData = mutableMapOf<String, ConversationData>()

    fun setOpenConversation(conversationId: String?) {
        currentOpenConversationId = conversationId
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Mensajes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de mensajes nuevos"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Llamadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de llamadas entrantes"
                enableVibration(true)
                enableLights(true)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Civis Servicio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la conexión activa"
            }

            manager.createNotificationChannel(messagesChannel)
            manager.createNotificationChannel(callsChannel)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun showChatMessage(
        context: Context,
        conversationId: String,
        senderName: String,
        senderAvatar: String?,
        messageContent: String,
        senderId: String,
        replyContent: String?
    ) {
        // No mostrar notificación si el usuario está viendo este chat
        if (conversationId == currentOpenConversationId) return

        // Asegurar que los canales existen (por si el servicio no los creó)
        createChannels(context)

        val data = synchronized(conversationData) {
            conversationData.getOrPut(conversationId) {
                ConversationData(senderName, senderId, senderAvatar)
            }
        }

        data.senderName = senderName
        data.senderId = senderId
        if (senderAvatar != null) data.senderAvatar = senderAvatar

        val displayText = if (!replyContent.isNullOrEmpty()) "↩ $replyContent\n$messageContent" else messageContent

        synchronized(data.messages) {
            data.messages.add(
                NotificationCompat.MessagingStyle.Message(
                    displayText,
                    System.currentTimeMillis(),
                    senderName
                )
            )
            while (data.messages.size > 6) {
                data.messages.removeAt(0)
            }
        }

        val notificationId = conversationId.hashCode()
        val requestCode = notificationId and 0x7FFFFFFF

        // === Intent: abrir chat al tocar ===
        val chatIntent = Intent(context, ChatActivity::class.java).apply {
            putExtra("conversationId", conversationId)
            putExtra("receiverId", senderId)
            putExtra("receiverName", senderName)
            putExtra("receiverAvatar", senderAvatar)
            putExtra("fromNotification", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, requestCode, chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // === MessagingStyle ===
        val myName = TokenManager.getInstance().getUser()?.display_name ?: "Yo"
        val style = NotificationCompat.MessagingStyle(myName)
        synchronized(data.messages) {
            for (msg in data.messages) {
                style.addMessage(msg)
            }
        }

        // === Acción de responder (RemoteInput) ===
        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = NotificationReplyReceiver.ACTION_REPLY
            putExtra("conversationId", conversationId)
            putExtra("receiverId", senderId)
            putExtra("senderName", senderName)
            putExtra("notificationId", notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, requestCode, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel("Responder...")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_send,
            "Responder",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()

        // === Construir notificación ===
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(displayText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(replyAction)

        data.largeIcon?.let { builder.setLargeIcon(it) }

        NotificationManagerCompat.from(context).notify("chat_$conversationId", notificationId, builder.build())

        // Cargar avatar async
        if (data.largeIcon == null && !senderAvatar.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bitmap: Bitmap? = Glide.with(context.applicationContext)
                        .asBitmap()
                        .load(buildUrl(senderAvatar))
                        .circleCrop()
                        .submit()
                        .get()
                    bitmap?.let {
                        data.largeIcon = it
                        builder.setLargeIcon(it)
                        NotificationManagerCompat.from(context).notify(
                            "chat_$conversationId", notificationId, builder.build()
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun addReplyToConversation(
        context: Context,
        conversationId: String,
        replyText: String
    ) {
        val data = synchronized(conversationData) {
            conversationData[conversationId]
        } ?: return

        val myName = TokenManager.getInstance().getUser()?.display_name ?: "Yo"

        synchronized(data.messages) {
            data.messages.add(
                NotificationCompat.MessagingStyle.Message(
                    replyText,
                    System.currentTimeMillis(),
                    myName
                )
            )
        }

        val notificationId = conversationId.hashCode()
        val style = NotificationCompat.MessagingStyle(myName)
        synchronized(data.messages) {
            for (msg in data.messages) {
                style.addMessage(msg)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(data.senderName)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)

        data.largeIcon?.let { builder.setLargeIcon(it) }

        NotificationManagerCompat.from(context).notify("chat_$conversationId", notificationId, builder.build())
    }

    fun showIncomingCall(
        context: Context,
        callerName: String,
        callType: String,
        callData: String
    ) {
        createChannels(context)
        val notificationId = 2002

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setContentTitle("Llamada $callType entrante")
            .setContentText(callerName)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        NotificationManagerCompat.from(context).notify("call", notificationId, builder.build())
    }

    fun cancelCallNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel("call", 2002)
    }

    fun cancelConversationNotifications(context: Context, conversationId: String) {
        try {
            NotificationManagerCompat.from(context).cancel("chat_$conversationId", conversationId.hashCode())
            synchronized(conversationData) {
                conversationData.remove(conversationId)
            }
        } catch (_: Exception) {}
    }

    private fun buildUrl(path: String): String {
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            val clean = if (path.startsWith("/")) path.substring(1) else path
            "${com.civis.app.config.ServerConfig.BASE_URL}/$clean"
        }
    }
}
