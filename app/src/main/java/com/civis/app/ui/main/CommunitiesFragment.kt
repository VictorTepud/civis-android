package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.Community
import com.civis.app.databinding.FragmentCommunitiesBinding
import com.civis.app.ui.communities.CommunityActivity
import com.civis.app.ui.communities.CreateCommunityActivity
import com.civis.app.utils.showToast
import com.civis.app.utils.appGson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommunitiesFragment : Fragment() {

    private var _binding: FragmentCommunitiesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CommunityAdapter
    private val communities = mutableListOf<Community>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommunitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadCommunities()

        binding.fabCreateCommunity.setOnClickListener {
            startActivity(Intent(requireContext(), CreateCommunityActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = CommunityAdapter(
            onItemClick = { community ->
                val intent = Intent(requireContext(), CommunityActivity::class.java).apply {
                    putExtra("communityId", community.id)
                }
                startActivity(intent)
            }
        )
        binding.recyclerViewCommunities.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCommunities.adapter = adapter
    }

    private fun loadCommunities() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.communitiesApi.getCommunities()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Community>>() {}.type
                            val list: List<Community> = appGson.fromJson(appGson.toJson(data), type)
                            communities.clear()
                            communities.addAll(list)
                            adapter.submitList(communities)
                        }
                    } else {
                        requireContext().showToast("Error al cargar comunidades")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    requireContext().showToast("Error de conexión")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
