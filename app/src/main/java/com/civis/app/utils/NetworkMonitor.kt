package com.civis.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.civis.app.config.ServerConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Monitorea la conexión a internet Y la disponibilidad del servidor.
 * - isConnected: true si hay internet (WiFi/datos)
 * - isServerReachable: true si el servidor responde
 * La app debe tratar !isServerReachable como offline.
 */
object NetworkMonitor {

    private const val TAG = "NetworkMonitor"

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isServerReachable = MutableStateFlow(false)
    val isServerReachable: StateFlow<Boolean> = _isServerReachable.asStateFlow()

    /** true si hay conexión Y el servidor responde */
    val isAvailable: Boolean get() = _isServerReachable.value

    private var connectivityManager: ConnectivityManager? = null
    private var checkJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isConnected.value = true
            // Cuando vuelve el internet, verificar si el servidor responde
            checkServerReachability()
        }

        override fun onLost(network: Network) {
            _isConnected.value = false
            _isServerReachable.value = false
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _isConnected.value = hasInternet
            if (hasInternet) {
                checkServerReachability()
            } else {
                _isServerReachable.value = false
            }
        }
    }

    fun init(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, networkCallback)

        _isConnected.value = checkCurrentConnection(context)
        if (_isConnected.value) {
            checkServerReachability()
        }
    }

    private fun checkCurrentConnection(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Verifica si el servidor responde con un ping HTTP rápido.
     * Se ejecuta en un hilo de fondo.
     */
    private fun checkServerReachability() {
        checkJob?.cancel()
        checkJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("${ServerConfig.API_URL}auth/verify-token")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("Authorization", "Bearer dummy")
                // No necesitamos que la petición tenga éxito, solo que el servidor responda
                // Un 401 significa que el servidor está vivo
                val responseCode = conn.responseCode
                conn.disconnect()
                val reachable = responseCode in 200..499 // Cualquier código HTTP = servidor vivo
                Log.d(TAG, "Server reachability check: $responseCode → ${if (reachable) "OK" else "FAIL"}")
                _isServerReachable.value = reachable
            } catch (e: Exception) {
                Log.d(TAG, "Server unreachable: ${e.message}")
                _isServerReachable.value = false
            }
        }
    }

    /**
     * Reintenta verificar la conexión con el servidor.
     * Se llama periódicamente o cuando se reintentan operaciones.
     */
    fun retryServerCheck() {
        if (_isConnected.value) {
            checkServerReachability()
        }
    }

    fun unregister() {
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        checkJob?.cancel()
    }
}
