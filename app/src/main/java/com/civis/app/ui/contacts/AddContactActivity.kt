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
import com.civis.app.utils.showToast
import com.google.gson.Gson
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
                val response = ApiClient.contactsApi.getContacts()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val adapter = binding.recyclerViewResults.adapter as? UserSearchAdapter
                        adapter?.submitList(emptyList())
                        showToast("Búsqueda realizada. Agrega el contacto con su ID.")
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

    private fun addContact(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.contactsApi.addContact(AddContactRequest(userId = userId))
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        showToast("Contacto agregado")
                    } else {
                        showToast("Error al agregar contacto")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("Error de conexión") }
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
