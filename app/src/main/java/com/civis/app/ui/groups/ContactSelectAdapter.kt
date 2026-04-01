package com.civis.app.ui.groups

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.civis.app.R
import com.civis.app.data.model.Contact
import com.civis.app.databinding.ItemContactSelectBinding
import com.civis.app.utils.loadAvatar

class ContactSelectAdapter(
    private val onContactSelected: (Contact, Boolean) -> Unit
) : ListAdapter<Contact, ContactSelectAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val binding: ItemContactSelectBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object DiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(a: Contact, b: Contact): Boolean = a.contactId == b.contactId
        override fun areContentsTheSame(a: Contact, b: Contact): Boolean = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactSelectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = getItem(position)
        with(holder.binding) {
            tvName.text = contact.nickname ?: contact.user.name
            tvPhone.text = contact.user.phone.ifEmpty { contact.user.email }

            ivAvatar.loadAvatar(contact.user.avatar)

            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = false
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                onContactSelected(contact, isChecked)
            }

            root.setOnClickListener {
                checkBox.isChecked = !checkBox.isChecked
            }
        }
    }
}
