package com.civis.app.ui.communities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.CreateCommunityRequest
import com.civis.app.databinding.ActivityCreateCommunityBinding
import com.civis.app.utils.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateCommunityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateCommunityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateCommunityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nueva Comunidad"
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnCreate.setOnClickListener {
            createCommunity()
        }
    }

    private fun createCommunity() {
        val name = binding.etCommunityName.text.toString().trim()
        val description = binding.etCommunityDescription.text.toString().trim()

        if (name.isEmpty()) {
            binding.etCommunityName.error = "El nombre es obligatorio"
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnCreate.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = CreateCommunityRequest(
                    name = name,
                    description = description.ifEmpty { null }
                )
                val response = ApiClient.communitiesApi.createCommunity(request)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreate.isEnabled = true
                    if (response.isSuccessful) {
                        showToast("Comunidad creada exitosamente")
                        finish()
                    } else {
                        showToast("Error al crear comunidad")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreate.isEnabled = true
                    showToast("Error de conexión")
                }
            }
        }
    }
}
