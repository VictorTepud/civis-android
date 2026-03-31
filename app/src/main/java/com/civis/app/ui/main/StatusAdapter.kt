package com.civis.app.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.civis.app.data.model.Status
import com.civis.app.databinding.ItemStatusBinding
import com.civis.app.utils.toGlideUrl

class StatusAdapter(
    private val onItemClick: (Status, Int) -> Unit
) : ListAdapter<Status, StatusAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemStatusBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<Status>() {
        override fun areItemsTheSame(a: Status, b: Status): Boolean = a.id == b.id
        override fun areContentsTheSame(a: Status, b: Status): Boolean = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStatusBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val status = getItem(position)
        with(holder.binding) {
            tvName.text = status.user?.name ?: "Desconocido"

            if (!status.mediaUrl.isNullOrEmpty()) {
                Glide.with(root.context)
                    .load(status.mediaUrl.toGlideUrl())
                    .placeholder(com.civis.app.R.drawable.ic_profile)
                    .into(ivStatusAvatar)
            } else {
                Glide.with(root.context)
                    .load(status.user?.avatar?.toGlideUrl())
                    .placeholder(com.civis.app.R.drawable.ic_profile)
                    .into(ivStatusAvatar)
            }

            val borderWidth = 3
            ivStatusAvatar.setBorderWidth(borderWidth)
            ivStatusAvatar.borderColor = when {
                status.viewers.isEmpty() -> com.civis.app.R.color.civis_green
                else -> com.civis.app.R.color.civis_dark_green
            }

            root.setOnClickListener { onItemClick(status, position) }
        }
    }
}
