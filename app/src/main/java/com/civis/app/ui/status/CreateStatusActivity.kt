package com.civis.app.ui.status

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.CreateStatusRequest
import com.civis.app.databinding.ActivityCreateStatusBinding
import com.civis.app.utils.showToast
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
        private const val PICK_IMAGE = 1001
        private const val CAMERA_REQUEST = 1002
        private val colors = listOf("#25D366", "#128C7E", "#075E54", "#34B7F1", "#FF6B6B", "#FFA500", "#9B59B6", "#1ABC9C")
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                startActivityForResult(intent, PICK_IMAGE)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            }
        }

        binding.btnPost.setOnClickListener {
            postStatus()
        }

        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    com.google.android.material.R.id.btn1 -> {
                        statusType = "text"
                        binding.etStatusContent.visibility = View.VISIBLE
                        binding.ivPreviewImage.visibility = View.GONE
                    }
                    com.google.android.material.R.id.btn2 -> {
                        statusType = "image"
                        binding.etStatusContent.visibility = View.GONE
                        binding.btnPickImage.performClick()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null && requestCode == PICK_IMAGE) {
            selectedImageUri = data.data
            binding.ivPreviewImage.setImageURI(selectedImageUri)
            binding.ivPreviewImage.visibility = View.VISIBLE
            statusType = "image"
        }
    }

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
                    val file = java.io.File(selectedImageUri!!.path ?: return@launch)
                    val requestFile = okhttp3.RequestBody.create(
                        okhttp3.MediaType.parse(contentResolver.getType(selectedImageUri!!) ?: "image/*"),
                        file
                    )
                    val body = okhttp3.MultipartBody.Part.createFormData("file", file.name, requestFile)
                    val uploadResponse = ApiClient.uploadApi.uploadStatus(body)
                    if (uploadResponse.isSuccessful) {
                        mediaUrl = uploadResponse.body()?.data?.toString()
                    }
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
