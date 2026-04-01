package com.civis.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.civis.app.databinding.FragmentProfileBinding
import com.civis.app.ui.profile.ProfileActivity
import com.civis.app.utils.TokenManager
import com.civis.app.utils.toGlideUrl

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserInfo()

        binding.cardEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserInfo()
    }

    private fun loadUserInfo() {
        val user = TokenManager.getInstance().getUser()
        if (user != null) {
            binding.tvUserName.text = user.name
            binding.tvUserBio.text = user.bio ?: "Disponible"
            binding.tvUserEmail.text = user.email
            binding.tvUserPhone.text = user.phone.ifEmpty { "No configurado" }

            if (!user.avatar.isNullOrEmpty()) {
                Glide.with(this)
                    .load(user.avatar.toGlideUrl())
                    .signature(ObjectKey(System.currentTimeMillis()))
                    .placeholder(com.civis.app.R.drawable.ic_profile)
                    .into(binding.ivAvatar)
            } else {
                binding.ivAvatar.setImageResource(com.civis.app.R.drawable.ic_profile)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
