package com.civis.app.ui.auth

import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.LoginRequest
import com.civis.app.data.model.User
import com.civis.app.databinding.ActivityLoginBinding
import com.civis.app.ui.main.MainActivity
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
import com.civis.app.utils.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType

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
                // Usar OkHttp directamente para ver la respuesta raw
                val client = okhttp3.OkHttpClient.Builder().build()
                val json = """{"email":"$email","password":"$password"}""".trimIndent()
                val body = okhttp3.RequestBody.create(
                    "application/json".toMediaType(), json
                )
                val baseUrl = com.civis.app.config.ServerConfig.API_URL
                val request = okhttp3.Request.Builder()
                    .url("${baseUrl}auth/login")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                Log.e("LoginActivity", "URL: $baseUrl/auth/login")
                Log.e("LoginActivity", "HTTP Status: ${response.code}")
                Log.e("LoginActivity", "Response: $responseBody")

                // Parsear manualmente: { success: true, data: { token, user } }
                val jsonObj = org.json.JSONObject(responseBody)
                val success = jsonObj.optBoolean("success", false)
                val data = jsonObj.optJSONObject("data")

                withContext(Dispatchers.Main) {
                    binding.progressBar.gone()
                    binding.btnLogin.isEnabled = true

                    if (success && data != null) {
                        val token = data.optString("token", "")
                        val userObj = data.optJSONObject("user")

                        if (token.isNotEmpty() && userObj != null) {
                            val userId = userObj.optString("id", "")
                            val userName = userObj.optString("name", "")
                            val userEmail = userObj.optString("email", "")
                            val userPhone = userObj.optString("phone", "")
                            val userAvatar = userObj.optString("avatar", "")
                            val userBio = userObj.optString("bio", "")

                            val user = User(
                                id = userId,
                                email = userEmail,
                                name = userName,
                                phone = userPhone,
                                avatar = userAvatar,
                                bio = userBio
                            )

                            Log.d("LoginActivity", "Login OK: user=${userName}, id=${userId}, token=${token.take(20)}...")

                            TokenManager.getInstance().saveToken(token)
                            TokenManager.getInstance().saveUser(user)
                            SocketManager.connect()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } else {
                            Log.e("LoginActivity", "token o user vacíos en data")
                            showToast("Error: respuesta inesperada del servidor")
                        }
                    } else {
                        val errorMsg = jsonObj.optString("message", "Credenciales incorrectas")
                        showToast("Error: $errorMsg")
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
