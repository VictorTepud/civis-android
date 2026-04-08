package com.civis.app.ui.contacts

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.AddContactRequest
import com.civis.app.data.model.Contact
import com.civis.app.data.model.User
import com.civis.app.databinding.ActivityAddContactBinding
import com.civis.app.ui.chat.ChatActivity
import com.civis.app.utils.NetworkMonitor
import com.civis.app.utils.TokenManager
import com.civis.app.utils.appGson
import com.civis.app.utils.loadAvatar
import com.civis.app.utils.showToast
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddContactBinding
    private var myContacts = mutableListOf<Contact>()
    private var searchResults = mutableListOf<User>()
    private val addedContactIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Contactos"
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupSelfChatItem()
        setupRecyclerViews()

        binding.btnSearch.setOnClickListener { performSearch() }

        binding.etEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        loadContacts()
    }

    /**
     * Configura el item "Enviar a mi mismo" que abre un chat con el usuario actual.
     */
    private fun setupSelfChatItem() {
        val currentUser = TokenManager.getInstance().getUser()
        if (currentUser != null) {
            val selfView: View = findViewById(com.civis.app.R.id.itemSelfChat)
            selfView.findViewById<de.hdodenhof.circleimageview.CircleImageView>(com.civis.app.R.id.ivAvatar)
                .loadAvatar(currentUser.avatar)
            selfView.findViewById<android.widget.TextView>(com.civis.app.R.id.tvName)
                .text = "Enviar a mi mismo"
            selfView.findViewById<android.widget.TextView>(com.civis.app.R.id.tvStatus)
                .text = currentUser.email

            val statusTv = selfView.findViewById<android.widget.TextView>(com.civis.app.R.id.tvStatus)
            statusTv.setTextColor(com.civis.app.R.color.text_secondary)

            selfView.setOnClickListener {
                openChat(currentUser.id, currentUser.name, currentUser.avatar)
            }
        } else {
            findViewById<View>(com.civis.app.R.id.itemSelfChat).visibility = View.GONE
            binding.tvSectionSelf.visibility = View.GONE
        }
    }

    private fun setupRecyclerViews() {
        // Adapter para contactos existentes
        val contactsAdapter = ContactListAdapter(
            onClick = { contact ->
                openChat(contact.user.id, contact.nickname ?: contact.user.name, contact.user.avatar)
            }
        )
        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = contactsAdapter

        // Adapter para resultados de búsqueda (usuarios nuevos)
        val searchAdapter = UserSearchAdapter(
            onAddClick = { user -> addContact(user.id) },
            onChatClick = { user -> openChat(user.id, user.name, user.avatar) },
            isContactAdded = { userId -> addedContactIds.contains(userId) }
        )
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewResults.adapter = searchAdapter
    }

    /**
     * Carga los contactos existentes del servidor.
     * El servidor devuelve campos planos (contact_id, name, avatar, online...)
     * pero el modelo Contact espera user anidado → mapeamos manualmente.
     */
    private fun loadContacts() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.contactsApi.getContacts()
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val jsonStr = appGson.toJson(data)
                        val jsonObj = org.json.JSONObject(jsonStr)
                        val contactsArr = jsonObj.optJSONArray("contacts")

                        if (contactsArr != null) {
                            val list = mutableListOf<Contact>()
                            for (i in 0 until contactsArr.length()) {
                                val c = contactsArr.getJSONObject(i)
                                val user = User(
                                    id = c.optString("contact_id", ""),
                                    email = c.optString("email", ""),
                                    name = c.optString("name", ""),
                                    phone = c.optString("phone", ""),
                                    avatar = c.optString("avatar", null),
                                    bio = c.optString("bio", null),
                                    online = c.optBoolean("online", false),
                                    lastSeen = c.optString("last_seen", null)
                                )
                                list.add(Contact(
                                    contactId = c.optString("contact_id", ""),
                                    nickname = if (c.isNull("nickname")) null else c.optString("nickname"),
                                    blocked = c.optBoolean("blocked", false),
                                    muted = c.optBoolean("muted", false),
                                    user = user
                                ))
                            }
                            myContacts.clear()
                            myContacts.addAll(list)
                            addedContactIds.addAll(list.map { it.contactId })

                            withContext(Dispatchers.Main) {
                                updateContactsList()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ContactsActivity", "loadContacts error: ${e.message}", e)
            }
        }
    }

    /**
     * Detecta si el texto es un email o un nombre y busca en consecuencia.
     * - Email → busca en el servidor usuarios no añadidos
     * - Nombre → filtra contactos ya añadidos
     */
    private fun performSearch() {
        val query = binding.etEmail.text.toString().trim()
        if (query.isEmpty()) {
            // Sin búsqueda, mostrar solo contactos existentes
            updateContactsList()
            hideSearchResults()
            return
        }

        if (android.util.Patterns.EMAIL_ADDRESS.matcher(query).matches()) {
            // Es un correo → buscar usuarios en el servidor
            searchUserByEmail(query)
        } else {
            // Es un nombre → filtrar contactos existentes
            filterContactsByName(query)
        }
    }

    /**
     * Filtra la lista de contactos existentes por nombre.
     */
    private fun filterContactsByName(name: String) {
        val filtered = myContacts.filter {
            (it.nickname ?: it.user.name).contains(name, ignoreCase = true)
        }

        // Si no hay contactos cargados aún, esperar
        if (myContacts.isEmpty()) {
            showToast("Cargando contactos...")
            return
        }

        hideSearchResults()

        with(binding.recyclerViewContacts.adapter as? ContactListAdapter) {
            this?.submitList(filtered)
        }

        // Actualizar encabezado
        binding.tvSectionContacts.text = if (filtered.isEmpty()) {
            "SIN RESULTADOS"
        } else {
            "CONTACTOS (${filtered.size})"
        }
        binding.tvSectionContacts.visibility = View.VISIBLE
        binding.recyclerViewContacts.visibility = View.VISIBLE
    }

    /**
     * Busca un usuario por email en el servidor para agregarlo.
     */
    private fun searchUserByEmail(email: String) {
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = com.civis.app.utils.TokenManager.getInstance().getToken() ?: ""
                val client = okhttp3.OkHttpClient.Builder().build()
                val request = okhttp3.Request.Builder()
                    .url("${com.civis.app.config.ServerConfig.API_URL}search/users?q=${java.net.URLEncoder.encode(email, "UTF-8")}")
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }

                if (response.isSuccessful) {
                    val jsonObj = org.json.JSONObject(responseBody)
                    val data = jsonObj.optJSONObject("data")
                    val usersArr = data?.optJSONArray("users") ?: jsonObj.optJSONArray("data")

                    if (usersArr != null && usersArr.length() > 0) {
                        val type = object : TypeToken<List<User>>() {}.type
                        val users: List<User> = appGson.fromJson(usersArr.toString(), type)

                        // Filtrar usuarios que ya son contactos Y el usuario actual (no puede agregarse a sí mismo)
                        val currentUser = TokenManager.getInstance().getUser()
                        val newUsers = users.filter {
                            !addedContactIds.contains(it.id) &&
                            (currentUser == null || it.id != currentUser.id)
                        }

                        // Preferir coincidencia exacta de email
                        val exact = newUsers.filter { it.email.equals(email, ignoreCase = true) }
                        searchResults.clear()
                        searchResults.addAll(if (exact.isNotEmpty()) exact else newUsers)

                        withContext(Dispatchers.Main) {
                            // Mostrar contactos existentes arriba
                            updateContactsList()

                            // Mostrar resultados de búsqueda abajo
                            val searchAdapter = binding.recyclerViewResults.adapter as? UserSearchAdapter
                            searchAdapter?.submitList(searchResults)

                            if (searchResults.isEmpty() && newUsers.isEmpty()) {
                                // Todos los resultados ya son contactos
                                showToast("Este usuario ya está en tus contactos")
                                hideSearchResults()
                            } else if (searchResults.isEmpty()) {
                                showToast("No se encontró ningún usuario con ese correo")
                                hideSearchResults()
                            } else {
                                binding.tvSectionResults.text = "RESULTADOS DE BÚSQUEDA (${searchResults.size})"
                                binding.tvSectionResults.visibility = View.VISIBLE
                                binding.recyclerViewResults.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            updateContactsList()
                            hideSearchResults()
                            showToast("No se encontró ningún usuario con ese correo")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateContactsList()
                        hideSearchResults()
                        showToast("Error al buscar usuario")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ContactsActivity", "Error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (NetworkMonitor.isConnected.value) {
                        showToast("Error al buscar usuario")
                    } else {
                        showToast("Sin conexión")
                    }
                }
            }
        }
    }

    private fun addContact(userId: String) {
        if (addedContactIds.contains(userId)) {
            showToast("Este contacto ya fue agregado")
            return
        }
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.contactsApi.addContact(AddContactRequest(userId = userId))
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    when (response.code()) {
                        201 -> {
                            addedContactIds.add(userId)
                            showToast("Contacto agregado correctamente")
                            // Actualizar botón en el adapter
                            val adapter = binding.recyclerViewResults.adapter as? UserSearchAdapter
                            adapter?.notifyDataSetChanged()
                            // Recargar contactos para mostrar el nuevo
                            loadContacts()
                        }
                        409 -> {
                            addedContactIds.add(userId)
                            showToast("Este contacto ya está en tu lista")
                            val adapter = binding.recyclerViewResults.adapter as? UserSearchAdapter
                            adapter?.notifyDataSetChanged()
                        }
                        404 -> showToast("Usuario no encontrado")
                        400 -> showToast("No puedes agregarte a ti mismo")
                        else -> showToast("Error al agregar contacto (${response.code()})")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (NetworkMonitor.isConnected.value) {
                        showToast("Error al agregar contacto")
                    } else {
                        showToast("Sin conexión")
                    }
                }
            }
        }
    }

    private fun openChat(userId: String, userName: String, userAvatar: String?) {
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Obtener o crear la conversación primero (igual que hace el servidor al enviar)
                val response = ApiClient.messagesApi.findOrCreateConversation(
                    mapOf("receiver_id" to userId)
                )

                if (response.isSuccessful) {
                    val data = response.body()?.data as? Map<*, *>
                    val convId = data?.get("conversationId") as? String
                        ?: data?.get("conversation_id") as? String
                        ?: ""

                    android.util.Log.d("ContactsActivity", "openChat: convId=$convId for userId=$userId")

                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE

                        // Abrir ChatActivity con las 4 extras EXACTAMENTE igual que ChatsFragment
                        val intent = Intent(this@ContactsActivity, ChatActivity::class.java).apply {
                            putExtra("conversationId", convId)
                            putExtra("receiverId", userId)
                            putExtra("receiverName", userName)
                            putExtra("receiverAvatar", userAvatar ?: "")
                        }
                        startActivity(intent)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        showToast("Error al abrir conversación")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ContactsActivity", "openChat error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (NetworkMonitor.isConnected.value) {
                        showToast("Error al abrir conversación")
                    } else {
                        showToast("Sin conexión")
                    }
                }
            }
        }
    }

    private fun updateContactsList() {
        if (myContacts.isNotEmpty()) {
            binding.tvSectionContacts.text = "CONTACTOS (${myContacts.size})"
            binding.tvSectionContacts.visibility = View.VISIBLE
            binding.recyclerViewContacts.visibility = View.VISIBLE
            val contactsAdapter = binding.recyclerViewContacts.adapter as? ContactListAdapter
            contactsAdapter?.submitList(myContacts.toList())
        } else {
            binding.tvSectionContacts.visibility = View.GONE
            binding.recyclerViewContacts.visibility = View.GONE
        }
    }

    private fun hideSearchResults() {
        binding.tvSectionResults.visibility = View.GONE
        binding.recyclerViewResults.visibility = View.GONE
        val searchAdapter = binding.recyclerViewResults.adapter as? UserSearchAdapter
        searchAdapter?.submitList(emptyList())
    }
}

