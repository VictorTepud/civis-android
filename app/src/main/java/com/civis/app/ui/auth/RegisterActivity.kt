package com.civis.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.RegisterRequest
import com.civis.app.databinding.ActivityRegisterBinding
import com.civis.app.ui.main.MainActivity
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
import com.civis.app.utils.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            attemptRegister()
        }

        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.ivTogglePassword.setOnClickListener {
            val isSelected = binding.ivTogglePassword.isSelected
            binding.ivTogglePassword.isSelected = !isSelected
            if (binding.ivTogglePassword.isSelected) {
                binding.etPassword.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding.etPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

        binding.ivToggleConfirm.setOnClickListener {
            val isSelected = binding.ivToggleConfirm.isSelected
            binding.ivToggleConfirm.isSelected = !isSelected
            if (binding.ivToggleConfirm.isSelected) {
                binding.etConfirmPassword.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                binding.etConfirmPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }
    }

    private fun attemptRegister() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (name.isEmpty()) {
            binding.etName.error = "El nombre es obligatorio"
            return
        }
        if (email.isEmpty()) {
            binding.etEmail.error = "El correo es obligatorio"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Correo electrónico no válido"
            return
        }
        if (password.isEmpty()) {
            binding.etPassword.error = "La contraseña es obligatoria"
            return
        }
        if (password.length < 6) {
            binding.etPassword.error = "La contraseña debe tener al menos 6 caracteres"
            return
        }
        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Las contraseñas no coinciden"
            return
        }

        binding.progressBar.visible()
        binding.btnRegister.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = RegisterRequest(
                    email = email,
                    password = password,
                    name = name,
                    phone = phone
                )
                val response = ApiClient.authApi.register(request)
                withContext(Dispatchers.Main) {
                    binding.progressBar.gone()
                    binding.btnRegister.isEnabled = true

                    if (response.isSuccessful) {
                        val authResponse = response.body()
                        if (authResponse != null) {
                            TokenManager.getInstance().saveToken(authResponse.token)
                            TokenManager.getInstance().saveUser(authResponse.user)
                            SocketManager.connect()
                            startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                            finish()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        showToast("Error: ${errorBody ?: "No se pudo registrar"}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.gone()
                    binding.btnRegister.isEnabled = true
                    showToast("Error de conexión: ${e.message}")
                }
            }
        }
    }

    private fun View.visible() { this.visibility = View.VISIBLE }
    private fun View.gone() { this.visibility = View.GONE }
}
