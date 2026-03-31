package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.Conversation
import com.civis.app.databinding.FragmentChatsBinding
import com.civis.app.ui.chat.ChatActivity
import com.civis.app.ui.contacts.AddContactActivity
import com.civis.app.utils.NetworkMonitor
import com.civis.app.utils.showToast
import com.google.gson.Gson
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        loadConversations()
    }

    /**
     * Se ejecuta cada vez que el fragment vuelve a ser visible.
     * Esto permite actualizar la lista de chats después de enviar
     * un mensaje o volver de una conversación.
     */
    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(
            onItemClick = { conversation ->
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("conversationId", conversation.id)
                    // Soporta tanto otherUser como participants para compatibilidad
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
            val intent = Intent(requireContext(), AddContactActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadConversations() {
        if (!NetworkMonitor.isConnected.value) {
            // Sin conexión - mostrar advertencia
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        binding.swipeRefreshLayout.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.messagesApi.getConversations()
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Conversation>>() {}.type
                            val list: List<Conversation> = Gson().fromJson(Gson().toJson(data), type)
                            conversations.clear()
                            conversations.addAll(list)
                            adapter.submitList(conversations.toList())
                        }
                    } else {
                        requireContext().showToast("Error al cargar conversaciones")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    requireContext().showToast("Error de conexión")
                }
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadConversations()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
