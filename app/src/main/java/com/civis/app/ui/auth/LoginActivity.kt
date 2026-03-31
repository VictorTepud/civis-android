package com.civis.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.LoginRequest
import com.civis.app.databinding.ActivityLoginBinding
import com.civis.app.ui.main.MainActivity
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
import com.civis.app.utils.showToast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (TokenManager.getInstance().isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
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
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

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

        binding.progressBar.visible()
        binding.btnLogin.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.authApi.login(LoginRequest(email, password))
                val rawBody = response.errorBody()?.string()
                val rawSuccess = response.body()?.let { Gson().toJson(it) }

                Log.d("LoginActivity", "Code: ${response.code()}, Body: $rawSuccess, Error: $rawBody")

                withContext(Dispatchers.Main) {
                    binding.progressBar.gone()
                    binding.btnLogin.isEnabled = true

                    if (response.isSuccessful && response.body() != null) {
                        val authResponse = response.body()!!
                        if (authResponse.token.isNotEmpty() && authResponse.user.id.isNotEmpty()) {
                            TokenManager.getInstance().saveToken(authResponse.token)
                            TokenManager.getInstance().saveUser(authResponse.user)
                            SocketManager.connect()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            Log.e("LoginActivity", "token vacío o user sin id. rawBody=$rawSuccess")
                            showToast("Error: respuesta inesperada del servidor")
                        }
                    } else {
                        showToast("Error: ${rawBody ?: "Credenciales incorrectas"}")
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Excepción: ${e.javaClass.simpleName}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.gone()
                    binding.btnLogin.isEnabled = true
                    showToast("Error de conexión: ${e.message}")
                }
            }
        }
    }

    private fun View.visible() { this.visibility = View.VISIBLE }
    private fun View.gone() { this.visibility = View.GONE }
}
