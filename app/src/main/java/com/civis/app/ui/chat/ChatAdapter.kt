package com.civis.app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.data.local.LocalMessage
import com.civis.app.databinding.ItemMessageSentBinding
import com.civis.app.databinding.ItemMessageReceivedBinding
import com.civis.app.databinding.ItemMessageImageBinding
import com.civis.app.utils.TokenManager
import com.civis.app.utils.formatTime
import com.civis.app.utils.toGlideUrl

class ChatAdapter(
    private val onMessageLongClick: (LocalMessage, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<LocalMessage>()
    private val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""

    companion object {
        const val TYPE_SENT_TEXT = 0
        const val TYPE_RECEIVED_TEXT = 1
        const val TYPE_SENT_IMAGE = 2
        const val TYPE_RECEIVED_IMAGE = 3
    }

    fun submitList(list: List<LocalMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val isMine = message.senderId == currentUserId
        return when {
            message.messageType == "image" && isMine -> TYPE_SENT_IMAGE
            message.messageType == "image" && !isMine -> TYPE_RECEIVED_IMAGE
            isMine -> TYPE_SENT_TEXT
            else -> TYPE_RECEIVED_TEXT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SENT_TEXT -> SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
            TYPE_RECEIVED_TEXT -> ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
            TYPE_SENT_IMAGE -> ImageViewHolder(ItemMessageImageBinding.inflate(inflater, parent, false))
            TYPE_RECEIVED_IMAGE -> ImageViewHolder(ItemMessageImageBinding.inflate(inflater, parent, false))
            else -> ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is SentViewHolder -> bindSentText(holder.binding, message)
            is ReceivedViewHolder -> bindReceivedText(holder.binding, message)
            is ImageViewHolder -> bindImage(holder.binding, message)
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun bindSentText(binding: ItemMessageSentBinding, message: LocalMessage) {
        binding.tvMessageContent.text = if (message.deleted) "Este mensaje fue eliminado" else message.content
        binding.tvTime.text = message.createdAt?.formatTime() ?: ""

        // Estado del mensaje: pendiente, enviando, enviado, leído
        when {
            message.status == "pending" -> {
                binding.ivReadReceipt.setImageResource(R.drawable.ic_clock)
                binding.ivReadReceipt.visibility = View.VISIBLE
            }
            message.status == "sending" -> {
                binding.ivReadReceipt.setImageResource(R.drawable.ic_clock)
                binding.ivReadReceipt.visibility = View.VISIBLE
            }
            message.status == "failed" -> {
                binding.ivReadReceipt.setImageResource(R.drawable.ic_error)
                binding.ivReadReceipt.visibility = View.VISIBLE
            }
            message.read -> {
                binding.ivReadReceipt.setImageResource(R.drawable.ic_double_check_blue)
                binding.ivReadReceipt.visibility = View.VISIBLE
            }
            message.content != null -> {
                binding.ivReadReceipt.setImageResource(R.drawable.ic_double_check)
                binding.ivReadReceipt.visibility = View.VISIBLE
            }
            else -> {
                binding.ivReadReceipt.setImageResource(R.drawable.ic_check)
                binding.ivReadReceipt.visibility = View.VISIBLE
            }
        }

        binding.tvReplyPreview.visibility = if (message.replyTo != null) View.VISIBLE else View.GONE
        binding.tvReplyPreview.text = if (message.replyTo != null) "Respondiendo" else ""
        binding.tvForwarded.visibility = if (message.forwarded) View.VISIBLE else View.GONE
        binding.tvForwarded.text = "Reenviado"

        binding.root.setOnLongClickListener { onMessageLongClick(message, binding.root); true }
    }

    private fun bindReceivedText(binding: ItemMessageReceivedBinding, message: LocalMessage) {
        binding.tvMessageContent.text = if (message.deleted) "Este mensaje fue eliminado" else message.content
        binding.tvTime.text = message.createdAt?.formatTime() ?: ""
        binding.ivReadReceipt.visibility = View.GONE

        binding.tvReplyPreview.visibility = if (message.replyTo != null) View.VISIBLE else View.GONE
        binding.tvReplyPreview.text = if (message.replyTo != null) "Respondiendo" else ""
        binding.tvForwarded.visibility = if (message.forwarded) View.VISIBLE else View.GONE
        binding.tvForwarded.text = "Reenviado"

        binding.root.setOnLongClickListener { onMessageLongClick(message, binding.root); true }
    }

    private fun bindImage(binding: ItemMessageImageBinding, message: LocalMessage) {
        binding.tvTime.text = message.createdAt?.formatTime() ?: ""
        message.mediaUrl?.let { url ->
            Glide.with(binding.root.context)
                .load(url.toGlideUrl())
                .placeholder(R.drawable.ic_camera)
                .into(binding.ivImage)
        }
        binding.root.setOnLongClickListener { onMessageLongClick(message, binding.root); true }
    }

    inner class SentViewHolder(val binding: ItemMessageSentBinding) : RecyclerView.ViewHolder(binding.root)
    inner class ReceivedViewHolder(val binding: ItemMessageReceivedBinding) : RecyclerView.ViewHolder(binding.root)
    inner class ImageViewHolder(val binding: ItemMessageImageBinding) : RecyclerView.ViewHolder(binding.root)
}
