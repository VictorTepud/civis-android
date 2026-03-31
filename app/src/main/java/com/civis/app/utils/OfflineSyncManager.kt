package com.civis.app.utils

import android.util.Log
import com.civis.app.data.api.ApiClient
import com.civis.app.data.local.AppDatabase
import com.civis.app.data.local.LocalMessage
import com.civis.app.data.model.Message
import com.civis.app.data.model.SendMessageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*

/**
 * Gestiona la sincronización de mensajes offline.
 * - Guarda mensajes localmente cuando no hay conexión
 * - Los envía automáticamente cuando se restaura la conexión
 * - Sincroniza mensajes del servidor con la base local
 */
object OfflineSyncManager {

    private const val TAG = "OfflineSyncManager"
    private var scope: CoroutineScope? = null
    private var db: AppDatabase? = null
    private var isSyncing = false

    fun init(database: AppDatabase) {
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
     * Se usa cuando no hay conexión o como paso previo al envío.
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
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date()),
            status = "pending"
        )
        db?.messageDao()?.insert(localMsg)
        return localMsg
    }

    /**
     * Intenta enviar un mensaje. Si hay conexión, lo envía al servidor.
     * Si no, lo guarda localmente como pendiente.
     */
    suspend fun sendOrQueueMessage(request: SendMessageRequest): Message? {
        val messageDao = db?.messageDao() ?: return null

        // Generar ID temporal para el mensaje local
        val tempId = java.util.UUID.randomUUID().toString()
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""

        if (NetworkMonitor.isConnected.value) {
            // Hay conexión - intentar enviar directamente
            try {
                val response = ApiClient.messagesApi.sendMessage(request)
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val msg = Gson().fromJson(Gson().toJson(data), Message::class.java)
                        // Guardar en base local como "sent"
                        val localMsg = toLocalMessage(msg, "sent")
                        messageDao.insert(localMsg)
                        return msg
                    }
                }
                // Si falló el envío pero hay conexión, marcar como failed
                Log.w(TAG, "Error al enviar mensaje: ${response.code()}")
                val pendingMsg = saveMessagePending(
                    id = tempId,
                    conversationId = "",
                    senderId = currentUserId,
                    receiverId = request.receiverId,
                    content = request.content,
                    messageType = request.messageType,
                    mediaUrl = request.mediaUrl,
                    replyTo = request.replyTo
                )
                messageDao.updateStatus(tempId, "failed")
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Excepción al enviar: ${e.message}")
                // Error de conexión - guardar como pendiente
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
            // Sin conexión - guardar localmente como pendiente
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
     * Sincroniza todos los mensajes pendientes cuando se restaura la conexión.
     */
    private suspend fun syncPendingMessages() {
        val messageDao = db?.messageDao() ?: return
        if (isSyncing) return
        isSyncing = true

        try {
            val pending = messageDao.getPendingMessages()
            if (pending.isEmpty()) {
                isSyncing = false
                return
            }

            Log.d(TAG, "Sincronizando ${pending.size} mensajes pendientes...")

            for (msg in pending) {
                try {
                    messageDao.updateStatus(msg.id, "sending")
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
                            // Eliminar el mensaje local temporal y guardar el del servidor
                            messageDao.softDelete(msg.id)
                            val localMsg = toLocalMessage(serverMsg, "sent")
                            messageDao.insert(localMsg)
                            Log.d(TAG, "Mensaje sincronizado: ${serverMsg.id}")
                        }
                    } else {
                        messageDao.updateStatus(msg.id, "failed")
                        Log.w(TAG, "No se pudo sincronizar mensaje ${msg.id}: ${response.code()}")
                    }
                } catch (e: Exception) {
                    messageDao.updateStatus(msg.id, "failed")
                    Log.e(TAG, "Error sincronizando mensaje ${msg.id}: ${e.message}")
                }
            }
        } finally {
            isSyncing = false
        }
    }

    /**
     * Sincroniza mensajes de una conversación con el servidor.
     * Descarga los últimos mensajes y los guarda localmente.
     */
    suspend fun syncConversationWithServer(conversationId: String): List<LocalMessage> {
        val messageDao = db?.messageDao() ?: return emptyList()

        if (!NetworkMonitor.isConnected.value) {
            // Sin conexión - devolver solo los locales
            return messageDao.getMessagesByConversation(conversationId)
        }

        try {
            val response = ApiClient.messagesApi.getMessages(conversationId)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    val type = object : TypeToken<List<Message>>() {}.type
                    val serverMessages: List<Message> = Gson().fromJson(Gson().toJson(data), type)
                    // Convertir a mensajes locales y guardar
                    val localMessages = serverMessages.map { toLocalMessage(it, "sent") }
                    messageDao.insertAll(localMessages)
                    return messageDao.getMessagesByConversation(conversationId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando conversación: ${e.message}")
        }

        // Si falló, devolver los locales
        return messageDao.getMessagesByConversation(conversationId)
    }

    /**
     * Guarda un mensaje recibido por socket en la base local.
     */
    suspend fun saveReceivedMessage(message: Message) {
        val messageDao = db?.messageDao() ?: return
        val localMsg = toLocalMessage(message, "sent")
        messageDao.insert(localMsg)
    }

    /**
     * Obtiene todos los mensajes de una conversación (locales primero, luego sincroniza si hay conexión).
     */
    suspend fun getMessages(conversationId: String): List<LocalMessage> {
        val messageDao = db?.messageDao() ?: return emptyList()

        // Primero mostrar los locales inmediatamente
        val localMessages = messageDao.getMessagesByConversation(conversationId)

        // Si hay conexión, sincronizar con el servidor
        if (NetworkMonitor.isConnected.value) {
            try {
                val response = ApiClient.messagesApi.getMessages(conversationId)
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val type = object : TypeToken<List<Message>>() {}.type
                        val serverMessages: List<Message> = Gson().fromJson(Gson().toJson(data), type)

                        // Preservar mensajes pendientes que no están en el servidor
                        val pendingLocal = messageDao.getPendingMessagesByConversation(conversationId)
                        val serverIds = serverMessages.map { it.id }.toSet()

                        val localMessagesToKeep = pendingLocal.filter { it.id !in serverIds }

                        // Guardar mensajes del servidor
                        val toInsert = serverMessages.map { toLocalMessage(it, "sent") } + localMessagesToKeep
                        messageDao.insertAll(toInsert)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al sincronizar: ${e.message}")
            }
        }

        return messageDao.getMessagesByConversation(conversationId)
    }

    /**
     * Eliminar todos los datos locales (para logout).
     */
    suspend fun clearAll() {
        db?.messageDao()?.deleteAll()
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
