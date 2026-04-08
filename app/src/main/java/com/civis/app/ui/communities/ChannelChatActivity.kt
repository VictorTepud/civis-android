package com.civis.app.ui.communities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.data.api.ApiClient
import com.civis.app.data.local.LocalMessage
import com.civis.app.data.model.Message
import com.civis.app.data.model.SendMessageRequest
import com.civis.app.databinding.ActivityChannelChatBinding
import com.civis.app.utils.OfflineSyncManager
import com.civis.app.utils.SocketManager
import com.civis.app.utils.showToast
import com.civis.app.utils.appGson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ChannelChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChannelChatBinding
    private lateinit var adapter: com.civis.app.ui.chat.ChatAdapter
    private val messages = mutableListOf<LocalMessage>()
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

        binding.toolbar.title = channelName
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = com.civis.app.ui.chat.ChatAdapter(messages, onMessageLongClick = { _, _ -> })
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerViewMessages.adapter = adapter

        loadMessages()

        binding.btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun sendMessage() {
        val content = binding.etMessage.text.toString().trim()
        if (content.isEmpty()) return
        binding.etMessage.text?.clear()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.messagesApi.sendMessage(
                    SendMessageRequest(content = content, messageType = "text")
                )
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val msg = appGson.fromJson(appGson.toJson(data), Message::class.java)
                        val localMsg = OfflineSyncManager.toLocalMessage(msg, "sent")
                        withContext(Dispatchers.Main) {
                            messages.add(localMsg)
                            adapter.refresh()
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
                // Los canales usan un endpoint diferente, por ahora mostramos vacío
                withContext(Dispatchers.Main) {
                    adapter.refresh()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error al cargar mensajes") }
            }
        }
    }
}
