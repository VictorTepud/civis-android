package com.civis.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.app.Person
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.civis.app.R
import com.civis.app.config.ServerConfig
import com.civis.app.ui.chat.ChatActivity
import com.civis.app.utils.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper para mostrar notificaciones estilo chat con MessagingStyle,
 * reply inline, y agrupación por conversación.
 */
object NotificationHelper {

    const val CHANNEL_MESSAGES = "civis_messages"
    const val CHANNEL_CALLS = "civis_calls"
    const val CHANNEL_SERVICE = "civis_socket_channel"
    private const val REPLY_KEY = "notification_reply_key"

    // Una notificación por conversación (el notification ID es el hashCode del conversationId)
    private val conversationMessages = ConcurrentHashMap<String, MutableList<android.app.NotificationCompat.MessagingStyle.Message>>()
    private val conversationSenders = ConcurrentHashMap<String, String>() // conversationId -> senderName
    private val conversationAvatars = ConcurrentHashMap<String, String>() // conversationId -> senderAvatar
    private val conversationSenderIds = ConcurrentHashMap<String, String>() // conversationId -> senderId

    /** Conversación que el usuario tiene abierta — no mostrar notificación si coincide */
    @Volatile
    private var openConversationId: String? = null

    fun setOpenConversation(conversationId: String?) {
        openConversationId = conversationId
    }

