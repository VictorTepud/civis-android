package com.civis.app.ui.calls

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.appcompat.app.AppCompatActivity
import com.civis.app.R
import com.civis.app.data.api.ApiClient
import com.civis.app.databinding.ActivityIncomingCallBinding
import com.civis.app.services.CallService
import com.civis.app.utils.showToast
import com.civis.app.utils.loadAvatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private var callId: String = ""
    private var callerId: String = ""
    private var callerName: String = ""
    private var callerAvatar: String = ""
    private var callType: String = "voice"
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val bundle = intent.extras
        callId = bundle?.getString("callId") ?: ""
        callerId = bundle?.getString("callerId") ?: ""
        callerName = bundle?.getString("callerName") ?: "Desconocido"
        callerAvatar = bundle?.getString("callerAvatar") ?: ""
        callType = bundle?.getString("callType") ?: "voice"

        binding.tvCallerName.text = callerName
        binding.tvCallType.text = if (callType == "video") "Videollamada entrante" else "Llamada entrante"

        binding.ivCallerAvatar.loadAvatar(callerAvatar)

        startRingtone()
        startVibration()
        setupListeners()
    }

    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri)
            ringtone?.play()
        } catch (_: Exception) {}
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    private fun startVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 1000, 500, 1000)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 1000, 500, 1000), 0)
        }
    }

    private fun stopVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()
    }

    private fun setupListeners() {
        binding.btnAccept.setOnClickListener {
            stopRingtone()
            stopVibration()
            answerCall()
        }

        binding.btnReject.setOnClickListener {
            stopRingtone()
            stopVibration()
            rejectCall()
        }
    }

    private fun answerCall() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.callsApi.answerCall(callId)
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@IncomingCallActivity, CallActivity::class.java).apply {
                        putExtra("receiverId", callerId)
                        putExtra("receiverName", callerName)
                        putExtra("receiverAvatar", callerAvatar)
                        putExtra("callType", callType)
                    }
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error al contestar: ${e.message}")
                    finish()
                }
            }
        }
    }

    private fun rejectCall() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.callsApi.rejectCall(callId)
            } catch (_: Exception) {}
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
        stopVibration()
    }
}
