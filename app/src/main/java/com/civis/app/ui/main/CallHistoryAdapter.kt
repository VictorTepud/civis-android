package com.civis.app.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.data.model.Call
import com.civis.app.databinding.ItemCallBinding
import com.civis.app.utils.formatDate
import com.civis.app.utils.toGlideUrl

class CallHistoryAdapter : ListAdapter<Call, CallHistoryAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemCallBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<Call>() {
        override fun areItemsTheSame(a: Call, b: Call): Boolean = a.id == b.id
        override fun areContentsTheSame(a: Call, b: Call): Boolean = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCallBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val call = getItem(position)
        with(holder.binding) {
            tvName.text = call.caller?.name ?: "Desconocido"

            val callType = if (call.type == "video") "Videollamada" else "Llamada"
            val direction = when (call.status) {
                "missed" -> "Perdida"
                "rejected" -> "Rechazada"
                "completed" -> {
                    val mins = call.duration / 60
                    val secs = call.duration % 60
                    if (mins > 0) "$mins min $secs seg" else "$secs seg"
                }
                else -> "En curso"
            }
            tvCallInfo.text = "$callType - $direction"

            if (call.status == "missed") {
                tvCallInfo.setTextColor(root.context.getColor(R.color.error_red))
                ivCallIcon.setImageResource(R.drawable.ic_call_missed)
            } else {
                tvCallInfo.setTextColor(root.context.getColor(R.color.text_secondary))
                ivCallIcon.setImageResource(
                    if (call.type == "video") R.drawable.ic_video else R.drawable.ic_call
                )
            }

            call.caller?.avatar?.let { avatar ->
                Glide.with(root.context)
                    .load(avatar.toGlideUrl())
                    .placeholder(R.drawable.ic_profile)
                    .into(ivAvatar)
            }

            tvTime.text = call.startedAt?.formatDate() ?: ""
        }
    }
}
