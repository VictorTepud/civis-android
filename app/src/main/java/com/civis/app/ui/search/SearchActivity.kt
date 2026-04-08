package com.civis.app.ui.search

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.Message
import com.civis.app.data.model.User
import com.civis.app.databinding.ActivitySearchBinding
import com.civis.app.ui.chat.ChatActivity
import com.civis.app.utils.showToast
import com.civis.app.utils.appGson
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val searchMessages = mutableListOf<Message>()
    private val searchContacts = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Buscar"
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupViewPager()

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { performSearch(it) }
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })
    }

    private fun setupViewPager() {
        val adapter = SearchPagerAdapter(this)
        binding.viewPager.adapter = adapter
        com.google.android.material.tabs.TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = adapter.titles[position]
        }.attach()
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) return
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conversationsResponse = ApiClient.messagesApi.getConversations()
                val contactsResponse = ApiClient.contactsApi.getContacts()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (conversationsResponse.isSuccessful) {
                        val data = conversationsResponse.body()?.data
                        if (data != null) {
                            val type = object : TypeToken<List<Message>>() {}.type
                            searchMessages.clear()
                            searchMessages.addAll(
                                appGson.fromJson<List<Message>>(appGson.toJson(data), type)
                                    .filter { it.content?.contains(query, true) == true }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showToast("Error de búsqueda")
                }
            }
        }
    }
}

class SearchPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    val titles = arrayOf("Mensajes", "Contactos", "Grupos")

    override fun getItemCount(): Int = titles.size
    override fun createFragment(position: Int): Fragment = Fragment()
}
