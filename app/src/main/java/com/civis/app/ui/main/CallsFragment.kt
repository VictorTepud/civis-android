package com.civis.app.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.Call
import com.civis.app.data.model.Contact
import com.civis.app.databinding.FragmentCallsBinding
import com.civis.app.ui.calls.CallActivity
import com.civis.app.ui.contacts.AddContactActivity
import com.civis.app.utils.TokenManager
import com.civis.app.utils.showToast
import com.civis.app.utils.appGson
import com.bumptech.glide.Glide
import com.civis.app.utils.toGlideUrl
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallsFragment : Fragment() {

    private var _binding: FragmentCallsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CallHistoryAdapter
    private val calls = mutableListOf<Call>()
    private val contacts = mutableListOf<Contact>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCallsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadCallHistory()
        loadContacts()

        binding.fabNewCall.setOnClickListener {
            showCallDialog()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadCallHistory()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCallHistory()
    }

    private fun setupRecyclerView() {
        adapter = CallHistoryAdapter(
            onCallClick = { call ->
                showCallOptions(call)
            }
        )
        binding.recyclerViewCalls.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCalls.adapter = adapter
    }

    private fun loadContacts() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.contactsApi.getContacts()
                if (response.isSuccessful) {
                    val data = response.body()?.data
                    if (data != null) {
                        val type = object : TypeToken<List<Contact>>() {}.type
                        val list: List<Contact> = appGson.fromJson(appGson.toJson(data), type)
                        contacts.clear()
                        contacts.addAll(list)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun showCallDialog() {
        if (contacts.isEmpty()) {
            // Si no hay contactos, ir a agregar contacto
            val options = arrayOf("Buscar usuario", "Agregar contacto")
            AlertDialog.Builder(requireContext())
                .setTitle("Realizar llamada")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(requireContext(), com.civis.app.ui.search.SearchActivity::class.java))
                        1 -> startActivity(Intent(requireContext(), AddContactActivity::class.java))
                    }
                }
                .show()
            return
        }

        // Crear diálogo con lista de contactos
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_select_contact, null)
        val listView = dialogView.findViewById<android.widget.ListView>(android.R.id.list)

        val names = contacts.map { it.user.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("¿A quién llamas?")
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .show()

        // Configurar el ListView manualmente con adapter custom
        val contactAdapter = object : android.widget.ArrayAdapter<String>(
            requireContext(), android.R.layout.simple_list_item_1, names
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.textSize = 16f
                view.setTextColor(requireContext().getColor(R.color.text_primary))
                return view
            }
        }
        listView.adapter = contactAdapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val contact = contacts[position]
            val options = arrayOf("📞 Llamada de voz", "🎥 Videollamada")
            AlertDialog.Builder(requireContext())
                .setTitle(contact.user.name)
                .setItems(options) { _, which ->
                    val type = if (which == 0) "voice" else "video"
                    startCall(contact.user.id, contact.user.name, contact.user.avatar ?: "", type)
                }
                .show()
        }
    }

    private fun showCallOptions(call: Call) {
        val callerName = call.caller?.name ?: "Desconocido"
        val callerId = call.caller?.id ?: call.callerId
        val callerAvatar = call.caller?.avatar ?: ""

        val options = arrayOf("Llamar de nuevo", "Videollamar")
        AlertDialog.Builder(requireContext())
            .setTitle(callerName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startCall(callerId, callerName, callerAvatar, "voice")
                    1 -> startCall(callerId, callerName, callerAvatar, "video")
                }
            }
            .show()
    }

    private fun startCall(receiverId: String, receiverName: String, receiverAvatar: String, callType: String) {
        val intent = Intent(requireContext(), CallActivity::class.java).apply {
            putExtra("receiverId", receiverId)
            putExtra("receiverName", receiverName)
            putExtra("receiverAvatar", receiverAvatar)
            putExtra("callType", callType)
        }
        startActivity(intent)
    }

    private fun loadCallHistory() {
        binding.swipeRefreshLayout.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.callsApi.getCallHistory()
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Call>>() {}.type
                            val list: List<Call> = appGson.fromJson(appGson.toJson(data), type)
                            calls.clear()
                            calls.addAll(list)
                            adapter.submitList(calls)
                            updateEmptyState()
                        }
                    } else {
                        updateEmptyState()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    updateEmptyState()
                }
            }
        }
    }

    private fun updateEmptyState() {
        if (calls.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerViewCalls.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerViewCalls.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
