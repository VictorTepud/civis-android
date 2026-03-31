package com.civis.app.ui.status

import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.civis.app.databinding.ActivityViewStatusBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ViewStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewStatusBinding
    private var statusIds: List<String> = emptyList()
    private var startIndex: Int = 0
    private var adapter: StatusPagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statusIds = intent.getStringArrayListExtra("statusIds") ?: emptyList()
        startIndex = intent.getIntExtra("startIndex", 0)

        setupViewPager()
    }

    private fun setupViewPager() {
        adapter = StatusPagerAdapter(statusIds) {
            finish()
        }
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startIndex, false)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.tvStatusIndex.text = "${position + 1} / ${statusIds.size}"
            }
        })

        binding.viewPager.getChildAt(0).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val width = binding.viewPager.width
                    val x = event.x
                    if (x < width / 3) {
                        // Tap left - previous
                        val current = binding.viewPager.currentItem
                        if (current > 0) binding.viewPager.setCurrentItem(current - 1, true)
                    } else if (x > width * 2 / 3) {
                        // Tap right - next
                        val current = binding.viewPager.currentItem
                        if (current < statusIds.size - 1) binding.viewPager.setCurrentItem(current + 1, true)
                    }
                    // Middle tap - pause/resume (could be implemented)
                }
            }
            false
        }

        binding.etReply.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val replyText = binding.etReply.text.toString().trim()
                if (replyText.isNotEmpty()) {
                    sendReply(replyText)
                    binding.etReply.text.clear()
                }
                true
            } else {
                false
            }
        }
    }

    private fun sendReply(text: String) {
        val currentPosition = binding.viewPager.currentItem
        val statusId = statusIds.getOrNull(currentPosition) ?: return
        // Reply via API
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.civis.app.data.api.ApiClient.statusApi.replyStatus(
                    statusId,
                    com.civis.app.data.model.ReplyStatusRequest(content = text)
                )
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter = null
    }
}
