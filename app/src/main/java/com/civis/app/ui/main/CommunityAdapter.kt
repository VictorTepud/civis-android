package com.civis.app.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.data.model.Community
import com.civis.app.databinding.ItemCommunityBinding
import com.civis.app.utils.toGlideUrl

class CommunityAdapter(
    private val onItemClick: (Community) -> Unit
) : ListAdapter<Community, CommunityAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemCommunityBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<Community>() {
        override fun areItemsTheSame(a: Community, b: Community): Boolean = a.id == b.id
        override fun areContentsTheSame(a: Community, b: Community): Boolean = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommunityBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val community = getItem(position)
        with(holder.binding) {
            tvCommunityName.text = community.name
            tvCommunityDescription.text = community.description ?: "Sin descripción"
            tvMemberCount.text = "${community.memberCount} miembros"

            if (!community.cover.isNullOrEmpty()) {
                Glide.with(root.context)
                    .load(community.cover.toGlideUrl())
                    .placeholder(R.drawable.ic_community)
                    .into(ivCover)
            } else if (!community.avatar.isNullOrEmpty()) {
                Glide.with(root.context)
                    .load(community.avatar.toGlideUrl())
                    .placeholder(R.drawable.ic_community)
                    .into(ivCover)
            }

            root.setOnClickListener { onItemClick(community) }
        }
    }
}
