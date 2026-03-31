package com.civis.app.ui.calls

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.data.model.InitiateCallRequest
import com.civis.app.data.model.SignalRequest
import com.civis.app.databinding.ActivityCallBinding
import com.civis.app.services.CallService
import com.civis.app.utils.SocketManager
import com.civis.app.utils.showToast
import com.civis.app.utils.toGlideUrl
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var receiverId: String = ""
    private var receiverName: String = ""
    private var receiverAvatar: String = ""
    private var callType: String = "voice"
    private var callId: String = ""
    private var isMuted = false
    private var isSpeakerOn = false
    private var isVideoEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        receiverId = intent.getStringExtra("receiverId") ?: ""
        receiverName = intent.getStringExtra("receiverName") ?: "Desconocido"
        receiverAvatar = intent.getStringExtra("receiverAvatar") ?: ""
        callType = intent.getStringExtra("callType") ?: "voice"

        binding.tvCallerName.text = receiverName
        binding.tvCallStatus.text = if (callType == "video") "Videollamada saliente..." else "Llamada saliente..."

        if (!receiverAvatar.isNullOrEmpty()) {
            Glide.with(this)
                .load(receiverAvatar.toGlideUrl())
                .placeholder(R.drawable.ic_profile)
                .into(binding.ivCallerAvatar)
        }

        if (callType == "voice") {
            binding.btnToggleVideo.visibility = View.GONE
        }

        initiateCall()
        setupListeners()
    }

    private fun initiateCall() {
        val serviceIntent = Intent(this, CallService::class.java).apply {
            putExtra("call_type", callType)
            putExtra("caller_name", receiverName)
        }
        startForegroundService(serviceIntent)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = InitiateCallRequest(
                    receiverId = receiverId,
                    type = callType
                )
                val response = ApiClient.callsApi.initiateCall(request)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val data = response.body()?.data
                        val json = Gson().toJson(data)
                        val jsonObject = Gson().fromJson(json, JSONObject::class.java)
                        callId = jsonObject.optString("id", "")
                        binding.tvCallStatus.text = "Sonando..."
                    } else {
                        showToast("Error al iniciar llamada")
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error de conexión")
                    finish()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnMute.setOnClickListener {
            isMuted = !isMuted
            binding.btnMute.setImageResource(
                if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic
            )
        }

        binding.btnSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            binding.btnSpeaker.setImageResource(
                if (isSpeakerOn) R.drawable.ic_speaker_on else R.drawable.ic_speaker_off
            )
        }

        binding.btnToggleVideo.setOnClickListener {
            isVideoEnabled = !isVideoEnabled
            binding.btnToggleVideo.setImageResource(
                if (isVideoEnabled) R.drawable.ic_video else R.drawable.ic_video_off
            )
        }

        binding.btnEndCall.setOnClickListener {
            endCall()
        }

        SocketManager.on("call_answered") { _ ->
            runOnUiThread {
                binding.tvCallStatus.text = "Conectado"
                startCallTimer()
            }
        }

        SocketManager.on("call_rejected") { _ ->
            runOnUiThread {
                showToast("Llamada rechazada")
                finish()
            }
        }

        SocketManager.on("call_ended") { _ ->
            runOnUiThread {
                showToast("Llamada finalizada")
                finish()
            }
        }
    }

    private fun startCallTimer() {
        binding.chronometer.base = SystemClock.elapsedRealtime()
        binding.chronometer.start()
        binding.chronometer.visibility = View.VISIBLE
    }

    private fun endCall() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (callId.isNotEmpty()) {
                    ApiClient.callsApi.endCall(callId)
                }
            } catch (_: Exception) {}
        }

        binding.chronometer.stop()
        stopService(Intent(this, CallService::class.java))
        SocketManager.off("call_answered")
        SocketManager.off("call_rejected")
        SocketManager.off("call_ended")
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        endCall()
    }
}
