package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.civis.app.R
import com.civis.app.databinding.ActivityMainBinding
import com.civis.app.ui.profile.ProfileActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val fragments = listOf(
        ChatsFragment(),
        StatusFragment(),
        CommunitiesFragment(),
        CallsFragment()
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
        binding.viewPager.offscreenPageLimit = 4
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> binding.viewPager.currentItem = 0
                R.id.nav_status -> binding.viewPager.currentItem = 1
                R.id.nav_communities -> binding.viewPager.currentItem = 2
                R.id.nav_calls -> binding.viewPager.currentItem = 3
            }
            true
        }
    }

    fun navigateToTab(index: Int) {
        binding.viewPager.currentItem = index
    }

    fun openProfile() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }
}
