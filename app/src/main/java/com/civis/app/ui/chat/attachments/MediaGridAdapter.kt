package com.civis.app.ui.chat.attachments

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.databinding.ItemMediaGridBinding

/**
 * Datos de un archivo multimedia del dispositivo.
 */
data class MediaItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val duration: Long = 0,
    val size: Long = 0
)

/**
 * Adapter para grid de imágenes y videos.
 * Soporta un item especial de cámara en la primera posición.
 */
class MediaGridAdapter(
    private val items: List<MediaItem>,
    private val type: Type,
    private val onItemClick: (MediaItem) -> Unit,
    private val onSelectionChanged: (Set<Int>) -> Unit,
    private val showCamera: Boolean = false,
    private val onCameraClick: (() -> Unit)? = null
) : RecyclerView.Adapter<MediaGridAdapter.ViewHolder>() {

    enum class Type { GALLERY, VIDEO }

    private val TYPE_CAMERA = 0
    private val TYPE_MEDIA = 1

    class ViewHolder(val binding: ItemMediaGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return if (showCamera && position == 0) TYPE_CAMERA else TYPE_MEDIA
    }

    override fun getItemCount(): Int {
        return if (showCamera) items.size + 1 else items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMediaGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_CAMERA) {
            // Celda de cámara
            holder.binding.ivMedia.setImageResource(R.drawable.ic_camera)
            holder.binding.ivMedia.scaleType = android.widget.ImageView.ScaleType.CENTER
            holder.binding.ivMedia.setBackgroundColor(
                holder.binding.root.context.getColor(R.color.surface)
            )
            holder.binding.ivPlayIcon.visibility = View.GONE
            holder.binding.tvDuration.visibility = View.GONE
            holder.binding.ivCheck.visibility = View.GONE
            holder.binding.selectionOverlay.visibility = View.GONE
            holder.binding.root.setOnClickListener {
                onCameraClick?.invoke()
            }
            return
        }

        // Media item (offset position by 1 if camera is shown)
        val item = items[if (showCamera) position - 1 else position]
        val ctx = holder.binding.root.context

        // Restaurar background default
        holder.binding.ivMedia.setBackgroundColor(0)
        holder.binding.ivMedia.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        holder.binding.ivCheck.visibility = View.GONE
        holder.binding.selectionOverlay.visibility = View.GONE

        // Cargar thumbnail
        Glide.with(ctx)
            .load(item.uri)
            .centerCrop()
            .into(holder.binding.ivMedia)

        // Video: mostrar duración e ícono de play
        if (type == Type.VIDEO) {
            holder.binding.ivPlayIcon.visibility = View.VISIBLE
            if (item.duration > 0) {
                val seconds = (item.duration / 1000) % 60
                val minutes = (item.duration / 60000) % 60
                val hours = item.duration / 3600000
                val text = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
                           else "%d:%02d".format(minutes, seconds)
                holder.binding.tvDuration.text = text
                holder.binding.tvDuration.visibility = View.VISIBLE
            }
        } else {
            holder.binding.ivPlayIcon.visibility = View.GONE
            holder.binding.tvDuration.visibility = View.GONE
        }

        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }
    }
}
