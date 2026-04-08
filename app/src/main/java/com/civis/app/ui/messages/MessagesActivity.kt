package com.civis.app.ui.messages

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.civis.app.R
import com.civis.app.databinding.ActivityMessagesBinding
import com.civis.app.ui.main.CallsFragment
import com.civis.app.ui.main.ChatsFragment
import com.civis.app.ui.main.CommunitiesFragment
import com.civis.app.ui.main.StatusFragment
import com.civis.app.ui.main.ViewPagerAdapter
import com.civis.app.utils.TokenManager

class MessagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessagesBinding

    private val chatFragments = listOf(
        ChatsFragment(),
        StatusFragment(),
        CommunitiesFragment(),
        CallsFragment()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = TokenManager.getInstance().getToken()
        if (token.isNullOrEmpty()) {
            startActivity(Intent(this, com.civis.app.ui.auth.LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMessagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupBottomNav()
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this, chatFragments)
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

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> binding.bottomNav.menu.findItem(R.id.nav_chats).isChecked = true
                    1 -> binding.bottomNav.menu.findItem(R.id.nav_status).isChecked = true
                    2 -> binding.bottomNav.menu.findItem(R.id.nav_communities).isChecked = true
                    3 -> binding.bottomNav.menu.findItem(R.id.nav_calls).isChecked = true
                }
            }
        })
    }
}
