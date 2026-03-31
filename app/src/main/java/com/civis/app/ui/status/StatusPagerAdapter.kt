package com.civis.app.ui.status

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.databinding.ItemStatusViewBinding

class StatusPagerAdapter(
    private val statusIds: List<String>,
    private val onComplete: () -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<StatusPagerAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemStatusViewBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStatusViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val statusId = statusIds.getOrNull(position) ?: return
        with(holder.binding) {
            tvStatusContent.text = "Estado $statusId"
            progressBar.max = 100
            progressBar.progress = 0

            val handler = Handler(Looper.getMainLooper())
            var progress = 0
            val runnable = object : Runnable {
                override fun run() {
                    progress += 2
                    progressBar.progress = progress
                    if (progress >= 100) {
                        if (position < statusIds.size - 1) {
                            (root.parent as? ViewPager2)?.setCurrentItem(position + 1, true)
                        } else {
                            onComplete()
                        }
                    } else {
                        handler.postDelayed(this, 100)
                    }
                }
            }
            handler.postDelayed(runnable, 100)

            root.tag = handler
        }
    }

    override fun getItemCount(): Int = statusIds.size
}
