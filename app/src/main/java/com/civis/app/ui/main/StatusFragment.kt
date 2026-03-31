package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.Status
import com.civis.app.databinding.FragmentStatusBinding
import com.civis.app.ui.status.CreateStatusActivity
import com.civis.app.ui.status.ViewStatusActivity
import com.civis.app.utils.showToast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StatusAdapter
    private val statuses = mutableListOf<Status>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadStatuses()

        binding.fabCreateStatus.setOnClickListener {
            startActivity(Intent(requireContext(), CreateStatusActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = StatusAdapter(
            onItemClick = { status, position ->
                val intent = Intent(requireContext(), ViewStatusActivity::class.java).apply {
                    putStringArrayListExtra("statusIds", ArrayList(statuses.map { it.id }))
                    putExtra("startIndex", position)
                }
                startActivity(intent)
            }
        )
        binding.recyclerViewStatuses.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerViewStatuses.adapter = adapter
    }

    private fun loadStatuses() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.statusApi.getStatuses()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Status>>() {}.type
                            val list: List<Status> = Gson().fromJson(Gson().toJson(data), type)
                            statuses.clear()
                            statuses.addAll(list)
                            adapter.submitList(statuses)
                        }
                    } else {
                        showToast("Error al cargar estados")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error de conexión")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
