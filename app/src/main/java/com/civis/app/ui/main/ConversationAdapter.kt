package com.civis.app.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.civis.app.data.model.Conversation
import com.civis.app.databinding.ItemConversationBinding
import com.civis.app.utils.formatDate
import com.civis.app.utils.loadAvatar

class ConversationAdapter(
    private val onItemClick: (Conversation) -> Unit,
    private val onLongClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(a: Conversation, b: Conversation): Boolean = a.id == b.id
        override fun areContentsTheSame(a: Conversation, b: Conversation): Boolean =
            a.lastMessage == b.lastMessage && a.unreadCount == b.unreadCount && a.lastMessageTime == b.lastMessageTime
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        with(holder.binding) {
            // Nombre: usar otherUser.name o name de la conversación
            val displayName = conversation.otherUser?.name ?: conversation.name ?: "Desconocido"
            tvName.text = displayName

            tvLastMessage.text = conversation.lastMessage ?: ""
            tvTime.text = conversation.lastMessageTime?.formatDate() ?: ""

            if (conversation.unreadCount > 0) {
                tvUnreadCount.text = conversation.unreadCount.toString()
                tvUnreadCount.visibility = android.view.View.VISIBLE
                tvName.setTypeface(tvName.typeface, android.graphics.Typeface.BOLD)
                tvLastMessage.setTypeface(tvLastMessage.typeface, android.graphics.Typeface.BOLD)
            } else {
                tvUnreadCount.visibility = android.view.View.GONE
                tvName.setTypeface(tvName.typeface, android.graphics.Typeface.NORMAL)
                tvLastMessage.setTypeface(tvLastMessage.typeface, android.graphics.Typeface.NORMAL)
            }

            // Avatar: usar otherUser.avatar o el de la conversación
            val avatarUrl = conversation.otherUser?.avatar
                ?: conversation.avatar
                ?: conversation.participants.firstOrNull()?.avatar
            ivAvatar.loadAvatar(avatarUrl)

            // Online status
            val isOnline = conversation.otherUser?.online ?: conversation.online
            if (isOnline) {
                viewOnlineDot.visibility = android.view.View.VISIBLE
            } else {
                viewOnlineDot.visibility = android.view.View.GONE
            }

            root.setOnClickListener { onItemClick(conversation) }
            root.setOnLongClickListener { onLongClick(conversation); true }
        }
    }
}
