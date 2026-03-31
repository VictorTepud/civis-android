package com.civis.app

import android.app.Application
import com.civis.app.utils.SocketManager
import com.civis.app.utils.TokenManager

class CivisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TokenManager.init(this)
        if (TokenManager.getInstance().isLoggedIn()) {
            SocketManager.connect()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        SocketManager.disconnect()
    }
}
