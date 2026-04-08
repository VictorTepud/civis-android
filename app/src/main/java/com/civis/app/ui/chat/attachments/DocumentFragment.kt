package com.civis.app.ui.chat.attachments

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.R
import com.civis.app.databinding.FragmentAttachmentDocumentBinding
import java.io.File

class DocumentFragment : Fragment() {

    private var _binding: FragmentAttachmentDocumentBinding? = null
    private val binding get() = _binding!!

    private val files = mutableListOf<MediaItem>()
    private lateinit var adapter: FileListAdapter

    var onFileSelected: ((uri: Uri, mimeType: String) -> Unit)? = null

    // Filtros disponibles
    private val filterTypes = listOf(
        FilterType("Todos", "*/*"),
        FilterType("PDF", "application/pdf"),
        FilterType("Docs", "application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        FilterType("Excel", "application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        FilterType("PPT", "application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        FilterType("Texto", "text/plain"),
        FilterType("ZIP", "application/zip,application/x-zip-compressed,application/x-rar-compressed"),
        FilterType("Imágenes", "image/*"),
        FilterType("Videos", "video/*"),
        FilterType("Audio", "audio/*"),
        FilterType("APK", "application/vnd.android.package-archive")
    )

    private var currentFilter: String = "*/*"

    /** Launcher para explorador completo del sistema */
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            // Tomar permiso persistente
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val mimeType = requireContext().contentResolver.getType(it) ?: guessMimeTypeFromUri(it)
            onFileSelected?.invoke(it, mimeType)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAttachmentDocumentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FileListAdapter(items = files) { item ->
            onFileSelected?.invoke(item.uri, item.mimeType)
        }

        binding.recyclerViewFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFiles.adapter = adapter

        createFilterChips()

        // Botón explorar archivos — abre el explorador completo del sistema
        binding.btnBrowseFiles.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        loadFiles()
    }

    private fun createFilterChips() {
        val container = binding.layoutFilters
        container.removeAllViews()

        for (filter in filterTypes) {
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = filter.label
                isCheckable = true
                isChecked = filter.mime == "*/*"
                setChipBackgroundColorResource(R.color.surface)
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 13f
                chipMinHeight = 32f
                isClickable = true
            }

            chip.setOnClickListener {
                for (i in 0 until container.childCount) {
                    (container.getChildAt(i) as? com.google.android.material.chip.Chip)?.isChecked = false
                }
                chip.isChecked = true

                if (filter.mime != currentFilter) {
                    currentFilter = filter.mime
                    loadFiles()
                }
            }

            container.addView(chip)
        }
    }

    private fun hasReadPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun loadFiles() {
        files.clear()
        adapter.notifyDataSetChanged()
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewFiles.visibility = View.GONE

        if (!hasReadPermission()) {
            binding.tvEmpty.text = "Se necesita permiso para acceder a los archivos. Toca \"Explorar archivos\" para elegir del sistema."
            binding.tvEmpty.visibility = View.VISIBLE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        Thread {
            doLoadFiles()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val b = _binding ?: return@post
                b.progressBar.visibility = View.GONE
                if (files.isEmpty()) {
                    b.tvEmpty.text = "No se encontraron archivos.\nToca \"Explorar archivos\" para elegir del sistema."
                    b.tvEmpty.visibility = View.VISIBLE
                    b.recyclerViewFiles.visibility = View.GONE
                } else {
                    b.tvEmpty.visibility = View.GONE
                    b.recyclerViewFiles.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                }
            }
        }.start()
    }

    private fun doLoadFiles() {
        // NO excluir ningún tipo: cargar TODO excepto archivos del sistema de Android
        var selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL"
        // Excluir archivos del sistema, thumbnails y carpetas vacías
        selection += " AND ${MediaStore.Files.FileColumns.SIZE} > 0"
        selection += " AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE '.%'"
        selection += " AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} != 'thumbnails'"

        var finalSelection = selection
        val selectionArgs: Array<String>? = if (currentFilter != "*/*") {
            val mimeTypes = currentFilter.split(",")
            if (mimeTypes.size == 1) {
                if (mimeTypes[0].endsWith("/*")) {
                    // Filtro por categoría: image/*, video/*, audio/*
                    finalSelection += " AND ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?"
                    arrayOf(mimeTypes[0])
                } else {
                    finalSelection += " AND ${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
                    arrayOf(mimeTypes[0])
                }
            } else {
                val placeholders = mimeTypes.joinToString(",") { "?" }
                finalSelection += " AND ${MediaStore.Files.FileColumns.MIME_TYPE} IN ($placeholders)"
                mimeTypes.toTypedArray()
            }
        } else null

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            val cursor: Cursor? = requireContext().contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                    MediaStore.Files.FileColumns.DATA
                ),
                finalSelection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dataCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol)
                    var mimeType = it.getString(mimeCol) ?: ""

                    // Detectar tipo real si MIME es genérico
                    if (mimeType.isEmpty() || mimeType == "application/octet-stream") {
                        val dataPath = if (dataCol >= 0) it.getString(dataCol) else null
                        mimeType = detectMimeType(name, dataPath)
                    }

                    val size = it.getLong(sizeCol)
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"), id
                    )
                    files.add(MediaItem(
                        uri = uri,
                        name = name,
                        mimeType = mimeType,
                        size = size
                    ))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DocumentFragment", "Error cargando archivos: ${e.message}")
        }
    }

    /** Detectar MIME type basado en la extensión del archivo */
    private fun detectMimeType(fileName: String, filePath: String? = null): String {
        val name = (filePath ?: fileName)
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt", "log", "csv" -> "text/plain"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "apk" -> "application/vnd.android.package-archive"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "js" -> "application/javascript"
            "py" -> "text/x-python"
            "java" -> "text/x-java-source"
            "kt" -> "text/x-kotlin"
            "cpp", "c", "h" -> "text/x-c"
            "sql" -> "application/sql"
            "db" -> "application/x-sqlite3"
            "exe" -> "application/x-msdownload"
            "iso" -> "application/x-iso9660-image"
            else -> "application/octet-stream"
        }
    }

    /** Intentar adivinar MIME desde la URI si contentResolver no lo tiene */
    private fun guessMimeTypeFromUri(uri: Uri): String {
        val name = queryFileName(uri) ?: uri.lastPathSegment ?: ""
        return detectMimeType(name)
    }

    private fun queryFileName(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                } else null
            }
        } catch (_: Exception) { null }
    }

    override fun onResume() {
        super.onResume()
        if (files.isEmpty()) loadFiles()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class FilterType(val label: String, val mime: String)
}
