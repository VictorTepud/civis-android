package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private val filteredCommunities = mutableListOf<Community>()

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
        setupSearch()
        loadCommunities()

        binding.fabCreateCommunity.setOnClickListener {
            startActivity(Intent(requireContext(), CreateCommunityActivity::class.java))
        }

        binding.btnExploreCommunities.setOnClickListener {
            startActivity(Intent(requireContext(), CreateCommunityActivity::class.java))
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadCommunities()
        }
    }

    override fun onResume() {
        super.onResume()
        loadCommunities()
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCommunities(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterCommunities(query: String) {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            filteredCommunities.clear()
            filteredCommunities.addAll(communities)
        } else {
            filteredCommunities.clear()
            filteredCommunities.addAll(
                communities.filter {
                    it.name.lowercase().contains(trimmed) ||
                    (it.description?.lowercase()?.contains(trimmed) == true)
                }
            )
        }
        adapter.submitList(filteredCommunities.toList())
        updateEmptyState()
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
        binding.swipeRefreshLayout.isRefreshing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.communitiesApi.getCommunities()
                withContext(Dispatchers.Main) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Community>>() {}.type
                            val list: List<Community> = appGson.fromJson(appGson.toJson(data), type)
                            communities.clear()
                            communities.addAll(list)
                            filterCommunities(binding.etSearch.text?.toString() ?: "")
                        } else {
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
        if (filteredCommunities.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerViewCommunities.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerViewCommunities.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