// Adapter para contactos ya añadidos (muestra nombre, avatar, estado)
class ContactListAdapter(
    private val onClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactListAdapter.ViewHolder>() {

    private var items = listOf<Contact>()

    fun submitList(list: List<Contact>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(com.civis.app.R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = items[position]
        val displayName = contact.nickname ?: contact.user.name
        val statusText = if (contact.user.online) "En línea" else contact.user.phone.ifEmpty { contact.user.email }

        holder.view.findViewById<de.hdodenhof.circleimageview.CircleImageView>(com.civis.app.R.id.ivAvatar)
            .loadAvatar(contact.user.avatar)
        holder.view.findViewById<android.widget.TextView>(com.civis.app.R.id.tvName).text = displayName
        holder.view.findViewById<android.widget.TextView>(com.civis.app.R.id.tvStatus).text = statusText

        // Color del estado: verde si online, gris si offline
        val tvStatus = holder.view.findViewById<android.widget.TextView>(com.civis.app.R.id.tvStatus)
        tvStatus.setTextColor(
            if (contact.user.online) com.civis.app.R.color.online_green
            else com.civis.app.R.color.text_secondary
        )

        holder.view.setOnClickListener { onClick(contact) }
    }

    override fun getItemCount() = items.size
}

// Adapter para resultados de búsqueda (usuarios nuevos para agregar)
class UserSearchAdapter(
    private val onAddClick: (User) -> Unit,
    private val onChatClick: (User) -> Unit,
    private val isContactAdded: (String) -> Boolean = { false }
) : androidx.recyclerview.widget.ListAdapter<User, UserSearchAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(a: User, b: User) = a.id == b.id
        override fun areContentsTheSame(a: User, b: User) = a == b
    }
) {
    inner class ViewHolder(val binding: com.civis.app.databinding.ItemUserSearchBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val binding = com.civis.app.databinding.ItemUserSearchBinding.inflate(
            android.view.LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)
        val alreadyAdded = isContactAdded(user.id)
        with(holder.binding) {
            tvName.text = user.name
            tvEmail.text = user.email
            if (alreadyAdded) {
                btnAdd.text = "Agregado"
                btnAdd.isEnabled = false
                btnAdd.alpha = 0.5f
            } else {
                btnAdd.text = "Agregar"
                btnAdd.isEnabled = true
                btnAdd.alpha = 1.0f
            }
            btnAdd.setOnClickListener { onAddClick(user) }
            root.setOnClickListener { onChatClick(user) }
        }
    }
}
