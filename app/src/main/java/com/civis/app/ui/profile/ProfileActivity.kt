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
            showToast("Permiso denegado para acceder a imágenes")
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

    // onActivityResult ya no es necesario — se usa Activity Result API

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
                    val inputStream = contentResolver.openInputStream(selectedAvatarUri!!) ?: return@launch
                    val tempFile = java.io.File(cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                    tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                    inputStream.close()
                    val mediaType = (contentResolver.getType(selectedAvatarUri!!) ?: "image/jpeg").toMediaType()
                    val requestFile = tempFile.asRequestBody(mediaType)
                    val body = okhttp3.MultipartBody.Part.createFormData("avatar", tempFile.name, requestFile)
                    val uploadResponse = ApiClient.uploadApi.uploadAvatar(body)
                    if (uploadResponse.isSuccessful) {
                        val data = uploadResponse.body()?.data
                        val dataMap = data as? Map<*, *>
                        avatarUrl = dataMap?.get("url") as? String
                    }
                    tempFile.delete()
                }

                val request = UpdateProfileRequest(
                    name = name,
                    bio = bio.ifEmpty { null },
                    phone = phone.ifEmpty { null },
                    avatar = avatarUrl
                )

                val response = ApiClient.usersApi.updateProfile(request)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    if (response.isSuccessful) {
                        showToast("Perfil actualizado")
                        val user = TokenManager.getInstance().getUser()?.copy(
                            name = name, bio = bio, phone = phone, avatar = avatarUrl
                        )
                        if (user != null) TokenManager.getInstance().saveUser(user)
                        finish()
                    } else {
                        showToast("Error al actualizar perfil")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    showToast("Error de conexión")
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
