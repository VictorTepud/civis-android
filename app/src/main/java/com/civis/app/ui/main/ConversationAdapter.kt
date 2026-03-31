package com.civis.app.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.civis.app.data.model.Conversation
import com.civis.app.databinding.ItemConversationBinding
import com.civis.app.utils.formatDate
import com.civis.app.utils.toGlideUrl

class ConversationAdapter(
    private val onItemClick: (Conversation) -> Unit,
    private val onLongClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(a: Conversation, b: Conversation): Boolean = a.id == b.id
        override fun areContentsTheSame(a: Conversation, b: Conversation): Boolean = a == b
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
            tvName.text = conversation.name ?: "Desconocido"
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

            val avatarUrl = conversation.avatar
                ?: conversation.participants.firstOrNull()?.avatar
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(root.context)
                    .load(avatarUrl.toGlideUrl())
                    .placeholder(com.civis.app.R.drawable.ic_profile)
                    .into(ivAvatar)
            }

            if (conversation.online) {
                viewOnlineDot.visibility = android.view.View.VISIBLE
            } else {
                viewOnlineDot.visibility = android.view.View.GONE
            }

            root.setOnClickListener { onItemClick(conversation) }
            root.setOnLongClickListener { onLongClick(conversation); true }
        }
    }
}
