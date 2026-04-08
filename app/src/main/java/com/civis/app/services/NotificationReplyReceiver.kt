package com.civis.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.SendMessageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver que recibe las respuestas inline desde las notificaciones.
 * Envía el mensaje al servidor y actualiza la notificación.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationReply"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra("conversationId") ?: return
        val receiverId = intent.getStringExtra("receiverId") ?: return

        // Obtener texto del reply
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.REPLY_KEY)?.toString()

        if (replyText.isNullOrBlank()) return

        Log.d(TAG, "Reply recibido para conversación $conversationId: $replyText")

        // Mostrar "Enviando..." en la notificación
        NotificationHelper.showSendingFeedback(context, conversationId)

        // Enviar mensaje al servidor
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Obtener o crear conversación si no existe
                val convResponse = ApiClient.messagesApi.sendMessageToConversation(
                    conversationId,
                    SendMessageRequest(
                        content = replyText,
                        messageType = "text"
                    )
                )

                if (convResponse.isSuccessful) {
                    Log.d(TAG, "Reply enviado exitosamente")
                    NotificationHelper.addReplyToConversation(context, conversationId, replyText)
                } else {
                    Log.e(TAG, "Error enviando reply: ${convResponse.code()}")
                    // Mostrar error en la notificación — reemplazar "Enviando..." con el texto real
                    NotificationHelper.addReplyToConversation(context, conversationId, replyText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando reply: ${e.message}")
                NotificationHelper.addReplyToConversation(context, conversationId, replyText)
            }
        }
    }
}
