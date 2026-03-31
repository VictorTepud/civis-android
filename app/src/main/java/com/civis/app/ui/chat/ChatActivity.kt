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
import com.civis.app.data.local.LocalMessage
import com.civis.app.data.model.Message
import com.civis.app.data.model.SendMessageRequest
import com.civis.app.databinding.ActivityChatBinding
import com.civis.app.ui.calls.CallActivity
import com.civis.app.utils.NetworkMonitor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import com.civis.app.utils.OfflineSyncManager
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
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
                val inputStream = contentResolver.openInputStream(uri) ?: return@launch
                val file = java.io.File(cacheDir, "upload_${System.currentTimeMillis()}")
                file.outputStream().use { out ->
                    inputStream.copyTo(out)
                }
                inputStream.close()

                val mediaTypeStr = contentResolver.getType(uri) ?: "image/*"
                val mediaType = mediaTypeStr.toMediaType()
                val requestFile = file.asRequestBody(mediaType)
                val body = okhttp3.MultipartBody.Part.createFormData("file", file.name, requestFile)
                val response = ApiClient.uploadApi.uploadMedia(body)
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val mediaUrl = Gson().toJson(data).trim('"')
                        sendMediaMessage(mediaUrl, type)
                    }
                } else {
                    withContext(Dispatchers.Main) { showToast("Error al subir") }
                }
                file.delete()
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
        sendMessageToServer(request)
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
     * Envía un mensaje al servidor. Si no hay conexión,
     * lo guarda localmente como pendiente y se enviará
     * automáticamente cuando se restaure la conexión.
     */
    private fun sendMessageToServer(request: SendMessageRequest) {
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""
        val tempId = UUID.randomUUID().toString()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        // Crear mensaje local optimista (se muestra inmediatamente)
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
            status = NetworkMonitor.isConnected.value.let { if (it) "sending" else "pending" }
        )

        // Mostrar inmediatamente en la UI
        runOnUiThread {
            messages.add(localMsg)
            adapter.notifyItemInserted(messages.size - 1)
            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
        }

        // Enviar en background
        CoroutineScope(Dispatchers.IO).launch {
            val result = OfflineSyncManager.sendOrQueueMessage(request)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    // El mensaje se envió correctamente, reemplazar el temporal con el del servidor
                    val index = messages.indexOfFirst { it.id == tempId }
                    if (index >= 0) {
                        messages.removeAt(index)
                        val serverLocal = OfflineSyncManager.toLocalMessage(result, "sent")
                        messages.add(serverLocal)
                        adapter.notifyDataSetChanged()
                        binding.recyclerViewMessages.scrollToPosition(messages.size - 1)

                        // Actualizar conversationId si era nuevo
                        if (conversationId.isEmpty()) {
                            conversationId = result.conversationId
                        }
                    }
                } else if (!NetworkMonitor.isConnected.value) {
                    // Sin conexión - ya está guardado como pending
                    showToast("Sin conexión. El mensaje se enviará automáticamente.")
                } else {
                    showToast("Error al enviar mensaje")
                }
            }
        }
    }

    /**
     * Carga los mensajes de la conversación.
     * Primero muestra los locales, luego sincroniza con el servidor.
     */
    private fun loadMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Cargar desde base local (inmediato)
                val localMessages = if (conversationId.isNotEmpty()) {
                    OfflineSyncManager.getMessages(conversationId)
                } else {
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    messages.clear()
                    messages.addAll(localMessages)
                    adapter.submitList(messages)
                    if (messages.isNotEmpty()) {
                        binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                    }
                }

                // Marcar como leídos
                markMessagesAsRead()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error al cargar mensajes") }
            }
        }
    }

    private fun markMessagesAsRead() {
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""
        if (conversationId.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Marcar en base local
                OfflineSyncManager.markAllAsReadLocal(conversationId, currentUserId)

                // Marcar en servidor (si hay conexión)
                val unread = messages.filter { !it.read && it.senderId != currentUserId }
                if (NetworkMonitor.isConnected.value && unread.isNotEmpty()) {
                    unread.forEach { msg ->
                        try {
                            ApiClient.messagesApi.markRead(msg.id)
                        } catch (_: Exception) {}
                    }
                    // Notificar al otro usuario via socket
                    SocketManager.emit("message_read", JSONObject().apply {
                        put("messageId", unread.first().id)
                        put("senderId", currentUserId)
                    })
                }
            } catch (_: Exception) {}
        }
    }

    private fun deleteMessage(messageId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (NetworkMonitor.isConnected.value) {
                    val response = ApiClient.messagesApi.deleteMessage(messageId)
                    if (response.isSuccessful) {
                        OfflineSyncManager.db?.softDelete(messageId)
                        withContext(Dispatchers.Main) {
                            val index = messages.indexOfFirst { it.id == messageId }
                            if (index >= 0) {
                                messages.removeAt(index)
                                adapter.notifyItemRemoved(index)
                            }
                        }
                    }
                } else {
                    // Sin conexión - eliminar solo localmente
                    OfflineSyncManager.db?.softDelete(messageId)
                    withContext(Dispatchers.Main) {
                        val index = messages.indexOfFirst { it.id == messageId }
                        if (index >= 0) {
                            messages.removeAt(index)
                            adapter.notifyItemRemoved(index)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error de conexión") }
            }
        }
    }

    private fun setupSocketListeners() {
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""

        // Escuchar mensajes nuevos dirigidos a este usuario
        SocketManager.on("message_$currentUserId") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            try {
                val message = Gson().fromJson(data.toString(), Message::class.java)
                if (message.conversationId == conversationId || message.senderId == receiverId) {
                    runOnUiThread {
                        // Guardar en base local
                        CoroutineScope(Dispatchers.IO).launch {
                            OfflineSyncManager.saveReceivedMessage(message)
                        }

                        // Mostrar en la UI
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

                        val exists = messages.any { it.id == message.id }
                        if (!exists) {
                            messages.add(localMsg)
                            adapter.notifyItemInserted(messages.size - 1)
                            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)

                            // Actualizar conversationId si era nuevo
                            if (conversationId.isEmpty()) {
                                conversationId = message.conversationId
                            }
                        }
                        markMessagesAsRead()
                    }
                }
            } catch (e: Exception) {
                // Ignorar errores de parseo
            }
        }

        // Escuchar indicadores de escritura
        SocketManager.on("typing_$currentUserId") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val typingUserId = data.optString("userId", "")
            if (typingUserId == receiverId) {
                runOnUiThread { binding.tvTypingIndicator.visible() }
            }
        }

        SocketManager.on("stop_typing_$currentUserId") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val typingUserId = data.optString("userId", "")
            if (typingUserId == receiverId) {
                runOnUiThread { binding.tvTypingIndicator.gone() }
            }
        }

        // Escuchar confirmación de lectura
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

    private fun messageToMessage(localMsg: LocalMessage): Message {
        return Message(
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
            sender = com.civis.app.data.model.User(name = localMsg.senderName, avatar = localMsg.senderAvatar)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        val currentUserId = TokenManager.getInstance().getUser()?.id ?: ""
        SocketManager.off("message_$currentUserId")
        SocketManager.off("typing_$currentUserId")
        SocketManager.off("stop_typing_$currentUserId")
        SocketManager.off("message_read_$currentUserId")
    }
}
