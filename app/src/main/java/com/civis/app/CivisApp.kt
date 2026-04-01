package com.civis.app

import android.app.Application
import com.civis.app.data.local.LocalDatabase
import com.civis.app.utils.NetworkMonitor
import com.civis.app.utils.OfflineSyncManager
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CivisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)

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
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        SocketManager.disconnect()
        NetworkMonitor.unregister()
    }
}
