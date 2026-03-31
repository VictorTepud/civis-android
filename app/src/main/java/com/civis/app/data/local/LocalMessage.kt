package com.civis.app.data.local

/**
 * Modelo de mensaje local (almacenado en SQLite).
 * Equivalente al anterior LocalMessage de Room, pero sin anotaciones.
 */
data class LocalMessage(
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
     * - "sending": enviándose por la red
     * - "sent": enviado al servidor correctamente
     * - "failed": error al enviar, se puede reintentar
     */
    val status: String = "sent"
)
