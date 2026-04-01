package com.civis.app.ui.status

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.CreateStatusRequest
import com.civis.app.databinding.ActivityCreateStatusBinding
import com.civis.app.utils.hasImagePermission
import com.civis.app.utils.imagePermissions
import com.civis.app.utils.showToast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateStatusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateStatusBinding
    private var selectedColor = "#25D366"
    private var selectedImageUri: Uri? = null
    private var statusType = "text"

    companion object {
        private val colors = listOf("#25D366", "#128C7E", "#075E54", "#34B7F1", "#FF6B6B", "#FFA500", "#9B59B6", "#1ABC9C")
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        selectedImageUri = uri
        uri?.let {
            binding.ivPreviewImage.setImageURI(it)
            binding.ivPreviewImage.visibility = View.VISIBLE
            statusType = "image"
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pickImageLauncher.launch("image/*")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nuevo Estado"
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupColorPicker()
        setupListeners()
    }

    private fun setupColorPicker() {
        colors.forEach { color ->
            val view = View(this).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.color_picker_size),
                    resources.getDimensionPixelSize(R.dimen.color_picker_size)
                )
                setBackgroundColor(Color.parseColor(color))
                setOnClickListener {
                    selectedColor = color
                    binding.etStatusContent.setBackgroundColor(Color.parseColor(color))
                }
            }
            binding.colorPickerContainer.addView(view)
        }
    }

    private fun setupListeners() {
        binding.btnPickImage.setOnClickListener {
            if (hasImagePermission()) {
                pickImageLauncher.launch("image/*")
            } else {
                permissionLauncher.launch(imagePermissions().first())
            }
        }

        binding.btnPost.setOnClickListener {
            postStatus()
        }

        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnText -> {
                        statusType = "text"
                        binding.etStatusContent.visibility = View.VISIBLE
                        binding.ivPreviewImage.visibility = View.GONE
                    }
                    R.id.btnImage -> {
                        statusType = "image"
                        binding.etStatusContent.visibility = View.GONE
                        binding.btnPickImage.performClick()
                    }
                }
            }
        }
    }

    // onActivityResult ya no es necesario — se usa Activity Result API


    private fun postStatus() {
        val content = binding.etStatusContent.text.toString().trim()

        if (statusType == "text" && content.isEmpty()) {
            showToast("Escribe algo para tu estado")
            return
        }

        if (statusType == "image" && selectedImageUri == null) {
            showToast("Selecciona una imagen")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnPost.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var mediaUrl: String? = null
                if (statusType == "image" && selectedImageUri != null) {
                    val inputStream = contentResolver.openInputStream(selectedImageUri!!) ?: return@launch
                    val tempFile = java.io.File(cacheDir, "status_${System.currentTimeMillis()}.jpg")
                    tempFile.outputStream().use { out -> inputStream.copyTo(out) }
                    inputStream.close()
                    val mediaType = (contentResolver.getType(selectedImageUri!!) ?: "image/jpeg").toMediaType()
                    val requestFile = tempFile.asRequestBody(mediaType)
                    val body = okhttp3.MultipartBody.Part.createFormData("status", tempFile.name, requestFile)
                    val uploadResponse = ApiClient.uploadApi.uploadStatus(body)
                    if (uploadResponse.isSuccessful) {
                        val data = uploadResponse.body()?.data
                        val dataMap = data as? Map<*, *>
                        mediaUrl = dataMap?.get("url") as? String
                    }
                    tempFile.delete()
                }

                val request = CreateStatusRequest(
                    type = statusType,
                    content = content.ifEmpty { null },
                    mediaUrl = mediaUrl,
                    backgroundColor = selectedColor
                )

                val response = ApiClient.statusApi.createStatus(request)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPost.isEnabled = true
                    if (response.isSuccessful) {
                        showToast("Estado publicado")
                        finish()
                    } else {
                        showToast("Error al publicar estado")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPost.isEnabled = true
                    showToast("Error: ${e.message}")
                }
            }
        }
    }
}
