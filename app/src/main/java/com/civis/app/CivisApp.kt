package com.civis.app

import android.app.Application
import com.civis.app.data.local.LocalDatabase
import com.civis.app.utils.NetworkMonitor
import com.civis.app.utils.OfflineSyncManager
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager

class CivisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)

        // Inicializar base de datos local (SQLite directo)
        val database = LocalDatabase.getInstance(this)
        OfflineSyncManager.init(database)

        // Monitorear estado de conexión
        NetworkMonitor.init(this)

        // Conectar socket si hay sesión activa
        if (TokenManager.getInstance().isLoggedIn()) {
            SocketManager.connect()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        SocketManager.disconnect()
        NetworkMonitor.unregister()
    }
}
