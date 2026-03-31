package com.civis.app.utils

import android.util.Log
import com.civis.app.data.api.ApiClient
import com.civis.app.data.local.LocalDatabase
import com.civis.app.data.local.LocalMessage
import com.civis.app.data.model.Message
import com.civis.app.data.model.SendMessageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gestiona la sincronización de mensajes offline con SQLite.
 */
object OfflineSyncManager {

    private const val TAG = "OfflineSyncManager"
    private var scope: CoroutineScope? = null
    var db: LocalDatabase? = null
        private set
    private var isSyncing = false

    fun init(database: LocalDatabase) {
        db = database
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        observeNetworkChanges()
    }

    private fun observeNetworkChanges() {
        scope?.launch {
            NetworkMonitor.isConnected.collect { connected ->
                if (connected) {
                    Log.d(TAG, "Conexión restaurada, sincronizando mensajes pendientes...")
                    syncPendingMessages()
                }
            }
        }
    }

    /**
     * Guarda un mensaje localmente con estado "pending".
     */
    suspend fun saveMessagePending(
        id: String,
        conversationId: String,
        senderId: String,
        receiverId: String?,
        content: String?,
        messageType: String = "text",
        mediaUrl: String? = null,
        replyTo: String? = null
    ): LocalMessage {
        val localMsg = LocalMessage(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            messageType = messageType,
            mediaUrl = mediaUrl,
            replyTo = replyTo,
            createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date()),
            status = "pending"
        )
        db?.insertMessage(localMsg)
        return localMsg
    }

    /**
     * Envía un mensaje. Si hay conexión lo envía al servidor.
     * Si no, lo guarda localmente como pendiente.
     */
    suspend fun sendOrQueueMessage(request: SendMessageRequest): Message? {
        val database = db ?: return null
        val tempId = UUID.randomUUID().toString()
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""

        if (NetworkMonitor.isConnected.value) {
            try {
                val response = ApiClient.messagesApi.sendMessage(request)
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val msg = Gson().fromJson(Gson().toJson(data), Message::class.java)
                        val localMsg = toLocalMessage(msg, "sent")
                        database.insertMessage(localMsg)
                        return msg
                    }
                }
                Log.w(TAG, "Error al enviar mensaje: ${response.code()}")
                saveMessagePending(
                    id = tempId,
                    conversationId = "",
                    senderId = currentUserId,
                    receiverId = request.receiverId,
                    content = request.content,
                    messageType = request.messageType,
                    mediaUrl = request.mediaUrl,
                    replyTo = request.replyTo
                )
                database.updateStatus(tempId, "failed")
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al enviar: ${e.message}")
                saveMessagePending(
                    id = tempId,
                    conversationId = "",
                    senderId = currentUserId,
                    receiverId = request.receiverId,
                    content = request.content,
                    messageType = request.messageType,
                    mediaUrl = request.mediaUrl,
                    replyTo = request.replyTo
                )
                return null
            }
        } else {
            saveMessagePending(
                id = tempId,
                conversationId = "",
                senderId = currentUserId,
                receiverId = request.receiverId,
                content = request.content,
                messageType = request.messageType,
                mediaUrl = request.mediaUrl,
                replyTo = request.replyTo
            )
            return null
        }
    }

    /**
     * Sincroniza mensajes pendientes cuando se restaura la conexión.
     */
    private suspend fun syncPendingMessages() {
        val database = db ?: return
        if (isSyncing) return
        isSyncing = true

        try {
            val pending = database.getPendingMessages()
            if (pending.isEmpty()) {
                isSyncing = false
                return
            }

            Log.d(TAG, "Sincronizando ${pending.size} mensajes pendientes...")

            for (msg in pending) {
                try {
                    database.updateStatus(msg.id, "sending")
                    val request = SendMessageRequest(
                        receiverId = msg.receiverId,
                        content = msg.content,
                        messageType = msg.messageType,
                        mediaUrl = msg.mediaUrl,
                        replyTo = msg.replyTo
                    )
                    val response = ApiClient.messagesApi.sendMessage(request)
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val serverMsg = Gson().fromJson(Gson().toJson(data), Message::class.java)
                            database.softDelete(msg.id)
                            database.insertMessage(toLocalMessage(serverMsg, "sent"))
                            Log.d(TAG, "Mensaje sincronizado: ${serverMsg.id}")
                        }
                    } else {
                        database.updateStatus(msg.id, "failed")
                    }
                } catch (e: Exception) {
                    database.updateStatus(msg.id, "failed")
                    Log.e(TAG, "Error sincronizando: ${e.message}")
                }
            }
        } finally {
            isSyncing = false
        }
    }

    /**
     * Obtiene mensajes de una conversación (locales + sincroniza si hay red).
     */
    suspend fun getMessages(conversationId: String): List<LocalMessage> {
        val database = db ?: return emptyList()

        // Mostrar locales primero
        if (NetworkMonitor.isConnected.value) {
            try {
                val response = ApiClient.messagesApi.getMessages(conversationId)
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val type = object : TypeToken<List<Message>>() {}.type
                        val serverMessages: List<Message> = Gson().fromJson(Gson().toJson(data), type)
                        val pending = database.getPendingMessages()
                        val serverIds = serverMessages.map { it.id }.toSet()
                        val localToKeep = pending.filter { it.id !in serverIds }
                        database.insertMessages(serverMessages.map { toLocalMessage(it, "sent") } + localToKeep)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sincronizando: ${e.message}")
            }
        }

        return database.getMessages(conversationId)
    }

    /**
     * Guarda mensaje recibido por socket.
     */
    suspend fun saveReceivedMessage(message: Message) {
        db?.insertMessage(toLocalMessage(message, "sent"))
    }

    /**
     * Marca todos los mensajes de una conversación como leídos (local).
     */
    fun markAllAsReadLocal(conversationId: String, currentUserId: String) {
        db?.markAllAsRead(conversationId, currentUserId)
    }

    suspend fun clearAll() {
        db?.deleteAll()
    }

    fun toLocalMessage(message: Message, status: String): LocalMessage {
        return LocalMessage(
            id = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            content = message.content,
            messageType = message.messageType,
            mediaUrl = message.mediaUrl,
            replyTo = message.replyTo,
            forwarded = message.forwarded,
            read = message.read,
            deleted = message.deleted,
            createdAt = message.createdAt,
            senderName = message.sender?.name,
            senderAvatar = message.sender?.avatar,
            status = status
        )
    }
}
