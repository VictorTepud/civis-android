package com.civis.app.ui.contacts

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.AddContactRequest
import com.civis.app.data.model.User
import com.civis.app.databinding.ActivityAddContactBinding
import com.civis.app.ui.chat.ChatActivity
import com.civis.app.utils.NetworkMonitor
import com.civis.app.utils.appGson
import com.civis.app.utils.showToast
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddContactBinding
    private var searchResults = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Agregar Contacto"
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()

        binding.btnSearch.setOnClickListener {
            searchUser()
        }

        binding.etEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchUser()
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        val adapter = UserSearchAdapter(
            onAddClick = { user ->
                addContact(user.id)
            },
            onChatClick = { user ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("receiverId", user.id)
                    putExtra("receiverName", user.name)
                    putExtra("receiverAvatar", user.avatar ?: "")
                }
                startActivity(intent)
            }
        )
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewResults.adapter = adapter
    }

    /**
     * Busca un usuario por email usando el endpoint de búsqueda del servidor.
     * Primero intenta POST /contacts/add (que busca y agrega directamente),
     * si el usuario ya existe contacta muestra un toast informativo.
     */
    private fun searchUser() {
        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty()) {
            binding.etEmail.error = "Ingresa un correo electrónico"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Correo no válido"
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Usar el endpoint de búsqueda de usuarios
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
                        searchResults.clear()

                        // Filtrar solo el que coincide con el email exacto
                        val exact = users.filter { it.email.equals(email, ignoreCase = true) }
                        searchResults.addAll(if (exact.isNotEmpty()) exact else users)

                        withContext(Dispatchers.Main) {
                            val adapter = binding.recyclerViewResults.adapter as? UserSearchAdapter
                            adapter?.submitList(searchResults)
                            if (searchResults.isEmpty()) {
                                showToast("No se encontró ningún usuario con ese correo")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            val adapter = binding.recyclerViewResults.adapter as? UserSearchAdapter
                            adapter?.submitList(emptyList())
                            showToast("No se encontró ningún usuario con ese correo")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showToast("Error al buscar usuario")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AddContact", "Error: ${e.message}", e)
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
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.contactsApi.addContact(AddContactRequest(userId = userId))
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        showToast("Contacto agregado correctamente")
                    } else {
                        showToast("Error al agregar contacto")
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
}

class UserSearchAdapter(
    private val onAddClick: (User) -> Unit,
    private val onChatClick: (User) -> Unit
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
        with(holder.binding) {
            tvName.text = user.name
            tvEmail.text = user.email
            btnAdd.setOnClickListener { onAddClick(user) }
            root.setOnClickListener { onChatClick(user) }
        }
    }
}
