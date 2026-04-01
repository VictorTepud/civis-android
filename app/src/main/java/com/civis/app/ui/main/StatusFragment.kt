package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.Status
import com.civis.app.databinding.FragmentStatusBinding
import com.civis.app.ui.status.CreateStatusActivity
import com.civis.app.ui.status.ViewStatusActivity
import com.civis.app.utils.TokenManager
import com.civis.app.utils.showToast
import com.civis.app.utils.appGson
import com.civis.app.utils.toGlideUrl
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StatusAdapter
    private lateinit var recentAdapter: StatusRecentAdapter
    private val statuses = mutableListOf<Status>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMyStatus()
        setupRecyclerView()
        loadStatuses()

        binding.fabCreateStatus.setOnClickListener {
            startActivity(Intent(requireContext(), CreateStatusActivity::class.java))
        }

        binding.layoutMyStatus.setOnClickListener {
            startActivity(Intent(requireContext(), CreateStatusActivity::class.java))
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadStatuses()
        }
    }

    override fun onResume() {
        super.onResume()
        loadStatuses()
        setupMyStatus()
    }

    private fun setupMyStatus() {
        val user = TokenManager.getInstance().getUser()
        if (user != null) {
            Glide.with(this)
                .load(user.avatar?.toGlideUrl())
                .placeholder(R.drawable.ic_profile)
                .into(binding.ivMyStatusAvatar)
            binding.tvMyStatusTime.text = "Mi estado"
        }
    }

    private fun setupRecyclerView() {
        // Horizontal RecyclerView for other users' status circles
        adapter = StatusAdapter(
            onItemClick = { status, position ->
                // position in the adapter = index into statuses list
                val intent = Intent(requireContext(), ViewStatusActivity::class.java).apply {
                    putStringArrayListExtra("statusIds", ArrayList(statuses.map { it.id }))
                    putExtra("startIndex", position)
                }
                startActivity(intent)
            }
        )
        binding.recyclerViewStatuses.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerViewStatuses.adapter = adapter

        // Vertical RecyclerView for recent status updates (detailed list)
        recentAdapter = StatusRecentAdapter(
            onItemClick = { status, position ->
                val intent = Intent(requireContext(), ViewStatusActivity::class.java).apply {
                    putStringArrayListExtra("statusIds", ArrayList(statuses.map { it.id }))
                    putExtra("startIndex", position)
                }
                startActivity(intent)
            }
        )
        binding.recyclerViewRecentStatuses.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewRecentStatuses.adapter = recentAdapter
    }

    private fun loadStatuses() {
        binding.swipeRefreshLayout.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.statusApi.getStatuses()
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Status>>() {}.type
                            val list: List<Status> = appGson.fromJson(appGson.toJson(data), type)
                            statuses.clear()
                            statuses.addAll(list)
                            adapter.submitList(statuses)
                            recentAdapter.submitList(statuses)

                            if (statuses.isEmpty()) {
                                binding.layoutEmptyState.visibility = View.VISIBLE
                                binding.recyclerViewRecentStatuses.visibility = View.GONE
                            } else {
                                binding.layoutEmptyState.visibility = View.GONE
                                binding.recyclerViewRecentStatuses.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        requireContext().showToast("Error al cargar estados")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (statuses.isEmpty()) {
                        binding.layoutEmptyState.visibility = View.VISIBLE
                        binding.recyclerViewRecentStatuses.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adapter for the vertical "recent updates" RecyclerView.
 * Shows each status as a card with avatar, name, and time.
 */
class StatusRecentAdapter(
    private val onItemClick: (Status, Int) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Status, StatusRecentAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: com.civis.app.databinding.ItemStatusRecentBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Status>() {
        override fun areItemsTheSame(a: Status, b: Status): Boolean = a.id == b.id
        override fun areContentsTheSame(a: Status, b: Status): Boolean = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = com.civis.app.databinding.ItemStatusRecentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val status = getItem(position)
        with(holder.binding) {
            tvName.text = status.user?.name ?: "Desconocido"
            tvTime.text = status.createdAt?.formatStatusTime() ?: ""

            val previewText = when {
                !status.content.isNullOrEmpty() -> status.content
                !status.mediaUrl.isNullOrEmpty() -> "📸 Foto"
                else -> "Estado"
            }
            tvPreview.text = previewText

            if (!status.mediaUrl.isNullOrEmpty()) {
                Glide.with(root.context)
                    .load(status.mediaUrl.toGlideUrl())
                    .placeholder(R.drawable.ic_profile)
                    .into(ivAvatar)
            } else {
                Glide.with(root.context)
                    .load(status.user?.avatar?.toGlideUrl())
                    .placeholder(R.drawable.ic_profile)
                    .into(ivAvatar)
            }

            root.setOnClickListener { onItemClick(status, position) }
        }
    }
}

private fun String.formatStatusTime(): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(this.substring(0, minOf(this.length, 19)))
        if (date != null) {
            val now = Date()
            val diffMs = now.time - date.time
            val diffHours = diffMs / (1000 * 60 * 60)
            when {
                diffHours < 1 -> "Hace un momento"
                diffHours < 24 -> "Hoy, ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)}"
                diffHours < 48 -> "Ayer, ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)}"
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            }
        } else this
    } catch (e: Exception) { this }
}
