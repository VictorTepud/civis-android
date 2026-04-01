package com.civis.app.ui.profile

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.UpdateProfileRequest
import com.civis.app.databinding.ActivityProfileEditBinding
import com.civis.app.ui.auth.LoginActivity
import com.civis.app.utils.hasImagePermission
import com.civis.app.utils.imagePermissions
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
import com.civis.app.utils.showToast
import com.civis.app.utils.toGlideUrl
import com.civis.app.utils.appGson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding
    private var selectedAvatarUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedAvatarUri = uri
        uri?.let { binding.ivAvatar.setImageURI(it) }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pickImageLauncher.launch("image/*")
        } else {
            showToast("Permiso denegado para acceder a imagenes")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Editar Perfil"
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadProfile()
        setupListeners()
    }

    private fun loadProfile() {
        val savedUser = TokenManager.getInstance().getUser()
        if (savedUser != null) {
            binding.etName.setText(savedUser.name)
            binding.etBio.setText(savedUser.bio ?: "")
            binding.etPhone.setText(savedUser.phone)

            if (!savedUser.avatar.isNullOrEmpty()) {
                Glide.with(this)
                    .load(savedUser.avatar.toGlideUrl())
                    .placeholder(R.drawable.ic_profile)
                    .into(binding.ivAvatar)
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = ApiClient.usersApi.getProfile()
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            val user = appGson.fromJson(
                                appGson.toJson(response.body()?.data),
                                com.civis.app.data.model.User::class.java
                            )
                            binding.etName.setText(user.name)
                            binding.etBio.setText(user.bio ?: "")
                            binding.etPhone.setText(user.phone)
                            if (!user.avatar.isNullOrEmpty()) {
                                Glide.with(this@ProfileActivity)
                                    .load(user.avatar.toGlideUrl())
                                    .placeholder(R.drawable.ic_profile)
                                    .into(binding.ivAvatar)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun setupListeners() {
        binding.ivAvatar.setOnClickListener {
            if (hasImagePermission()) {
                pickImageLauncher.launch("image/*")
            } else {
                permissionLauncher.launch(imagePermissions().first())
            }
        }

        binding.btnSave.setOnClickListener {
            saveProfile()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val bio = binding.etBio.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()

        if (name.isEmpty()) {
            binding.etName.error = "El nombre es obligatorio"
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        android.util.Log.e("ProfileActivity", "=== INICIO saveProfile ===")
        android.util.Log.e("ProfileActivity", "selectedAvatarUri: $selectedAvatarUri")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var avatarUrl: String? = null

                if (selectedAvatarUri != null) {
                    android.util.Log.e("ProfileActivity", "PASO 1: Abriendo inputStream...")
                    val inputStream = contentResolver.openInputStream(selectedAvatarUri!!)
                    if (inputStream == null) {
                        android.util.Log.e("ProfileActivity", "ERROR: No se pudo abrir inputStream del URI")
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.btnSave.isEnabled = true
                            showToast("Error al leer la imagen seleccionada")
                        }
                        return@launch
                    }

                    android.util.Log.e("ProfileActivity", "PASO 2: Creando tempFile...")
                    val tempFile = java.io.File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                    tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                    inputStream.close()
                    android.util.Log.e("ProfileActivity", "PASO 2: tempFile creado, size=${tempFile.length()} bytes")

                    val mediaType = (contentResolver.getType(selectedAvatarUri!!) ?: "image/jpeg").toMediaType()
                    val requestFile = tempFile.asRequestBody(mediaType)

                    android.util.Log.e("ProfileActivity", "PASO 3: Creando MultipartBody con field='avatar'...")
                    val body = okhttp3.MultipartBody.Part.createFormData("avatar", tempFile.name, requestFile)

                    android.util.Log.e("ProfileActivity", "PASO 4: Enviando uploadAvatar...")
                    val uploadResponse = ApiClient.uploadApi.uploadAvatar(body)
                    android.util.Log.e("ProfileActivity", "PASO 4: Upload response code=${uploadResponse.code()}, message=${uploadResponse.message()}")
                    android.util.Log.e("ProfileActivity", "PASO 4: Upload response body=${uploadResponse.body()}")
                    android.util.Log.e("ProfileActivity", "PASO 4: Upload errorBody=${uploadResponse.errorBody()?.string()}")

                    if (uploadResponse.isSuccessful) {
                        val rawData = uploadResponse.body()?.data
                        android.util.Log.e("ProfileActivity", "PASO 5: rawData type=${rawData?.javaClass?.name}, value=$rawData")
                        
                        // Probar multiples formas de extraer la URL
                        when (rawData) {
                            is Map<*, *> -> {
                                avatarUrl = rawData["url"] as? String
                                android.util.Log.e("ProfileActivity", "PASO 5a: Extraido de Map, avatarUrl=$avatarUrl")
                            }
                            is com.google.gson.JsonObject -> {
                                avatarUrl = rawData.get("url")?.asString
                                android.util.Log.e("ProfileActivity", "PASO 5b: Extraido de JsonObject, avatarUrl=$avatarUrl")
                            }
                            is String -> {
                                // Si data viene como string JSON
                                try {
                                    val jsonObj = org.json.JSONObject(rawData)
                                    avatarUrl = jsonObj.optString("url", null)
                                    android.util.Log.e("ProfileActivity", "PASO 5c: Extraido de String JSON, avatarUrl=$avatarUrl")
                                } catch (e: Exception) {
                                    android.util.Log.e("ProfileActivity", "PASO 5c: Error parseando string: ${e.message}")
                                }
                            }
                            else -> {
                                // Ultimo intento: serializar con appGson
                                try {
                                    val jsonStr = appGson.toJson(rawData)
                                    val jsonObj = org.json.JSONObject(jsonStr)
                                    avatarUrl = jsonObj.optString("url", null)
                                    android.util.Log.e("ProfileActivity", "PASO 5d: Extraido via appGson serialize, jsonStr=$jsonStr, avatarUrl=$avatarUrl")
                                } catch (e: Exception) {
                                    android.util.Log.e("ProfileActivity", "PASO 5d: Error serializando: ${e.message}")
                                }
                            }
                        }
                    } else {
                        android.util.Log.e("ProfileActivity", "UPLOAD FALLO: code=${uploadResponse.code()}")
                    }

                    tempFile.delete()
                } else {
                    android.util.Log.e("ProfileActivity", "No se selecciono nueva foto, conservando avatar existente")
                }

                val finalAvatarForRequest = avatarUrl ?: TokenManager.getInstance().getUser()?.avatar
                android.util.Log.e("ProfileActivity", "PASO 6: Enviando updateProfile con avatar=$finalAvatarForRequest")

                val request = UpdateProfileRequest(
                    name = name,
                    bio = bio.ifEmpty { null },
                    phone = phone.ifEmpty { null },
                    avatar = finalAvatarForRequest
                )

                val response = ApiClient.usersApi.updateProfile(request)
                android.util.Log.e("ProfileActivity", "PASO 7: updateProfile code=${response.code()}, body=${response.body()}")

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    if (response.isSuccessful) {
                        val currentUser = TokenManager.getInstance().getUser()
                        val savedAvatar = avatarUrl ?: currentUser?.avatar
                        val savedBio = if (bio.isEmpty()) currentUser?.bio else bio
                        val savedPhone = if (phone.isEmpty()) currentUser?.phone else phone
                        val user = currentUser?.copy(
                            name = name, bio = savedBio, phone = savedPhone ?: "", avatar = savedAvatar
                        )
                        if (user != null) TokenManager.getInstance().saveUser(user)
                        android.util.Log.e("ProfileActivity", "PASO 8: Perfil guardado, avatar final=$savedAvatar")
                        showToast("Perfil actualizado")
                        finish()
                    } else {
                        android.util.Log.e("ProfileActivity", "PASO 7: updateProfile FALLO: ${response.code()}")
                        showToast("Error al actualizar perfil")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileActivity", "EXCEPCION: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    showToast("Error de conexion: ${e.message}")
                }
            }
        }
    }

    private fun logout() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.authApi.logout()
            } catch (_: Exception) {}
        }
        SocketManager.disconnect()
        TokenManager.getInstance().clearAll()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
