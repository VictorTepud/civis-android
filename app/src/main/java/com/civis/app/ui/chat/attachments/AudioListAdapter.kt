package com.civis.app.ui.chat.attachments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.civis.app.databinding.ItemAudioListBinding

/**
 * Adapter para lista de archivos de audio.
 */
class AudioListAdapter(
    private val items: List<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<AudioListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAudioListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAudioListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.binding.tvFileName.text = item.name

        // Mostrar duración y tamaño
        val parts = mutableListOf<String>()
        if (item.duration > 0) {
            val seconds = (item.duration / 1000) % 60
            val minutes = (item.duration / 60000) % 60
            parts.add("%d:%02d".format(minutes, seconds))
        }
        if (item.size > 0) {
            parts.add(formatFileSize(item.size))
        }
        holder.binding.tvFileInfo.text = parts.joinToString(" · ")

        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
