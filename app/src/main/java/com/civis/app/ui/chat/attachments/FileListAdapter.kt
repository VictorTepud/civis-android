package com.civis.app.ui.chat.attachments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.civis.app.R
import com.civis.app.databinding.ItemFileListBinding

/**
 * Adapter para lista de archivos del gestor de documentos.
 */
class FileListAdapter(
    private val items: List<MediaItem>,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<FileListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFileListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvFileName.text = item.name
        holder.binding.tvFileInfo.text = formatFileInfo(item.mimeType, item.size)

        // Icono según tipo de archivo
        val iconRes = getFileIcon(item.mimeType)
        try {
            holder.binding.ivFileIcon.setImageResource(iconRes)
        } catch (_: Exception) {
            holder.binding.ivFileIcon.setImageResource(R.drawable.ic_attach)
        }

        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private fun getFileIcon(mimeType: String): Int {
        return when {
            mimeType.contains("pdf") -> R.drawable.ic_file_pdf
            mimeType.contains("word") || mimeType.contains("document") -> R.drawable.ic_file_doc
            mimeType.contains("sheet") || mimeType.contains("excel") -> R.drawable.ic_file_sheet
            mimeType.contains("presentation") || mimeType.contains("powerpoint") -> R.drawable.ic_file_ppt
            mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("7z") -> R.drawable.ic_file_zip
            mimeType.contains("text") || mimeType.contains("json") || mimeType.contains("xml") ||
                mimeType.contains("html") || mimeType.contains("javascript") || mimeType.contains("java") ||
                mimeType.contains("python") || mimeType.contains("kotlin") || mimeType.contains("sql") -> R.drawable.ic_file_text
            mimeType.startsWith("image/") -> R.drawable.ic_file_image
            mimeType.startsWith("video/") -> R.drawable.ic_file_video
            mimeType.startsWith("audio/") -> R.drawable.ic_file_audio
            mimeType.contains("apk") -> R.drawable.ic_file_apk
            else -> R.drawable.ic_attach
        }
    }

    private fun formatFileInfo(mimeType: String, size: Long): String {
        val typeLabel = when {
            mimeType.contains("pdf") -> "PDF"
            mimeType.contains("word") || mimeType.contains("document") -> "DOCX"
            mimeType.contains("sheet") || mimeType.contains("excel") -> "XLSX"
            mimeType.contains("presentation") || mimeType.contains("powerpoint") -> "PPTX"
            mimeType.contains("text") || mimeType.contains("json") || mimeType.contains("xml") ||
                mimeType.contains("html") || mimeType.contains("javascript") -> "TXT"
            mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("7z") -> "ZIP"
            mimeType.startsWith("image/") -> "Imagen"
            mimeType.startsWith("video/") -> "Video"
            mimeType.startsWith("audio/") -> "Audio"
            mimeType.contains("apk") -> "APK"
            mimeType.contains("octet-stream") -> "Archivo"
            else -> {
                val ext = mimeType.substringAfterLast("/").uppercase()
                if (ext.length <= 6 && ext.all { it.isLetterOrDigit() }) ext else "Archivo"
            }
        }
        val sizeStr = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
            else -> "%.1f MB".format(size / (1024.0 * 1024.0))
        }
        return "$typeLabel · $sizeStr"
    }
}
