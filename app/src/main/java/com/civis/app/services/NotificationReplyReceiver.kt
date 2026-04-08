package com.civis.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.civis.app.utils.SocketManager
import org.json.JSONObject

/**
 * BroadcastReceiver que maneja la respuesta inline desde la notificación.
 * Envía el mensaje por socket usando el evento 'message:send'.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationReply"
        const val ACTION_REPLY = "com.civis.app.ACTION_REPLY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getString(NotificationHelper.REPLY_KEY)
        if (replyText.isNullOrEmpty()) return

        val receiverId = intent.getStringExtra("receiverId") ?: return
        val conversationId = intent.getStringExtra("conversationId") ?: return

        // Mostrar la respuesta del usuario en la notificación
        NotificationHelper.addReplyToConversation(context, conversationId, replyText)

        // Enviar el mensaje por socket (como lo hace el ChatActivity)
        try {
            SocketManager.emit("message:send", JSONObject().apply {
                put("conversation_id", conversationId)
                put("content", replyText)
                put("message_type", "text")
            })
            Log.d(TAG, "Respuesta enviada desde notificación")
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando respuesta desde notificación", e)
        }
    }
}