    /** Crear canales de notificación (Android 8+) */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Mensajes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de mensajes de chat"
                enableVibration(true)
                setBypassDnd(true)
            }

            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Llamadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de llamadas entrantes"
                enableVibration(true)
                setBypassDnd(true)
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

    /**
     * Construir URL completa a partir de un path relativo.
     * Los avatares vienen como "/uploads/avatars/..." y necesitan el host completo.
     */
    private fun buildUrl(relativePath: String?): String {
        if (relativePath.isNullOrBlank()) return ""
        return if (relativePath.startsWith("http")) {
            relativePath
        } else {
            "${ServerConfig.BASE_URL}$relativePath"
        }
    }

    /**
     * Mostrar notificación de chat con MessagingStyle y acción de reply inline.
     */
    fun showChatMessage(
        context: Context,
        conversationId: String,
        senderId: String,
        senderName: String,
        senderAvatar: String?,
        content: String,
        senderDisplayName: String = senderName
    ) {
        // Si la conversación está abierta, no mostrar notificación
        if (conversationId == openConversationId) return

        createChannels(context)

        // Guardar metadata de la conversación
        conversationSenders[conversationId] = senderName
        conversationAvatars[conversationId] = senderAvatar ?: ""
        conversationSenderIds[conversationId] = senderId

        // Agregar mensaje al historial
        val messages = conversationMessages.getOrPut(conversationId) { mutableListOf() }
        messages.add(
            NotificationCompat.MessagingStyle.Message(
                content,
                System.currentTimeMillis(),
                senderDisplayName
            )
        )
        // Mantener solo los últimos 10 mensajes
        while (messages.size > 10) {
            messages.removeAt(0)
        }

        // El "usuario" de este dispositivo para MessagingStyle
        val me = Person.Builder()
            .setName(TokenManager.getInstance().getUser()?.name ?: "Yo")
            .build()

        val style = NotificationCompat.MessagingStyle(me)
        messages.forEach { style.addMessage(it) }

        // Intent para abrir ChatActivity al tocar la notificación
        val chatIntent = Intent(context, ChatActivity::class.java).apply {
            putExtra("conversationId", conversationId)
            putExtra("receiverId", senderId)
            putExtra("receiverName", senderName)
            putExtra("receiverAvatar", senderAvatar ?: "")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Reply action con RemoteInput
        val remoteInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel("Responder...")
            .build()

        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            putExtra("conversationId", conversationId)
            putExtra("receiverId", senderId)
            putExtra("receiverName", senderName)
            putExtra("receiverAvatar", senderAvatar ?: "")
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            conversationId.hashCode() + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_send,
            "Responder",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val notificationId = conversationId.hashCode()

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(style)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false) // No auto-cancel para que MessagingStyle se acumule
            .setNumber(messages.size)
            .setShowWhen(true)
            .addAction(replyAction)
            .setGroup(conversationId)

        // Cargar avatar con Glide si está disponible
        val avatarUrl = buildUrl(senderAvatar)
        if (avatarUrl.isNotEmpty()) {
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = try {
                        Glide.with(context)
                            .asBitmap()
                            .load(avatarUrl)
                            .circleCrop()
                            .submit()
                            .get()
                    } catch (_: Exception) { null }

                    if (bitmap != null) {
                        val person = Person.Builder()
                            .setName(senderDisplayName)
                            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap))
                            .build()

                        val newStyle = NotificationCompat.MessagingStyle(me)
                        messages.forEach { msg ->
                            if (msg.person?.name == senderDisplayName) {
                                newStyle.addMessage(NotificationCompat.MessagingStyle.Message(
                                    msg.text, msg.timestamp, person
                                ))
                            } else {
                                newStyle.addMessage(msg)
                            }
                        }

                        val updatedBuilder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setStyle(newStyle)
                            .setContentIntent(openPendingIntent)
                            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(false)
                            .setNumber(messages.size)
                            .setShowWhen(true)
                            .addAction(replyAction)
                            .setGroup(conversationId)
                            .setLargeIcon(bitmap)

                        NotificationManagerCompat.from(context).notify(notificationId, updatedBuilder.build())
                    }
                }
            } catch (_: Exception) {}
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    /**
     * Agregar el reply del usuario a la notificación para que se vea reflejado.
     */
    fun addReplyToConversation(context: Context, conversationId: String, replyText: String) {
        createChannels(context)

        val messages = conversationMessages[conversationId] ?: return
        val myName = TokenManager.getInstance().getUser()?.name ?: "Yo"

        messages.add(
            NotificationCompat.MessagingStyle.Message(
                replyText,
                System.currentTimeMillis(),
                myName
            )
        )

        val senderName = conversationSenders[conversationId] ?: "Chat"
        val me = Person.Builder().setName(myName).build()

        val style = NotificationCompat.MessagingStyle(me)
        messages.forEach { style.addMessage(it) }

        // Actualizar notificación existente
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setNumber(messages.size)
            .setShowWhen(true)
            .setGroup(conversationId)
            .setOnlyAlertOnce(true) // No hacer sonido/vibración al actualizar con reply

        NotificationManagerCompat.from(context).notify(conversationId.hashCode(), builder.build())
    }

    /**
     * Cancelar notificaciones de una conversación (cuando el usuario abre el chat).
     */
    fun cancelConversationNotifications(context: Context, conversationId: String?) {
        if (conversationId.isNullOrEmpty()) return
        NotificationManagerCompat.from(context).cancel(conversationId.hashCode())
        conversationMessages.remove(conversationId)
        conversationSenders.remove(conversationId)
        conversationAvatars.remove(conversationId)
        conversationSenderIds.remove(conversationId)
    }

    /** Mostrar feedback "Enviando..." temporal en la notificación */
    fun showSendingFeedback(context: Context, conversationId: String) {
        val messages = conversationMessages[conversationId] ?: return
        val myName = TokenManager.getInstance().getUser()?.name ?: "Yo"

        messages.add(
            NotificationCompat.MessagingStyle.Message(
                "Enviando...",
                System.currentTimeMillis(),
                myName
            )
        )

        val me = Person.Builder().setName(myName).build()
        val style = NotificationCompat.MessagingStyle(me)
        messages.forEach { style.addMessage(it) }

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setNumber(messages.size)
            .setShowWhen(true)
            .setGroup(conversationId)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)

        NotificationManagerCompat.from(context).notify(conversationId.hashCode(), builder.build())
    }
}
