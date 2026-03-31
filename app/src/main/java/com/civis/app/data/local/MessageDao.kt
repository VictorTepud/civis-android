package com.civis.app.data.local

import androidx.room.*

/**
 * DAO para operaciones CRUD de mensajes locales.
 */
@Dao
interface MessageDao {

    // Obtener todos los mensajes de una conversación en orden ascendente
    @Query("SELECT * FROM local_messages WHERE conversationId = :conversationId AND deleted = 0 ORDER BY createdAt ASC")
    suspend fun getMessagesByConversation(conversationId: String): List<LocalMessage>

    // Obtener mensajes pendientes de envío
    @Query("SELECT * FROM local_messages WHERE status IN ('pending', 'failed') ORDER BY createdAt ASC")
    suspend fun getPendingMessages(): List<LocalMessage>

    // Obtener pendientes para una conversación específica
    @Query("SELECT * FROM local_messages WHERE conversationId = :conversationId AND status IN ('pending', 'failed') ORDER BY createdAt ASC")
    suspend fun getPendingMessagesByConversation(conversationId: String): List<LocalMessage>

    // Insertar un mensaje
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: LocalMessage)

    // Insertar múltiples mensajes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<LocalMessage>)

    // Actualizar estado de un mensaje
    @Query("UPDATE local_messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    // Marcar mensaje como leído
    @Query("UPDATE local_messages SET read = 1 WHERE id = :messageId")
    suspend fun markAsRead(messageId: String)

    // Marcar todos los mensajes de una conversación como leídos
    @Query("UPDATE local_messages SET read = 1 WHERE conversationId = :conversationId AND senderId != :currentUserId")
    suspend fun markAllAsRead(conversationId: String, currentUserId: String)

    // Eliminar mensaje (soft delete)
    @Query("UPDATE local_messages SET deleted = 1 WHERE id = :messageId")
    suspend fun softDelete(messageId: String)

    // Eliminar todos los mensajes de una conversación
    @Query("DELETE FROM local_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    // Obtener el último mensaje de una conversación
    @Query("SELECT * FROM local_messages WHERE conversationId = :conversationId AND deleted = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: String): LocalMessage?

    // Obtener conteo de no leídos por conversación
    @Query("SELECT COUNT(*) FROM local_messages WHERE conversationId = :conversationId AND senderId != :currentUserId AND read = 0 AND deleted = 0")
    suspend fun getUnreadCount(conversationId: String, currentUserId: String): Int

    // Eliminar todos los mensajes (para logout)
    @Query("DELETE FROM local_messages")
    suspend fun deleteAll()
}
