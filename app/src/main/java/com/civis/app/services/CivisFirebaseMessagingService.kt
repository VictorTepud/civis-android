package com.civis.app.services

import android.util.Log
import com.civis.app.data.api.ApiClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service.
 * - onNewToken: envía el token FCM al servidor
 * - onMessageReceived: muestra la notificación con MessagingStyle
 */
class CivisFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo FCM token: ${token.take(20)}...")

        // Enviar token al servidor
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.usersApi.updateFcmToken(
                    com.civis.app.data.model.FcmTokenRequest(fcmToken = token)
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "FCM token enviado al servidor")
                } else {
                    Log.e(TAG, "Error enviando FCM token: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando FCM token: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Mensaje FCM recibido")

        // El payload viene en data (not notification) porque enviamos ambos
        val data = message.data
        if (data.isEmpty()) {
            Log.w(TAG, "FCM data vacío, ignorando")
            return
        }

        val type = data["type"] ?: return

        when (type) {
            "chat_message" -> {
                val conversationId = data["conversationId"] ?: return
                val senderId = data["senderId"] ?: return
                val senderName = data["senderName"] ?: "Usuario"
                val senderAvatar = data["senderAvatar"] ?: ""
                val content = data["content"] ?: ""

                NotificationHelper.showChatMessage(
                    context = this,
                    conversationId = conversationId,
                    senderId = senderId,
                    senderName = senderName,
                    senderAvatar = senderAvatar,
                    content = content
                )
            }
            "incoming_call" -> {
                val callType = data["callType"] ?: "audio"
                val callerName = data["callerName"] ?: "Llamada entrante"
                // Las llamadas se manejan con la actividad IncomingCallActivity vía socket
                // pero podemos mostrar una notificación como backup
                Log.d(TAG, "Llamada entrante FCM: $callerName ($callType)")
            }
        }
    }
}
