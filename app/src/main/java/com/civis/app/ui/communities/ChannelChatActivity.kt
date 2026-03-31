package com.civis.app.ui.communities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.Message
import com.civis.app.data.model.SendMessageRequest
import com.civis.app.databinding.ActivityChannelChatBinding
import com.civis.app.utils.SocketManager
import com.civis.app.utils.showToast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ChannelChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChannelChatBinding
    private val messages = mutableListOf<Message>()
    private var communityId: String = ""
    private var channelId: String = ""
    private var channelName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChannelChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        communityId = intent.getStringExtra("communityId") ?: ""
        channelId = intent.getStringExtra("channelId") ?: ""
        channelName = intent.getStringExtra("channelName") ?: "Canal"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "# $channelName"
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        setupListeners()
        loadMessages()
        setupSocketListeners()
    }

    private fun setupRecyclerView() {
        val adapter = com.civis.app.ui.chat.ChatAdapter(
            onMessageLongClick = { _, _ -> }
        )
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerViewMessages.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            sendChannelMessage()
        }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendChannelMessage()
                true
            } else {
                false
            }
        }
    }

    private fun sendChannelMessage() {
        val content = binding.etMessage.text.toString().trim()
        if (content.isEmpty()) return
        binding.etMessage.text.clear()

        val request = SendMessageRequest(
            content = content,
            messageType = "text"
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.communitiesApi.sendChannelMessage(communityId, channelId, request)
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val msg = Gson().fromJson(Gson().toJson(data), Message::class.java)
                        withContext(Dispatchers.Main) {
                            messages.add(msg)
                            (binding.recyclerViewMessages.adapter as? com.civis.app.ui.chat.ChatAdapter)?.submitList(messages)
                            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error al enviar") }
            }
        }
    }

    private fun loadMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.communitiesApi.getChannelMessages(communityId, channelId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Message>>() {}.type
                            val list: List<Message> = Gson().fromJson(Gson().toJson(data), type)
                            messages.clear()
                            messages.addAll(list)
                            (binding.recyclerViewMessages.adapter as? com.civis.app.ui.chat.ChatAdapter)?.submitList(messages)
                            if (messages.isNotEmpty()) {
                                binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun setupSocketListeners() {
        SocketManager.on("channel_message") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val message = Gson().fromJson(data.toString(), Message::class.java)
            runOnUiThread {
                messages.add(message)
                (binding.recyclerViewMessages.adapter as? com.civis.app.ui.chat.ChatAdapter)?.submitList(messages)
                binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.off("channel_message")
    }
}
