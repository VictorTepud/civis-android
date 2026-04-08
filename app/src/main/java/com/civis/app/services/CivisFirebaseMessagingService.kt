package com.civis.app.services

import android.util.Log
import com.civis.app.data.api.ApiClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CivisFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo FCM token: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Mensaje recibido de FCM: type=${message.data["type"]}")

        val data = message.data
        val type = data["type"] ?: return

        when (type) {
            "chat_message" -> {
                val senderName = data["senderName"] ?: "Nuevo mensaje"
                val senderAvatar = data["senderAvatar"]
                val messageContent = data["content"] ?: ""
                val conversationId = data["conversationId"] ?: ""
                val senderId = data["senderId"] ?: ""
                val replyContent = data["replyContent"]

                NotificationHelper.showChatMessage(
                    context = this,
                    conversationId = conversationId,
                    senderName = senderName,
                    senderAvatar = senderAvatar,
                    messageContent = messageContent,
                    senderId = senderId,
                    replyContent = replyContent
                )
            }
            "incoming_call" -> {
                val callerName = data["callerName"] ?: "Llamada entrante"
                val callType = data["callType"] ?: "de voz"
                val callData = data["callData"] ?: ""

                NotificationHelper.showIncomingCall(
                    context = this,
                    callerName = callerName,
                    callType = callType,
                    callData = callData
                )
            }
        }
    }

    private fun sendTokenToServer(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.usersApi.updateFcmToken(
                    mapOf("fcm_token" to token)
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "FCM token enviado al servidor")
                } else {
                    Log.e(TAG, "Error enviando FCM token: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando FCM token", e)
            }
        }
    }
}
