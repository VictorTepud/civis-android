package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.R
import com.civis.app.data.local.LocalConversation
import com.civis.app.data.model.Conversation
import com.civis.app.databinding.FragmentChatsBinding
import com.civis.app.ui.chat.ChatActivity
import com.civis.app.ui.contacts.AddContactActivity
import com.civis.app.ui.groups.CreateGroupActivity
import com.civis.app.ui.profile.ProfileActivity
import com.civis.app.ui.search.SearchActivity
import com.civis.app.utils.NetworkMonitor
import com.civis.app.utils.OfflineSyncManager
import com.civis.app.utils.TokenManager
import com.civis.app.utils.appGson
import com.civis.app.utils.showToast
import com.civis.app.config.ServerConfig
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()
    private var hasLoadedOnce = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener(null)
        binding.toolbar.title = "Civis"
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    startActivity(Intent(requireContext(), SearchActivity::class.java))
                    true
                }
                R.id.action_profile -> {
                    startActivity(Intent(requireContext(), ProfileActivity::class.java))
                    true
                }
                R.id.action_new_group -> {
                    startActivity(Intent(requireContext(), CreateGroupActivity::class.java))
                    true
                }
                else -> false
            }
        }

        setupRecyclerView()
        setupFab()
        loadConversations()

        // Cuando vuelve la red, recargar
        viewLifecycleOwner.lifecycleScope.launch {
            NetworkMonitor.isConnected.collect { connected ->
                if (connected && !binding.swipeRefreshLayout.isRefreshing) {
                    loadConversations()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(
            onItemClick = { conversation ->
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("conversationId", conversation.id)
                    val otherUser = conversation.otherUser
                    val participant = conversation.participants.firstOrNull()
                    putExtra("receiverId", otherUser?.id ?: participant?.id ?: "")
                    putExtra("receiverName", otherUser?.name ?: conversation.name ?: participant?.name ?: "")
                    putExtra("receiverAvatar", otherUser?.avatar ?: participant?.avatar ?: "")
                }
                startActivity(intent)
            },
            onLongClick = { conversation ->
                val name = conversation.otherUser?.name ?: conversation.name
                name?.let { requireContext().showToast("$it: Silenciar, Bloquear, Eliminar") }
            }
        )
        binding.recyclerViewChats.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewChats.adapter = adapter
    }

    private fun setupFab() {
        binding.fabNewChat.setOnClickListener {
            startActivity(Intent(requireContext(), AddContactActivity::class.java))
        }
    }

    private fun loadConversations() {
        val token = TokenManager.getInstance().getToken() ?: ""
        if (token.isEmpty()) {
            binding.swipeRefreshLayout.isRefreshing = false
            loadLocalConversations()
            return
        }

        binding.swipeRefreshLayout.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("${ServerConfig.API_URL}messages/conversations")
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }

                if (response.isSuccessful) {
                    val jsonObj = org.json.JSONObject(responseBody)
                    val data = jsonObj.optJSONArray("data")

                    if (data != null) {
                        val type = object : TypeToken<List<Conversation>>() {}.type
                        val list: List<Conversation> = appGson.fromJson(data.toString(), type)

                        // Cachear localmente
                        val localConvs = list.map { LocalConversation.fromConversation(it) }
                        OfflineSyncManager.db?.insertConversations(localConvs)

                        conversations.clear()
                        conversations.addAll(list)
                        hasLoadedOnce = true
                        withContext(Dispatchers.Main) {
                            adapter.submitList(conversations.toList())
                        }
                    } else if (!hasLoadedOnce) {
                        loadLocalConversations()
                    }
                } else if (!hasLoadedOnce) {
                    loadLocalConversations()
                }
            } catch (e: Exception) {
                android.util.Log.d("ChatsFragment", "No se pudo cargar del servidor: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
                loadLocalConversations()
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadConversations()
        }
    }

    private fun loadLocalConversations() {
        CoroutineScope(Dispatchers.IO).launch {
            val localConvs = OfflineSyncManager.db?.getConversations() ?: emptyList()
            val convList = localConvs.map { it.toConversation() }

            withContext(Dispatchers.Main) {
                if (convList.isNotEmpty()) {
                    conversations.clear()
                    conversations.addAll(convList)
                    adapter.submitList(conversations.toList())
                } else {
                    adapter.submitList(emptyList())
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
