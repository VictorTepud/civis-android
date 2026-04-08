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
import com.civis.app.databinding.FragmentAttachmentVideoBinding

class VideoFragment : Fragment() {

    private var _binding: FragmentAttachmentVideoBinding? = null
    private val binding get() = _binding!!

    private val videos = mutableListOf<MediaItem>()
    private lateinit var adapter: MediaGridAdapter

    var onVideoSelected: ((uri: android.net.Uri, mimeType: String) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAttachmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MediaGridAdapter(
            items = videos,
            type = MediaGridAdapter.Type.VIDEO,
            onItemClick = { item ->
                onVideoSelected?.invoke(item.uri, item.mimeType)
            },
            onSelectionChanged = { }
        )

        binding.recyclerViewVideo.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerViewVideo.adapter = adapter

        loadVideos()
    }

    override fun onResume() {
        super.onResume()
        if (videos.isEmpty()) loadVideos()
    }

    private fun loadVideos() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "Se necesita permiso para acceder a los videos"
                return
            }
        }

        videos.clear()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            val cursor: Cursor? = requireContext().contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol)
                    val mimeType = it.getString(mimeCol)
                    val duration = it.getLong(durCol)
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                    videos.add(MediaItem(
                        uri = uri,
                        name = name,
                        mimeType = mimeType,
                        duration = duration
                    ))
                }
            }
        } catch (_: Exception) {}

        binding.progressBar.visibility = View.GONE
        if (videos.isEmpty()) {
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
