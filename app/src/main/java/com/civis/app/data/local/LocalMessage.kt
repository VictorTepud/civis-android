package com.civis.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import androidx.room.ColumnInfo

/**
 * Entidad de Room para almacenar mensajes localmente.
 * Permite funcionar sin conexión a internet.
 */
@Entity(tableName = "local_messages")
data class LocalMessage(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String? = null,
    val content: String? = null,
    val messageType: String = "text",
    val mediaUrl: String? = null,
    val replyTo: String? = null,
    val forwarded: Boolean = false,
    val read: Boolean = false,
    val deleted: Boolean = false,
    val createdAt: String? = null,
    val senderName: String? = null,
    val senderAvatar: String? = null,

    /**
     * Estado del mensaje:
     * - "pending": creado localmente, espera para enviarse
     * - "sending":正在通过网络发送
     * - "sent": enviado al servidor correctamente
     * - "failed": error al enviar, se puede reintentar
     */
    val status: String = "sent"
)
