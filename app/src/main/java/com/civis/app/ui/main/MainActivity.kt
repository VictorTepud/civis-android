package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.civis.app.R
import com.civis.app.databinding.ActivityMainBinding
import com.civis.app.ui.chat.ChatActivity
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

        // Handle notification intent (from system notification tap when app was closed)
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Si el intent viene de una notificación FCM con type=chat_message,
     * abrir directamente el ChatActivity.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("type")
        if (type == "chat_message") {
            val conversationId = intent.getStringExtra("conversationId") ?: return
            val senderId = intent.getStringExtra("senderId") ?: ""
            val senderName = intent.getStringExtra("senderName") ?: "Chat"
            val senderAvatar = intent.getStringExtra("senderAvatar") ?: ""

            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra("conversationId", conversationId)
                putExtra("receiverId", senderId)
                putExtra("receiverName", senderName)
                putExtra("receiverAvatar", senderAvatar)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
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
