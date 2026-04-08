package com.civis.app.ui.chat.attachments

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.databinding.FragmentAttachmentGalleryBinding

/**
 * Fragmento que muestra la cámara en la primera posición y luego las fotos.
 */
class GalleryFragment : Fragment() {

    private var _binding: FragmentAttachmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val photos = mutableListOf<MediaItem>()
    private lateinit var adapter: MediaGridAdapter

    var onPhotoSelected: ((uri: android.net.Uri, mimeType: String) -> Unit)? = null
    var onCameraSelected: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAttachmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MediaGridAdapter(
            items = photos,
            type = MediaGridAdapter.Type.GALLERY,
            onItemClick = { item ->
                onPhotoSelected?.invoke(item.uri, item.mimeType)
            },
            onSelectionChanged = { },
            showCamera = true,
            onCameraClick = {
                onCameraSelected?.invoke()
            }
        )

        binding.recyclerViewGallery.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerViewGallery.adapter = adapter

        loadPhotos()
    }

    override fun onResume() {
        super.onResume()
        if (photos.isEmpty()) loadPhotos()
    }

    private fun loadPhotos() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "Se necesita permiso para acceder a las fotos"
                return
            }
        }

        photos.clear()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor: Cursor? = requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol)
                    val mimeType = it.getString(mimeCol)
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    photos.add(MediaItem(uri = uri, name = name, mimeType = mimeType))
                }
            }
        } catch (_: Exception) {}

        binding.progressBar.visibility = View.GONE
        if (photos.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.tvEmpty.visibility = View.GONE
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
