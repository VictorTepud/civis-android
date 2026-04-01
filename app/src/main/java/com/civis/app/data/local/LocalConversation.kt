package com.civis.app.data.local

import com.civis.app.data.model.Conversation
import com.civis.app.data.model.User

/**
 * Modelo de conversación almacenada en SQLite para uso offline.
 * Se usa para mostrar la lista de chats sin conexión.
 */
data class LocalConversation(
    val id: String,
    val type: String = "individual",
    val name: String? = null,
    val avatar: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0,
    val otherUserId: String? = null,
    val otherUserName: String? = null,
    val otherUserAvatar: String? = null,
    val otherUserOnline: Boolean = false,
    val otherUserLastSeen: String? = null
) {
    /**
     * Convierte a Conversation del modelo de datos para el adapter.
     */
    fun toConversation(): Conversation {
        return Conversation(
            id = id,
            type = type,
            name = name,
            avatar = avatar,
            lastMessage = lastMessage,
            lastMessageTime = lastMessageTime,
            unreadCount = unreadCount,
            otherUser = if (otherUserId != null) {
                User(
                    id = otherUserId,
                    name = otherUserName ?: "",
                    avatar = otherUserAvatar,
                    online = otherUserOnline,
                    lastSeen = otherUserLastSeen
                )
            } else null
        )
    }

    companion object {
        /**
         * Crea un LocalConversation a partir de un Conversation del modelo de datos.
         */
        fun fromConversation(conv: Conversation): LocalConversation {
            return LocalConversation(
                id = conv.id,
                type = conv.type,
                name = conv.name,
                avatar = conv.avatar,
                lastMessage = conv.lastMessage,
                lastMessageTime = conv.lastMessageTime,
                unreadCount = conv.unreadCount,
                otherUserId = conv.otherUser?.id,
                otherUserName = conv.otherUser?.name,
                otherUserAvatar = conv.otherUser?.avatar,
                otherUserOnline = conv.otherUser?.online ?: false,
                otherUserLastSeen = conv.otherUser?.lastSeen
            )
        }
    }
}
