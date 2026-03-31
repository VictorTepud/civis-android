package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.civis.app.R
import com.civis.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val fragments = listOf(
        ChatsFragment(),
        StatusFragment(),
        CallsFragment(),
        CommunitiesFragment(),
        ProfileFragment()
    )
    private val tabTitles = arrayOf("Chats", "Estados", "Llamadas", "Comunidades", "Perfil")
    private val tabIcons = intArrayOf(
        R.drawable.ic_chat,
        R.drawable.ic_status,
        R.drawable.ic_call,
        R.drawable.ic_community,
        R.drawable.ic_profile
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupBottomNav()
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this, fragments)
        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 5
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> binding.viewPager.currentItem = 0
                R.id.nav_status -> binding.viewPager.currentItem = 1
                R.id.nav_calls -> binding.viewPager.currentItem = 2
                R.id.nav_communities -> binding.viewPager.currentItem = 3
                R.id.nav_profile -> binding.viewPager.currentItem = 4
            }
            true
        }
    }

    fun navigateToTab(index: Int) {
        binding.viewPager.currentItem = index
    }
}
