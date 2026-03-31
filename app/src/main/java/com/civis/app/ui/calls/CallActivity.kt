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
import org.webrtc.*

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

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null

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

        initWebRTC()
        initiateCall()
        setupListeners()
    }

    private fun initWebRTC() {
        val initializationOptions = PeerConnectionFactory.InitializationOptions
            .builder(this)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)

        if (callType == "video") {
            val videoCapturer = createVideoCapturer()
            val videoSource = peerConnectionFactory?.createVideoSource(videoCapturer ?: return)
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video_track", videoSource)
        }

        val iceServers = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                runOnUiThread {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            binding.tvCallStatus.text = "Conectado"
                            startCallTimer()
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            binding.tvCallStatus.text = "Desconectado"
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            endCall()
                        }
                        else -> {}
                    }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            ApiClient.callsApi.sendSignal(callId, SignalRequest(
                                signalType = "ice_candidate",
                                signalData = Gson().toJson(it)
                            ))
                        } catch (_: Exception) {}
                    }
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })

        localAudioTrack?.let { peerConnection?.addTrack(it) }
        localVideoTrack?.let { peerConnection?.addTrack(it) }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return try {
            Camera2Enumerator(this).run {
                deviceNames.firstOrNull { isFrontFacing(it) }?.let {
                    createCapturer(it, null)
                }
            }
        } catch (e: Exception) {
            null
        }
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
                        createAndSendOffer()
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

    private fun createAndSendOffer() {
        if (peerConnection == null) return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (callType == "video") {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                ApiClient.callsApi.sendSignal(callId, SignalRequest(
                                    signalType = "offer",
                                    signalData = sdp?.description ?: ""
                                ))
                            } catch (_: Exception) {}
                        }
                    }
                    override fun onCreateSuccess(p0: String?) {}
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                runOnUiThread { showToast("Error al crear oferta: $error") }
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun setupListeners() {
        binding.btnMute.setOnClickListener {
            isMuted = !isMuted
            localAudioTrack?.setEnabled(!isMuted)
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
            localVideoTrack?.setEnabled(isVideoEnabled)
            binding.btnToggleVideo.setImageResource(
                if (isVideoEnabled) R.drawable.ic_video else R.drawable.ic_video_off
            )
        }

        binding.btnEndCall.setOnClickListener {
            endCall()
        }

        SocketManager.on("call_answered") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            val answer = data.optString("sdp", "")
            val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, answer)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    runOnUiThread {
                        binding.tvCallStatus.text = "Conectado"
                        startCallTimer()
                    }
                }
                override fun onCreateSuccess(p0: String?) {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
            }, remoteSdp)
        }

        SocketManager.on("ice_candidate") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            try {
                val candidate = Gson().fromJson(data.toString(), IceCandidate::class.java)
                peerConnection?.addIceCandidate(candidate)
            } catch (_: Exception) {}
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
        peerConnection?.close()
        peerConnectionFactory?.dispose()
        stopService(Intent(this, CallService::class.java))
        SocketManager.off("call_answered")
        SocketManager.off("ice_candidate")
        SocketManager.off("call_rejected")
        SocketManager.off("call_ended")
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        endCall()
    }
}
