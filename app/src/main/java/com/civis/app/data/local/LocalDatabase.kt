package com.civis.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Helper de SQLite directo para almacenar mensajes y conversaciones localmente.
 * Permite funcionar offline y sincronizar cuando vuelve la conexión.
 */
class LocalDatabase(context: Context) : SQLiteOpenHelper(context, "civis_messages.db", null, 2) {

    companion object {
        @Volatile
        private var instance: LocalDatabase? = null

        fun getInstance(context: Context): LocalDatabase {
            return instance ?: synchronized(this) {
                instance ?: LocalDatabase(context.applicationContext).also { instance = it }
            }
        }

        fun destroyInstance() {
            instance?.close()
            instance = null
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                receiver_id TEXT,
                content TEXT,
                message_type TEXT DEFAULT 'text',
                media_url TEXT,
                reply_to TEXT,
                forwarded INTEGER DEFAULT 0,
                read INTEGER DEFAULT 0,
                deleted INTEGER DEFAULT 0,
                created_at TEXT,
                sender_name TEXT,
                sender_avatar TEXT,
                status TEXT DEFAULT 'sent'
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conv_id ON messages(conversation_id)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS conversations (
                id TEXT PRIMARY KEY,
                type TEXT DEFAULT 'individual',
                name TEXT,
                avatar TEXT,
                last_message TEXT,
                last_message_time TEXT,
                unread_count INTEGER DEFAULT 0,
                other_user_id TEXT,
                other_user_name TEXT,
                other_user_avatar TEXT,
                other_user_online INTEGER DEFAULT 0,
                other_user_last_seen TEXT
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversations (
                    id TEXT PRIMARY KEY,
                    type TEXT DEFAULT 'individual',
                    name TEXT,
                    avatar TEXT,
                    last_message TEXT,
                    last_message_time TEXT,
                    unread_count INTEGER DEFAULT 0,
                    other_user_id TEXT,
                    other_user_name TEXT,
                    other_user_avatar TEXT,
                    other_user_online INTEGER DEFAULT 0,
                    other_user_last_seen TEXT
                )
            """)
        }
    }

    // ========== Mensajes ==========

    fun getMessages(conversationId: String): List<LocalMessage> {
        val list = mutableListOf<LocalMessage>()
        val db = readableDatabase
        val cursor = db.query(
            "messages",
            null,
            "conversation_id = ? AND deleted = 0",
            arrayOf(conversationId),
            null, null, "created_at ASC"
        )
        cursor.use {
            while (it.moveToNext()) list.add(it.toLocalMessage())
        }
        return list
    }

    fun getPendingMessages(): List<LocalMessage> {
        val list = mutableListOf<LocalMessage>()
        val db = readableDatabase
        val cursor = db.query(
            "messages",
            null,
            "status IN ('pending', 'failed') AND deleted = 0",
            null, null, null, "created_at ASC"
        )
        cursor.use {
            while (it.moveToNext()) list.add(it.toLocalMessage())
        }
        return list
    }

    fun insertMessage(msg: LocalMessage) {
        val db = writableDatabase
        val values = msg.toContentValues()
        db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun insertMessages(messages: List<LocalMessage>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (msg in messages) {
                db.insertWithOnConflict("messages", null, msg.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateStatus(messageId: String, status: String) {
        val db = writableDatabase
        db.execSQL("UPDATE messages SET status = ? WHERE id = ?", arrayOf(status, messageId))
    }

    fun markAsRead(messageId: String) {
        val db = writableDatabase
        db.execSQL("UPDATE messages SET read = 1 WHERE id = ?", arrayOf(messageId))
    }

    fun markAllAsRead(conversationId: String, currentUserId: String) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE messages SET read = 1 WHERE conversation_id = ? AND sender_id != ? AND deleted = 0",
            arrayOf(conversationId, currentUserId)
        )
    }

    fun softDelete(messageId: String) {
        val db = writableDatabase
        db.execSQL("UPDATE messages SET deleted = 1 WHERE id = ?", arrayOf(messageId))
    }

    fun deleteByConversation(conversationId: String) {
        val db = writableDatabase
        db.delete("messages", "conversation_id = ?", arrayOf(conversationId))
    }

    fun deleteAllMessages() {
        val db = writableDatabase
        db.delete("messages", null, null)
    }

    // ========== Conversaciones ==========

    fun getConversations(): List<LocalConversation> {
        val list = mutableListOf<LocalConversation>()
        val db = readableDatabase
        val cursor = db.query(
            "conversations",
            null, null, null, null, null,
            "last_message_time DESC"
        )
        cursor.use {
            while (it.moveToNext()) list.add(it.toLocalConversation())
        }
        return list
    }

    fun insertConversations(conversations: List<LocalConversation>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (conv in conversations) {
                db.insertWithOnConflict("conversations", null, conv.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertConversation(conv: LocalConversation) {
        val db = writableDatabase
        db.insertWithOnConflict("conversations", null, conv.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateLastMessage(conversationId: String, lastMessage: String?, lastMessageTime: String?) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE conversations SET last_message = ?, last_message_time = ? WHERE id = ?",
            arrayOf(lastMessage, lastMessageTime, conversationId)
        )
    }

    fun deleteAllConversations() {
        val db = writableDatabase
        db.delete("conversations", null, null)
    }

    // ========== Extensiones ==========

    private fun android.database.Cursor.toLocalMessage(): LocalMessage {
        return LocalMessage(
            id = getString(getColumnIndexOrThrow("id")),
            conversationId = getString(getColumnIndexOrThrow("conversation_id")),
            senderId = getString(getColumnIndexOrThrow("sender_id")),
            receiverId = getStringOrNull(getColumnIndex("receiver_id")),
            content = getStringOrNull(getColumnIndex("content")),
            messageType = getString(getColumnIndexOrThrow("message_type")),
            mediaUrl = getStringOrNull(getColumnIndex("media_url")),
            replyTo = getStringOrNull(getColumnIndex("reply_to")),
            forwarded = getInt(getColumnIndexOrThrow("forwarded")) == 1,
            read = getInt(getColumnIndexOrThrow("read")) == 1,
            deleted = getInt(getColumnIndexOrThrow("deleted")) == 1,
            createdAt = getStringOrNull(getColumnIndex("created_at")),
            senderName = getStringOrNull(getColumnIndex("sender_name")),
            senderAvatar = getStringOrNull(getColumnIndex("sender_avatar")),
            status = getString(getColumnIndexOrThrow("status"))
        )
    }

    private fun android.database.Cursor.toLocalConversation(): LocalConversation {
        return LocalConversation(
            id = getString(getColumnIndexOrThrow("id")),
            type = getStringOrNull(getColumnIndex("type")) ?: "individual",
            name = getStringOrNull(getColumnIndex("name")),
            avatar = getStringOrNull(getColumnIndex("avatar")),
            lastMessage = getStringOrNull(getColumnIndex("last_message")),
            lastMessageTime = getStringOrNull(getColumnIndex("last_message_time")),
            unreadCount = getInt(getColumnIndexOrThrow("unread_count")),
            otherUserId = getStringOrNull(getColumnIndex("other_user_id")),
            otherUserName = getStringOrNull(getColumnIndex("other_user_name")),
            otherUserAvatar = getStringOrNull(getColumnIndex("other_user_avatar")),
            otherUserOnline = getInt(getColumnIndexOrThrow("other_user_online")) == 1,
            otherUserLastSeen = getStringOrNull(getColumnIndex("other_user_last_seen"))
        )
    }

    private fun android.database.Cursor.getStringOrNull(index: Int): String? {
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun LocalMessage.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("conversation_id", conversationId)
            put("sender_id", senderId)
            put("receiver_id", receiverId)
            put("content", content)
            put("message_type", messageType)
            put("media_url", mediaUrl)
            put("reply_to", replyTo)
            put("forwarded", if (forwarded) 1 else 0)
            put("read", if (read) 1 else 0)
            put("deleted", if (deleted) 1 else 0)
            put("created_at", createdAt)
            put("sender_name", senderName)
            put("sender_avatar", senderAvatar)
            put("status", status)
        }
    }

    private fun LocalConversation.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("type", type)
            put("name", name)
            put("avatar", avatar)
            put("last_message", lastMessage)
            put("last_message_time", lastMessageTime)
            put("unread_count", unreadCount)
            put("other_user_id", otherUserId)
            put("other_user_name", otherUserName)
            put("other_user_avatar", otherUserAvatar)
            put("other_user_online", if (otherUserOnline) 1 else 0)
            put("other_user_last_seen", otherUserLastSeen)
        }
    }
}
