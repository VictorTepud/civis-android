package com.civis.app.ui.groups

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.data.api.ApiClient
import com.civis.app.data.api.ContactsApi
import com.civis.app.data.model.Contact
import com.civis.app.data.model.CreateGroupRequest
import com.civis.app.databinding.ActivityCreateGroupBinding
import com.civis.app.utils.showToast
import com.civis.app.utils.appGson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private lateinit var adapter: ContactSelectAdapter
    private val contacts = mutableListOf<Contact>()
    private val selectedMembers = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nuevo Grupo"
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        loadContacts()

        binding.btnCreate.setOnClickListener {
            createGroup()
        }
    }

    private fun setupRecyclerView() {
        adapter = ContactSelectAdapter(
            onContactSelected = { contact, isSelected ->
                if (isSelected) {
                    selectedMembers.add(contact.contactId)
                } else {
                    selectedMembers.remove(contact.contactId)
                }
                binding.tvMemberCount.text = "${selectedMembers.size} seleccionados"
            }
        )
        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewContacts.adapter = adapter
    }

    private fun loadContacts() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.contactsApi.getContacts()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Contact>>() {}.type
                            val list: List<Contact> = appGson.fromJson(appGson.toJson(data), type)
                            contacts.clear()
                            contacts.addAll(list)
                            adapter.submitList(contacts)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun createGroup() {
        val name = binding.etGroupName.text.toString().trim()
        val description = binding.etGroupDescription.text.toString().trim()

        if (name.isEmpty()) {
            binding.etGroupName.error = "El nombre del grupo es obligatorio"
            return
        }

        if (selectedMembers.isEmpty()) {
            showToast("Selecciona al menos un miembro")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCreate.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = CreateGroupRequest(
                    name = name,
                    memberIds = selectedMembers.toList(),
                    description = description.ifEmpty { null }
                )
                val response = ApiClient.groupsApi.createGroup(request)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreate.isEnabled = true
                    if (response.isSuccessful) {
                        showToast("Grupo creado exitosamente")
                        finish()
                    } else {
                        showToast("Error al crear grupo")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreate.isEnabled = true
                    showToast("Error de conexión")
                }
            }
        }
    }
}
