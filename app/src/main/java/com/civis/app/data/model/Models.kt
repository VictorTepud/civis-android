package com.civis.app.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: Any? = null
)

data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val avatar: String? = null,
    val bio: String? = null,
    val online: Boolean = false,
    val lastSeen: String? = null,
    val privacySettings: PrivacySettings? = null,
    val createdAt: String? = null
)

data class PrivacySettings(
    val lastSeen: String = "everyone",
    val profilePhoto: String = "everyone",
    val about: String = "everyone",
    val status: String = "everyone",
    val readReceipts: Boolean = true
)

data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val receiverId: String? = null,
    val groupId: String? = null,
    val content: String? = null,
    val messageType: String = "text",
    val mediaUrl: String? = null,
    val replyTo: String? = null,
    val forwarded: Boolean = false,
    val read: Boolean = false,
    val deleted: Boolean = false,
    val createdAt: String? = null,
    val sender: User? = null
)

data class Conversation(
    val id: String = "",
    val type: String = "individual",
    val name: String? = null,
    val avatar: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0,
    val participants: List<User> = emptyList(),
    val otherUser: User? = null,
    val online: Boolean = false
)

data class Contact(
    val contactId: String = "",
    val nickname: String? = null,
    val blocked: Boolean = false,
    val muted: Boolean = false,
    val user: User = User()
)

data class Group(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val avatar: String? = null,
    val createdBy: String = "",
    val members: List<User> = emptyList(),
    val settings: GroupSettings? = null
)

data class GroupSettings(
    val onlyAdminsCanSend: Boolean = false,
    val onlyAdminsCanEdit: Boolean = false
)

data class Status(
    val id: String = "",
    val userId: String = "",
    val type: String = "text",
    val content: String? = null,
    val mediaUrl: String? = null,
    val backgroundColor: String? = null,
    val viewers: List<String> = emptyList(),
    val replies: List<StatusReply> = emptyList(),
    val expiresAt: String? = null,
    val createdAt: String? = null,
    val user: User? = null
)

data class StatusReply(
    val userId: String = "",
    val content: String = "",
    val createdAt: String? = null
)

data class Community(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val avatar: String? = null,
    val cover: String? = null,
    val createdBy: String = "",
    val settings: CommunitySettings? = null,
    val members: List<User> = emptyList(),
    val memberCount: Int = 0,
    val channels: List<Channel> = emptyList()
)

data class CommunitySettings(
    val onlyAdminsCanPost: Boolean = false,
    val isPublic: Boolean = true
)

data class Channel(
    val id: String = "",
    val communityId: String = "",
    val name: String = "",
    val description: String? = null,
    val type: String = "text",
    val createdBy: String = "",
    val createdAt: String? = null
)

data class Call(
    val id: String = "",
    val type: String = "voice",
    val callerId: String = "",
    val receiverId: String? = null,
    val groupId: String? = null,
    val status: String = "ringing",
    val duration: Long = 0,
    val startedAt: String? = null,
    val caller: User? = null
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val phone: String = ""
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String = "",
    val user: User = User()
)

data class SendMessageRequest(
    val receiverId: String? = null,
    val groupId: String? = null,
    val content: String? = null,
    val messageType: String = "text",
    val mediaUrl: String? = null,
    val replyTo: String? = null
)

data class UpdateProfileRequest(
    val name: String? = null,
    val bio: String? = null,
    val phone: String? = null,
    val avatar: String? = null
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class CreateGroupRequest(
    val name: String,
    val memberIds: List<String>,
    val description: String? = null
)

data class UpdateGroupRequest(
    val name: String? = null,
    val description: String? = null,
    val avatar: String? = null
)

data class AddMemberRequest(
    val userId: String
)

data class CreateStatusRequest(
    val type: String = "text",
    val content: String? = null,
    val mediaUrl: String? = null,
    val backgroundColor: String? = null
)

data class ReplyStatusRequest(
    val content: String
)

data class CreateCommunityRequest(
    val name: String,
    val description: String? = null
)

data class CreateChannelRequest(
    val name: String,
    val description: String? = null,
    val type: String = "text"
)

data class InitiateCallRequest(
    val receiverId: String? = null,
    val groupId: String? = null,
    val type: String = "voice"
)

data class SignalRequest(
    val signalType: String,
    val signalData: String
)

data class ReplyRequest(
    val content: String
)

data class ForwardRequest(
    val receiverId: String? = null,
    val groupId: String? = null
)

data class AddContactRequest(
    val userId: String
)

data class NicknameRequest(
    val nickname: String
)
