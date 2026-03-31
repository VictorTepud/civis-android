package com.civis.app.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.Message
import com.civis.app.data.model.SendMessageRequest
import com.civis.app.databinding.ActivityChatBinding
import com.civis.app.ui.calls.CallActivity
import com.civis.app.utils.SocketManager
import com.civis.app.utils.showToast
import com.civis.app.utils.visible
import com.civis.app.utils.gone
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()
    private var conversationId: String = ""
    private var receiverId: String = ""
    private var receiverName: String = ""
    private var receiverAvatar: String = ""
    private var replyingTo: Message? = null

    companion object {
        private const val PICK_IMAGE = 1001
        private const val PICK_FILE = 1002
        private const val CAMERA_REQUEST = 1003
        private const val PERMISSION_REQUEST = 1004
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        conversationId = intent.getStringExtra("conversationId") ?: ""
        receiverId = intent.getStringExtra("receiverId") ?: ""
        receiverName = intent.getStringExtra("receiverName") ?: "Chat"
        receiverAvatar = intent.getStringExtra("receiverAvatar") ?: ""

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        loadMessages()
        setupSocketListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.title = receiverName
        binding.tvOnlineStatus.text = "en línea"
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_voice_call -> {
                    startCall("voice")
                    true
                }
                R.id.action_video_call -> {
                    startCall("video")
                    true
                }
                R.id.action_contact_info -> {
                    showToast("Info de contacto")
                    true
                }
                else -> false
            }
        }
    }

    private fun startCall(type: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("receiverId", receiverId)
            putExtra("receiverName", receiverName)
            putExtra("receiverAvatar", receiverAvatar)
            putExtra("callType", type)
        }
        startActivity(intent)
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            onMessageLongClick = { message, view ->
                showMessageOptions(message, view)
            }
        )
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerViewMessages.adapter = adapter
    }

    private fun showMessageOptions(message: Message, view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_message_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reply -> {
                    replyingTo = message
                    binding.replyPreview.root.visible()
                    binding.replyPreview.tvReplyText.text = message.content ?: "Multimedia"
                    binding.replyPreview.tvReplyName.text = "Respondiendo a ${message.sender?.name ?: "Desconocido"}"
                    binding.etMessage.requestFocus()
                    true
                }
                R.id.action_forward -> {
                    showToast("Reenviar mensaje")
                    true
                }
                R.id.action_delete -> {
                    deleteMessage(message.id)
                    true
                }
                R.id.action_copy -> {
                    message.content?.let {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Mensaje", it))
                        showToast("Mensaje copiado")
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.ivAttach.setOnClickListener {
            showAttachmentOptions()
        }

        binding.replyPreview.btnCloseReply.setOnClickListener {
            replyingTo = null
            binding.replyPreview.root.gone()
        }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                SocketManager.emit("typing", JSONObject().apply {
                    put("receiverId", receiverId)
                    put("conversationId", conversationId)
                })
                binding.btnSend.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                binding.btnRecord.visibility = if (s.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun showAttachmentOptions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST
            )
            return
        }

        val options = arrayOf("Cámara", "Galería", "Documento", "Ubicación")
        android.app.AlertDialog.Builder(this)
            .setTitle("Adjuntar")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                    2 -> openFilePicker()
                    3 -> showToast("Ubicación no disponible aún")
                }
            }
            .show()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_REQUEST
            )
            return
        }
        val cameraIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, CAMERA_REQUEST)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                PICK_IMAGE -> {
                    val imageUri: Uri? = data.data
                    imageUri?.let { uploadAndSendMedia(it, "image") }
                }
                PICK_FILE -> {
                    val fileUri: Uri? = data.data
                    fileUri?.let { uploadAndSendMedia(it, "document") }
                }
                CAMERA_REQUEST -> {
                    val photo = data.extras?.get("data") as? android.graphics.Bitmap
                    photo?.let { showToast("Imagen capturada") }
                }
            }
        }
    }

    private fun uploadAndSendMedia(uri: Uri, type: String) {
        showToast("Subiendo medio...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = java.io.File(uri.path ?: return@launch)
                val requestFile = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse(contentResolver.getType(uri) ?: "image/*"),
                    file
                )
                val body = okhttp3.MultipartBody.Part.createFormData("file", file.name, requestFile)
                val response = ApiClient.uploadApi.uploadMedia(body)
                if (response.isSuccessful) {
                    val mediaUrl = response.body()?.data?.toString()
                    if (mediaUrl != null) {
                        sendMediaMessage(mediaUrl, type)
                    }
                } else {
                    withContext(Dispatchers.Main) { showToast("Error al subir") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error: ${e.message}") }
            }
        }
    }

    private fun sendMediaMessage(mediaUrl: String, type: String) {
        val request = SendMessageRequest(
            receiverId = receiverId,
            content = null,
            messageType = type,
            mediaUrl = mediaUrl
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.messagesApi.sendMessage(request)
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val msg = Gson().fromJson(Gson().toJson(data), Message::class.java)
                        withContext(Dispatchers.Main) {
                            messages.add(msg)
                            adapter.notifyItemInserted(messages.size - 1)
                            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error al enviar") }
            }
        }
    }

    private fun sendMessage() {
        val content = binding.etMessage.text.toString().trim()
        if (content.isEmpty()) return

        binding.etMessage.text.clear()

        val request = SendMessageRequest(
            receiverId = receiverId,
            content = content,
            messageType = "text",
            replyTo = replyingTo?.id
        )

        replyingTo = null
        binding.replyPreview.root.gone()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.messagesApi.sendMessage(request)
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val msg = Gson().fromJson(Gson().toJson(data), Message::class.java)
                        withContext(Dispatchers.Main) {
                            messages.add(msg)
                            adapter.notifyItemInserted(messages.size - 1)
                            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) { showToast("Error al enviar mensaje") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error de conexión") }
            }
        }
    }

    private fun loadMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.messagesApi.getMessages(conversationId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Message>>() {}.type
                            val list: List<Message> = Gson().fromJson(Gson().toJson(data), type)
                            messages.clear()
                            messages.addAll(list)
                            adapter.submitList(messages)
                            if (messages.isNotEmpty()) {
                                binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                            }
                            markMessagesAsRead()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error al cargar mensajes") }
            }
        }
    }

    private fun markMessagesAsRead() {
        val unread = messages.filter { !it.read }
        if (unread.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                unread.forEach { msg ->
                    ApiClient.messagesApi.markRead(msg.id)
                    SocketManager.emit("message_read", JSONObject().apply {
                        put("messageId", msg.id)
                        put("conversationId", conversationId)
                    })
                }
            } catch (_: Exception) {}
        }
    }

    private fun deleteMessage(messageId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.messagesApi.deleteMessage(messageId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val index = messages.indexOfFirst { it.id == messageId }
                        if (index >= 0) {
                            messages.removeAt(index)
                            adapter.notifyItemRemoved(index)
                        }
                    } else {
                        showToast("Error al eliminar mensaje")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error de conexión") }
            }
        }
    }

    private fun setupSocketListeners() {
        SocketManager.on("new_message") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val message = Gson().fromJson(data.toString(), Message::class.java)
            if (message.conversationId == conversationId || message.senderId == receiverId) {
                runOnUiThread {
                    val exists = messages.any { it.id == message.id }
                    if (!exists) {
                        messages.add(message)
                        adapter.notifyItemInserted(messages.size - 1)
                        binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                        markMessagesAsRead()
                    }
                }
            }
        }

        SocketManager.on("user_typing") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val typingUserId = data.optString("userId", "")
            if (typingUserId == receiverId) {
                runOnUiThread {
                    binding.tvTypingIndicator.visible()
                }
            }
        }

        SocketManager.on("stop_typing") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val typingUserId = data.optString("userId", "")
            if (typingUserId == receiverId) {
                runOnUiThread {
                    binding.tvTypingIndicator.gone()
                }
            }
        }

        SocketManager.on("message_read") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val messageId = data.optString("messageId", "")
            runOnUiThread {
                val index = messages.indexOfFirst { it.id == messageId }
                if (index >= 0) {
                    messages[index] = messages[index].copy(read = true)
                    adapter.notifyItemChanged(index)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SocketManager.off("new_message")
        SocketManager.off("user_typing")
        SocketManager.off("stop_typing")
        SocketManager.off("message_read")
    }
}
