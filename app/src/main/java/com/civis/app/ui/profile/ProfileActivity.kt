package com.civis.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.UpdateProfileRequest
import com.civis.app.databinding.ActivityProfileEditBinding
import com.civis.app.ui.auth.LoginActivity
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
import com.civis.app.utils.appGson
import com.civis.app.utils.hasImagePermission
import com.civis.app.utils.imagePermissions
import com.civis.app.utils.showToast
import com.civis.app.utils.toGlideUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody

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
                } catch (_: Exception) { }
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

        binding.btnSave.setOnClickListener { saveProfile() }

        binding.btnLogout.setOnClickListener { logout() }
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var avatarUrl: String? = null

                if (selectedAvatarUri != null) {
                    val inputStream = contentResolver.openInputStream(selectedAvatarUri!!)
                    if (inputStream == null) {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.visibility = View.GONE
                            binding.btnSave.isEnabled = true
                            showToast("Error al leer la imagen")
                        }
                        return@launch
                    }

                    val tempFile = java.io.File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                    tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                    inputStream.close()

                    val mediaType = (contentResolver.getType(selectedAvatarUri!!) ?: "image/jpeg").toMediaType()
                    val requestFile = tempFile.asRequestBody(mediaType)
                    // Campo 'avatar' como espera el servidor: avatarUpload.single('avatar')
                    val body = okhttp3.MultipartBody.Part.createFormData("avatar", tempFile.name, requestFile)

                    android.util.Log.e("ProfileActivity", "Enviando upload avatar, tempFile size=${tempFile.length()}")
                    val uploadResponse = ApiClient.uploadApi.uploadAvatar(body)
                    android.util.Log.e("ProfileActivity", "Upload code=${uploadResponse.code()} body=${uploadResponse.body()} err=${uploadResponse.errorBody()?.string()}")

                    if (uploadResponse.isSuccessful) {
                        // Servidor envuelve en { success: true, data: { url } }
                        val data = uploadResponse.body()?.data
                        val dataMap = data as? Map<*, *>
                        avatarUrl = dataMap?.get("url") as? String
                        android.util.Log.e("ProfileActivity", "Upload data=$data, avatarUrl=$avatarUrl")
                    } else {
                        android.util.Log.e("ProfileActivity", "Upload fallo: ${uploadResponse.code()}")
                    }
                    tempFile.delete()
                }

                // El servidor ya guarda el avatar en BD con el upload.
                // Aqui solo actualizamos nombre, bio, phone.
                // Si se subio avatar, ya se guardo en BD por el upload route.
                val request = UpdateProfileRequest(
                    name = name,
                    bio = bio.ifEmpty { null },
                    phone = phone.ifEmpty { null }
                )

                val response = ApiClient.usersApi.updateProfile(request)
                android.util.Log.e("ProfileActivity", "updateProfile code=${response.code()} body=${response.body()}")

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    if (response.isSuccessful) {
                        // Guardar avatar actualizado en TokenManager
                        val currentUser = TokenManager.getInstance().getUser()
                        val finalAvatar = avatarUrl ?: currentUser?.avatar
                        val finalBio = if (bio.isEmpty()) currentUser?.bio else bio
                        val finalPhone = if (phone.isEmpty()) currentUser?.phone else phone
                        val user = currentUser?.copy(
                            name = name,
                            bio = finalBio,
                            phone = finalPhone ?: "",
                            avatar = finalAvatar
                        )
                        if (user != null) TokenManager.getInstance().saveUser(user)
                        showToast("Perfil actualizado")
                        finish()
                    } else {
                        showToast("Error al actualizar perfil")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileActivity", "EXCEPCION: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    showToast("Error de conexion")
                }
            }
        }
    }

    private fun logout() {
        CoroutineScope(Dispatchers.IO).launch {
            try { ApiClient.authApi.logout() } catch (_: Exception) { }
        }
        SocketManager.disconnect()
        TokenManager.getInstance().clearAll()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
