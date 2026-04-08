package com.civis.app.ui.media

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.MediaController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.databinding.ActivityMediaViewerBinding
import com.civis.app.utils.VideoCacheManager
import com.civis.app.utils.toGlideUrl

class MediaViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaViewerBinding
    private var isFullscreen = false
    private var isLandscape = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra("url") ?: ""
        val type = intent.getStringExtra("type") ?: "image"
        val fileName = intent.getStringExtra("fileName") ?: ""

        // Configurar cierre
        binding.ivClose.setOnClickListener { finish() }
        if (fileName.isNotEmpty()) {
            binding.tvFileName.text = fileName
            binding.tvFileName.visibility = View.VISIBLE
        } else {
            binding.tvFileName.visibility = View.GONE
        }

        if (type == "video") {
            setupVideo(url)
        } else {
            setupImage(url)
        }
    }

    // ====== IMAGEN CON ZOOM ======

    private fun setupImage(url: String) {
        binding.ivFullscreenImage.visibility = View.VISIBLE
        binding.videoView.visibility = View.GONE
        binding.ivFullscreenVideo.visibility = View.GONE

        // Doble toque en la imagen para ocultar/mostrar toolbar
        binding.ivFullscreenImage.onSingleTapListener = {
            toggleToolbarVisibility()
        }

        Glide.with(this)
            .load(url.toGlideUrl())
            .placeholder(R.drawable.ic_camera)
            .error(R.drawable.ic_camera)
            .into(binding.ivFullscreenImage)
    }

    // ====== VIDEO CON FULLSCREEN ======

    private fun setupVideo(url: String) {
        binding.ivFullscreenImage.visibility = View.GONE
        binding.videoView.visibility = View.VISIBLE
        binding.ivFullscreenVideo.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE

        // Botón fullscreen: alterna landscape/portrait
        binding.ivFullscreenVideo.setOnClickListener {
            toggleVideoFullscreen()
        }

        // Verificar caché
        val cachedFile = VideoCacheManager.getCachedFile(this, url)
        if (cachedFile != null) {
            playVideo(cachedFile.absolutePath)
        } else if (isNetworkAvailable()) {
            val fullUrl = url.toGlideUrl()
            playVideo(fullUrl)
            VideoCacheManager.download(this, url)
        } else {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Video no disponible sin conexión", Toast.LENGTH_LONG).show()
        }

        // Toque en el video para ocultar/mostrar toolbar y controles
        binding.videoView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                toggleToolbarVisibility()
                mediaController?.show()
                resetAutoHide()
            }
            false
        }
    }

    private fun playVideo(path: String) {
        binding.videoView.setVideoPath(path)

        mediaController = MediaController(this).apply {
            setAnchorView(binding.root)
            setMediaPlayer(binding.videoView)
        }
        binding.videoView.setMediaController(mediaController)
        binding.videoView.keepScreenOn = true

        binding.videoView.setOnPreparedListener { mp: MediaPlayer ->
            mp.setScreenOnWhilePlaying(true)
            mp.start()
            binding.progressBar.visibility = View.GONE
            resetAutoHide()
        }

        binding.videoView.setOnErrorListener { _: MediaPlayer, _: Int, _: Int ->
            binding.progressBar.visibility = View.GONE
            if (!isNetworkAvailable()) {
                Toast.makeText(
                    this@MediaViewerActivity,
                    "Error de conexión. El video no se pudo cargar.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@MediaViewerActivity,
                    "Error al reproducir video",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        }

        binding.videoView.start()
    }

    /** Alterna entre landscape y portrait para fullscreen */
    private fun toggleVideoFullscreen() {
        if (isLandscape) {
            // Volver a portrait
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isLandscape = false
            isFullscreen = false
            binding.ivFullscreenVideo.setImageResource(R.drawable.ic_fullscreen)
            exitImmersiveMode()
        } else {
            // Ir a landscape fullscreen
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isLandscape = true
            isFullscreen = true
            binding.ivFullscreenVideo.setImageResource(R.drawable.ic_fullscreen_exit)
            enterImmersiveMode()
        }
    }

    /** Entrar a modo inmersivo (ocultar barra de estado y navegación) */
    private fun enterImmersiveMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
        // Ocultar toolbar al entrar a fullscreen
        binding.toolbar.visibility = View.GONE
    }

    /** Salir del modo inmersivo */
    private fun exitImmersiveMode() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        // Mostrar toolbar al salir
        binding.toolbar.visibility = View.VISIBLE
    }

    /** Mostrar/ocultar toolbar al tocar */
    private fun toggleToolbarVisibility() {
        if (binding.toolbar.visibility == View.VISIBLE) {
            binding.toolbar.visibility = View.GONE
        } else {
            binding.toolbar.visibility = View.VISIBLE
            resetAutoHide()
        }
    }

    /** Auto-ocultar toolbar después de 3 segundos */
    private fun resetAutoHide() {
        hideRunnable?.let { uiHandler.removeCallbacks(it) }
        hideRunnable = Runnable {
            if (binding.toolbar.visibility == View.VISIBLE) {
                binding.toolbar.visibility = View.GONE
            }
        }
        uiHandler.postDelayed(hideRunnable!!, 3000)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Si el usuario rotó manualmente el teléfono mientras ve un video
        if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && !isLandscape) {
            isLandscape = true
            isFullscreen = true
            binding.ivFullscreenVideo.setImageResource(R.drawable.ic_fullscreen_exit)
            enterImmersiveMode()
        } else if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT && isLandscape) {
            isLandscape = false
            isFullscreen = false
            binding.ivFullscreenVideo.setImageResource(R.drawable.ic_fullscreen)
            exitImmersiveMode()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideRunnable?.let { uiHandler.removeCallbacks(it) }
        try { binding.videoView.stopPlayback() } catch (_: Exception) {}
        mediaController = null
    }
}
