package com.civis.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.civis.app.R
import com.civis.app.databinding.DialogMediaPreviewBinding

/**
 * Muestra un dialogo de preview fullscreen para imagenes/videos con campo de caption.
 * Para videos: usa VideoView para reproducir. Para imagenes: usa ImageView.
 * NO sube el archivo; simplemente retorna el URI + caption + tipo cuando el usuario presiona Enviar.
 * La subida se maneja en ChatActivity.
 */
class MediaUploadHelper(
    private val context: Context,
    private val onReadyToSend: (Uri, String, String) -> Unit // (uri, type, caption)
) {

    private var dialog: AlertDialog? = null
    private var binding: DialogMediaPreviewBinding? = null
    private var mediaType: String = "image"
    private var mediaPlayer: MediaPlayer? = null

    @SuppressLint("SetTextI18n")
    fun show(uri: Uri, type: String) {
        mediaType = type
        val b = DialogMediaPreviewBinding.inflate(LayoutInflater.from(context))
        binding = b

        dialog = AlertDialog.Builder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(b.root)
            .setCancelable(true)
            .setOnCancelListener { dismiss() }
            .create()
        dialog?.show()

        // Cargar preview
        if (type == "video") {
            // Mostrar VideoView para reproducir video
            b.ivPreview.visibility = View.GONE
            b.videoPreview.visibility = View.VISIBLE
            b.ivPlayOverlay.visibility = View.GONE

            // Retrasar setVideoURI hasta que el dialogo esté completamente medido
            // (SurfaceView dentro de AlertDialog puede no tener surface lista inmediatamente)
            b.videoPreview.post {
                try {
                    b.videoPreview.setVideoURI(uri)
                    b.videoPreview.setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        b.videoPreview.start()
                        b.ivPlayOverlay.visibility = View.GONE
                    }
                    b.videoPreview.setOnErrorListener { _, _, _ ->
                        b.videoPreview.visibility = View.GONE
                        b.ivPreview.visibility = View.VISIBLE
                        b.ivPlayOverlay.visibility = View.VISIBLE
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(context, uri)
                            val bitmap = retriever.getFrameAtTime(0)
                            if (bitmap != null) {
                                b.ivPreview.setImageBitmap(bitmap)
                                bitmap.recycle()
                            }
                            retriever.release()
                        } catch (_: Exception) {
                            b.ivPreview.setImageResource(R.drawable.ic_video)
                        }
                        true
                    }
                    b.videoPreview.start()
                } catch (_: Exception) {
                    // Si VideoView falla, mostrar frame estático
                    b.videoPreview.visibility = View.GONE
                    b.ivPreview.visibility = View.VISIBLE
                    b.ivPlayOverlay.visibility = View.VISIBLE
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        val bitmap = retriever.getFrameAtTime(0)
                        if (bitmap != null) {
                            b.ivPreview.setImageBitmap(bitmap)
                            bitmap.recycle()
                        }
                        retriever.release()
                    } catch (_: Exception) {
                        b.ivPreview.setImageResource(R.drawable.ic_video)
                    }
                }
            }

            // Tap para pausar/reanudar
            b.previewContainer.setOnClickListener {
                if (b.videoPreview.isPlaying) {
                    b.videoPreview.pause()
                    b.ivPlayOverlay.visibility = View.VISIBLE
                } else {
                    b.videoPreview.start()
                    b.ivPlayOverlay.visibility = View.GONE
                }
            }
        } else {
            // Mostrar ImageView para imagen
            b.ivPreview.visibility = View.VISIBLE
            b.videoPreview.visibility = View.GONE
            b.ivPlayOverlay.visibility = View.GONE
            b.ivPreview.setImageURI(uri)
        }

        // Obtener nombre y tamaño
        val fileName = queryFileName(context, uri) ?: "archivo"
        b.tvFileName.text = fileName
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val sizeBytes = inputStream.available().toLong()
                inputStream.close()
                val sizeKb = sizeBytes / 1024
                val sizeStr = if (sizeKb > 1024) String.format("%.1f MB", sizeKb / 1024.0) else "$sizeKb KB"
                b.tvFileSize.text = sizeStr
            }
        } catch (_: Exception) {}

        // Listeners
        b.btnClose.setOnClickListener { dismiss() }
        b.btnSendMedia.setOnClickListener {
            val caption = b.etCaption.text.toString().trim()
            onReadyToSend(uri, mediaType, caption)
            dismiss()
        }
    }

    fun dismiss() {
        try {
            binding?.videoPreview?.stopPlayback()
        } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
        dialog?.dismiss()
        dialog = null
        binding = null
    }

    companion object {
        fun queryFileName(context: Context, uri: Uri): String? {
            return try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                    } else null
                }
            } catch (_: Exception) { null }
        }
    }
}
