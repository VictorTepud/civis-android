package com.civis.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.data.local.LocalMessage
import com.civis.app.data.model.Message
import com.civis.app.data.model.SendMessageRequest
import com.civis.app.databinding.ActivityChatBinding
import com.civis.app.ui.calls.CallActivity
import com.civis.app.utils.cameraPermissions
import com.civis.app.utils.filePermissions
import com.civis.app.utils.hasFilePermission
import com.civis.app.utils.hasImagePermission
import com.civis.app.utils.hasVideoPermission
import com.civis.app.utils.imagePermissions
import com.civis.app.utils.videoPermissions
import com.civis.app.utils.NetworkMonitor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import com.civis.app.utils.OfflineSyncManager
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
import com.civis.app.utils.showToast
import com.civis.app.utils.visible
import com.civis.app.utils.gone
import com.civis.app.utils.appGson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<LocalMessage>()
    private var conversationId: String = ""
    private var receiverId: String = ""
    private var receiverName: String = ""
    private var receiverAvatar: String = ""
    private var replyingTo: Message? = null
    private val PERMISSION_REQUEST = 1005

    // Activity Result APIs (modern, no deprecated startActivityForResult)
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadAndSendMedia(it, "image") }
    }

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadAndSendMedia(it, "video") }
    }

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadAndSendMedia(it, "document") }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val photo = result.data?.extras?.get("data") as? android.graphics.Bitmap
            if (photo != null) {
                val file = java.io.File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { photo.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
                uploadFile(file, "image/jpeg", "image")
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val allGranted = grants.values.all { it }
        if (!allGranted) showToast("Permiso denegado. No se puede acceder a los archivos.")
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
                R.id.action_voice_call -> { startCall("voice"); true }
                R.id.action_video_call -> { startCall("video"); true }
                R.id.action_contact_info -> { showToast("Info de contacto"); true }
                else -> false
            }
        }
    }

    private fun startCall(type: String) {
        startActivity(Intent(this, CallActivity::class.java).apply {
            putExtra("receiverId", receiverId)
            putExtra("receiverName", receiverName)
            putExtra("receiverAvatar", receiverAvatar)
            putExtra("callType", type)
        })
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(messages) { message, view ->
            showMessageOptions(message, view)
        }
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerViewMessages.adapter = adapter
    }

    /** Agrega un mensaje a la lista y refresca el adapter */
    private fun addMessage(msg: LocalMessage) {
        messages.add(msg)
        adapter.notifyItemInserted(messages.size - 1)
        binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
    }

    /** Reemplaza toda la lista y refresca */
    private fun setMessages(list: List<LocalMessage>) {
        messages.clear()
        messages.addAll(list)
        adapter.refresh()
        if (messages.isNotEmpty()) {
            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
        }
    }

    private fun showMessageOptions(message: LocalMessage, view: View) {
        val msg = messageToMessage(message)
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_message_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_reply -> {
                    replyingTo = msg
                    binding.replyPreview.root.visible()
                    binding.replyPreview.tvReplyText.text = message.content ?: "Multimedia"
                    binding.replyPreview.tvReplyName.text = "Respondiendo a ${message.senderName ?: "Desconocido"}"
                    binding.etMessage.requestFocus()
                    true
                }
                R.id.action_forward -> { showToast("Reenviar mensaje"); true }
                R.id.action_delete -> { deleteMessage(message.id); true }
                R.id.action_copy -> {
                    message.content?.let {
                        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("Mensaje", it))
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
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.ivAttach.setOnClickListener { showAttachmentOptions() }

        binding.replyPreview.btnCloseReply.setOnClickListener {
            replyingTo = null
            binding.replyPreview.root.gone()
        }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage(); true
            } else false
        }

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                SocketManager.emit("typing", JSONObject().apply {
                    put("targetId", receiverId)
                    put("conversationId", conversationId)
                })
                binding.btnSend.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                binding.btnRecord.visibility = if (s.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun showAttachmentOptions() {
        val options = arrayOf("Cámara", "Galería", "Video", "Documento")
        android.app.AlertDialog.Builder(this)
            .setTitle("Adjuntar")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                    2 -> openVideoPicker()
                    3 -> openFilePicker()
                }
            }
            .show()
    }

    private fun openCamera() {
        val perms = cameraPermissions()
        if (perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            cameraLauncher.launch(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
        } else {
            permissionLauncher.launch(perms)
        }
    }

    private fun openGallery() {
        val perms = imagePermissions()
        if (hasImagePermission()) {
            pickImageLauncher.launch("image/*")
        } else {
            permissionLauncher.launch(perms)
        }
    }

    private fun openVideoPicker() {
        val perms = videoPermissions()
        if (hasVideoPermission()) {
            pickVideoLauncher.launch("video/*")
        } else {
            permissionLauncher.launch(perms)
        }
    }

    private fun openFilePicker() {
        val perms = filePermissions()
        if (hasFilePermission()) {
            pickFileLauncher.launch("*/*")
        } else {
            permissionLauncher.launch(perms)
        }
    }

    private fun uploadAndSendMedia(uri: Uri, type: String) {
        showToast("Subiendo...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val file = java.io.File(cacheDir, "upload_${System.currentTimeMillis()}")
                file.outputStream().use { out -> inputStream.copyTo(out) }
                inputStream.close()
                val mimeType = contentResolver.getType(uri) ?: when (type) {
                    "video" -> "video/mp4"
                    else -> "image/jpeg"
                }
                uploadFile(file, mimeType, type)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error al subir: ${e.message}") }
            }
        }
    }

    private fun uploadFile(file: java.io.File, mimeType: String, type: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mediaType = mimeType.toMediaType()
                val requestFile = file.asRequestBody(mediaType)
                val body = okhttp3.MultipartBody.Part.createFormData("media", file.name, requestFile)
                val response = ApiClient.uploadApi.uploadMedia(body)
                if (response.isSuccessful) {
                    val responseData = response.body()?.data
                    val dataMap = responseData as? Map<*, *>
                    val mediaUrl = dataMap?.get("url") as? String
                    if (mediaUrl != null) {
                        sendMediaMessage(mediaUrl, type)
                    } else {
                        withContext(Dispatchers.Main) { showToast("Error: respuesta vacía del servidor") }
                    }
                } else {
                    withContext(Dispatchers.Main) { showToast("Error al subir archivo") }
                }
                file.delete()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!NetworkMonitor.isConnected.value) {
                        showToast("Sin conexión. No se puede subir el archivo.")
                    } else {
                        showToast("Error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun sendMediaMessage(mediaUrl: String, type: String) {
        sendMessageToServer(SendMessageRequest(
            receiverId = receiverId,
            content = null,
            messageType = type,
            mediaUrl = mediaUrl
        ))
    }

    private fun sendMessage() {
        val content = binding.etMessage.text.toString().trim()
        if (content.isEmpty()) return
        binding.etMessage.text?.clear()

        val request = SendMessageRequest(
            receiverId = receiverId,
            content = content,
            messageType = "text",
            replyTo = replyingTo?.id
        )
        replyingTo = null
        binding.replyPreview.root.gone()
        sendMessageToServer(request)
    }

    /**
     * Envía un mensaje al servidor. Lo muestra inmediatamente en la UI.
     * Si no hay conexión, lo guarda como pendiente.
     */
    private fun sendMessageToServer(request: SendMessageRequest) {
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""
        val tempId = UUID.randomUUID().toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        // Crear mensaje local optimista
        val localMsg = LocalMessage(
            id = tempId,
            conversationId = conversationId,
            senderId = currentUserId,
            receiverId = request.receiverId,
            content = request.content,
            messageType = request.messageType,
            mediaUrl = request.mediaUrl,
            replyTo = request.replyTo,
            createdAt = timestamp,
            senderName = TokenManager.getInstance().getUser()?.name,
            senderAvatar = TokenManager.getInstance().getUser()?.avatar,
            status = if (NetworkMonitor.isServerReachable.value) "sending" else "pending"
        )

        // Mostrar inmediatamente
        addMessage(localMsg)

        // Guardar en SQLite local
        CoroutineScope(Dispatchers.IO).launch {
            OfflineSyncManager.db?.insertMessage(localMsg)

            // Intentar enviar al servidor
            val result = OfflineSyncManager.sendOrQueueMessage(request)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    // Reemplazar temporal con respuesta del servidor
                    val index = messages.indexOfFirst { it.id == tempId }
                    if (index >= 0) {
                        val serverMsg = OfflineSyncManager.toLocalMessage(result, "sent")
                        messages[index] = serverMsg
                        adapter.notifyItemChanged(index)

                        if (conversationId.isEmpty()) {
                            conversationId = result.conversationId
                        }
                    }
                } else if (!NetworkMonitor.isServerReachable.value) {
                    // Marcar como pendiente
                    val index = messages.indexOfFirst { it.id == tempId }
                    if (index >= 0) {
                        messages[index] = messages[index].copy(status = "pending")
                        adapter.notifyItemChanged(index)
                    }
                    showToast("Sin conexión. Se enviará automáticamente.")
                } else {
                    val index = messages.indexOfFirst { it.id == tempId }
                    if (index >= 0) {
                        messages[index] = messages[index].copy(status = "failed")
                        adapter.notifyItemChanged(index)
                    }
                }
            }
        }
    }

    /**
     * Carga mensajes: locales primero, luego sincroniza con servidor si hay conexión.
     */
    private fun loadMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Cargar de SQLite local (inmediato)
                val localMessages = if (conversationId.isNotEmpty()) {
                    OfflineSyncManager.getMessages(conversationId)
                } else emptyList()

                withContext(Dispatchers.Main) {
                    setMessages(localMessages)
                }

                // 2. Sincronizar con servidor si hay conexión
                if (NetworkMonitor.isServerReachable.value && conversationId.isNotEmpty()) {
                    try {
                        val response = ApiClient.messagesApi.getMessages(conversationId)
                        if (response.isSuccessful) {
                            val data = response.body()?.data
                            if (data != null) {
                                val type = object : TypeToken<List<Message>>() {}.type
                                val serverMessages: List<Message> = appGson.fromJson(appGson.toJson(data), type)

                                val pending = OfflineSyncManager.db?.getPendingMessages() ?: emptyList()
                                val serverIds = serverMessages.map { it.id }.toSet()
                                val localToKeep = pending.filter { it.id !in serverIds }

                                val allMessages = (serverMessages.map {
                                    OfflineSyncManager.toLocalMessage(it, "sent")
                                } + localToKeep).sortedBy { it.createdAt }

                                withContext(Dispatchers.Main) {
                                    setMessages(allMessages)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("ChatActivity", "No se pudo sincronizar: ${e.message}")
                    }
                }

                markMessagesAsRead()
            } catch (e: Exception) {
                android.util.Log.d("ChatActivity", "Error cargando: ${e.message}")
            }
        }
    }

    private fun markMessagesAsRead() {
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""
        if (conversationId.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                OfflineSyncManager.markAllAsReadLocal(conversationId, currentUserId)
                val unread = messages.filter { !it.read && it.senderId != currentUserId }
                if (NetworkMonitor.isServerReachable.value && unread.isNotEmpty()) {
                    unread.forEach { msg ->
                        try { ApiClient.messagesApi.markRead(msg.id) } catch (_: Exception) {}
                    }
                    SocketManager.emit("message_read", JSONObject().apply {
                        put("messageId", unread.first().id)
                        put("senderId", currentUserId)
                    })
                }
            } catch (_: Exception) {}
        }
    }

    private fun deleteMessage(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (NetworkMonitor.isServerReachable.value) {
                    try {
                        val response = ApiClient.messagesApi.deleteMessage(messageId)
                        if (response.isSuccessful) {
                            OfflineSyncManager.db?.softDelete(messageId)
                            withContext(Dispatchers.Main) {
                                messages.removeAt(index)
                                adapter.notifyItemRemoved(index)
                            }
                            return@launch
                        }
                    } catch (_: Exception) {}
                }
                // Sin conexión o error — eliminar solo localmente
                OfflineSyncManager.db?.softDelete(messageId)
                withContext(Dispatchers.Main) {
                    messages.removeAt(index)
                    adapter.notifyItemRemoved(index)
                }
            } catch (_: Exception) {}
        }
    }

    private fun setupSocketListeners() {
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""

        SocketManager.on("message_$currentUserId") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            try {
                val message = appGson.fromJson(data.toString(), Message::class.java)
                if (message.conversationId == conversationId || message.senderId == receiverId) {
                    runOnUiThread {
                        CoroutineScope(Dispatchers.IO).launch {
                            OfflineSyncManager.saveReceivedMessage(message)
                        }

                        val localMsg = LocalMessage(
                            id = message.id,
                            conversationId = message.conversationId,
                            senderId = message.senderId,
                            receiverId = message.receiverId,
                            content = message.content,
                            messageType = message.messageType,
                            mediaUrl = message.mediaUrl,
                            replyTo = message.replyTo,
                            forwarded = message.forwarded,
                            read = message.read,
                            createdAt = message.createdAt,
                            senderName = message.sender?.name,
                            senderAvatar = message.sender?.avatar,
                            status = "sent"
                        )

                        if (messages.none { it.id == message.id }) {
                            addMessage(localMsg)
                            if (conversationId.isEmpty()) {
                                conversationId = message.conversationId
                            }
                        }
                        markMessagesAsRead()
                    }
                }
            } catch (_: Exception) {}
        }

        SocketManager.on("typing_$currentUserId") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            if (data.optString("userId", "") == receiverId) {
                runOnUiThread { binding.tvTypingIndicator.visible() }
            }
        }

        SocketManager.on("stop_typing_$currentUserId") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            if (data.optString("userId", "") == receiverId) {
                runOnUiThread { binding.tvTypingIndicator.gone() }
            }
        }

        SocketManager.on("message_read_$currentUserId") { args ->
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

    private fun messageToMessage(localMsg: LocalMessage) = Message(
        id = localMsg.id,
        conversationId = localMsg.conversationId,
        senderId = localMsg.senderId,
        receiverId = localMsg.receiverId,
        content = localMsg.content,
        messageType = localMsg.messageType,
        mediaUrl = localMsg.mediaUrl,
        replyTo = localMsg.replyTo,
        forwarded = localMsg.forwarded,
        read = localMsg.read,
        deleted = localMsg.deleted,
        createdAt = localMsg.createdAt,
        sender = com.civis.app.data.model.User(name = localMsg.senderName ?: "", avatar = localMsg.senderAvatar)
    )

    override fun onDestroy() {
        super.onDestroy()
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""
        SocketManager.off("message_$currentUserId")
        SocketManager.off("typing_$currentUserId")
        SocketManager.off("stop_typing_$currentUserId")
        SocketManager.off("message_read_$currentUserId")
    }
}
