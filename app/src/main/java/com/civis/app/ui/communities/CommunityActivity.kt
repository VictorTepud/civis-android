package com.civis.app.ui.communities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.Community
import com.civis.app.data.model.Channel
import com.civis.app.databinding.ActivityCommunityBinding
import com.civis.app.utils.showToast
import com.civis.app.utils.toGlideUrl
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommunityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommunityBinding
    private var communityId: String = ""
    private var community: Community? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        communityId = intent.getStringExtra("communityId") ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadCommunity()
    }

    private fun loadCommunity() {
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.communitiesApi.getCommunity(communityId)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            community = Gson().fromJson(Gson().toJson(data), Community::class.java)
                            updateUI()
                        }
                    } else {
                        showToast("Error al cargar comunidad")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showToast("Error de conexión")
                }
            }
        }
    }

    private fun updateUI() {
        val comm = community ?: return
        binding.toolbar.title = comm.name
        binding.tvCommunityDesc.text = comm.description ?: "Sin descripción"
        binding.tvMemberCount.text = "${comm.memberCount} miembros"

        if (!comm.cover.isNullOrEmpty()) {
            Glide.with(this)
                .load(comm.cover.toGlideUrl())
                .placeholder(com.civis.app.R.drawable.ic_community)
                .into(binding.ivCover)
        } else if (!comm.avatar.isNullOrEmpty()) {
            Glide.with(this)
                .load(comm.avatar.toGlideUrl())
                .placeholder(com.civis.app.R.drawable.ic_community)
                .into(binding.ivCover)
        }

        binding.recyclerViewChannels.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewChannels.adapter = ChannelAdapter(
            onItemClick = { channel ->
                val intent = Intent(this, ChannelChatActivity::class.java).apply {
                    putExtra("communityId", communityId)
                    putExtra("channelId", channel.id)
                    putExtra("channelName", channel.name)
                    putExtra("channelType", channel.type)
                }
                startActivity(intent)
            }
        ).apply {
            submitList(comm.channels)
        }
    }
}

class ChannelAdapter(
    private val onItemClick: (Channel) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Channel, ChannelAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Channel>() {
        override fun areItemsTheSame(a: Channel, b: Channel) = a.id == b.id
        override fun areContentsTheSame(a: Channel, b: Channel) = a == b
    }
) {
    inner class ViewHolder(val binding: com.civis.app.databinding.ItemChannelBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val binding = com.civis.app.databinding.ItemChannelBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = getItem(position)
        with(holder.binding) {
            tvChannelName.text = channel.name
            tvChannelDescription.text = channel.description ?: ""
            tvChannelType.text = when (channel.type) {
                "announcement" -> "📢 Anuncio"
                "voice" -> "🎤 Voz"
                "media" -> "📁 Multimedia"
                else -> "💬 Texto"
            }
            root.setOnClickListener { onItemClick(channel) }
        }
    }
}
