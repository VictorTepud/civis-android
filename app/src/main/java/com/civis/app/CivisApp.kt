package com.civis.app

import android.app.Application
import com.civis.app.data.local.LocalDatabase
import com.civis.app.services.NotificationHelper
import com.civis.app.utils.NetworkMonitor
import com.civis.app.utils.OfflineSyncManager
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CivisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)

        // Crear canales de notificación al iniciar
        NotificationHelper.createChannels(this)

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)

        // Inicializar base de datos local (SQLite directo)
        val database = LocalDatabase.getInstance(this)
        OfflineSyncManager.init(database)

        // Monitorear estado de conexión
        NetworkMonitor.init(this)

        // Conectar socket SOLO cuando haya red y haya sesión activa
        if (TokenManager.getInstance().isLoggedIn()) {
            // Esperar a que NetworkMonitor detecte la red antes de conectar
            CoroutineScope(Dispatchers.Main).launch {
                NetworkMonitor.isConnected.collect { connected ->
                    if (connected) {
                        SocketManager.onNetworkAvailable()
                    }
                }
            }
            SocketManager.connect() // Esto no conecta si no hay red, marca shouldConnect=true

            // Obtener y enviar FCM token al servidor
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    sendFcmTokenToServer(token)
                }
            }
        }
    }

    private fun sendFcmTokenToServer(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiClient = com.civis.app.data.api.ApiClient
                val response = apiClient.usersApi.updateFcmToken(
                    com.civis.app.data.model.FcmTokenRequest(fcmToken = token)
                )
                if (response.isSuccessful) {
                    android.util.Log.d("CivisApp", "FCM token enviado al servidor")
                } else {
                    android.util.Log.e("CivisApp", "Error enviando FCM token: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("CivisApp", "Error enviando FCM token: ${e.message}")
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        SocketManager.disconnect()
        NetworkMonitor.unregister()
    }
}
