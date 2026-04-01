package com.civis.app.utils

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException
import com.civis.app.config.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SocketManager {

    private const val TAG = "SocketManager"

    private var socket: Socket? = null
    private var shouldConnect = false

    fun connect() {
        shouldConnect = true
        // Solo intentar conectar si hay red
        if (!NetworkMonitor.isConnected.value) {
            Log.d(TAG, "Sin red, se conectará cuando haya conexión")
            return
        }
        doConnect()
    }

    private fun doConnect() {
        if (socket?.connected() == true) return
        try {
            val token = TokenManager.getInstance().getToken()
            val serverUrl = ServerConfig.BASE_URL
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 2000
                reconnectionDelayMax = 10000
                timeout = 30000
                forceNew = false
                if (!token.isNullOrEmpty()) {
                    this.query = "token=$token"
                }
            }
            Log.d(TAG, "Conectando socket a: $serverUrl")

            // Desconectar socket anterior si existe
            socket?.disconnect()
            socket?.off()

            socket = IO.socket(serverUrl, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket conectado al servidor")
                emit("user_online", null)
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket desconectado")
            }

            // Errores de conexión son normales al perder red, solo log info
            socket?.on(Socket.EVENT_CONNECT_ERROR) {
                Log.d(TAG, "Socket: error de conexión (servidor no alcanzable o sin red)")
            }

            socket?.on("reconnect") {
                Log.d(TAG, "Socket reconectado")
                emit("user_online", null)
            }

            socket?.on("reconnect_error") {
                Log.d(TAG, "Socket: reintentando reconexión...")
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e(TAG, "URL inválida: ${e.message}")
        }
    }

    fun disconnect() {
        shouldConnect = false
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    /**
     * Debe llamarse cuando NetworkMonitor detecta que volvió la red.
     */
    fun onNetworkAvailable() {
        if (shouldConnect && !isConnected()) {
            Log.d(TAG, "Red disponible, intentando conectar socket...")
            doConnect()
        }
    }

    fun emit(event: String, data: Any?) {
        if (socket?.connected() == true) {
            if (data != null) {
                socket?.emit(event, if (data is JSONObject) data else JSONObject(gson.toJson(data)))
            } else {
                socket?.emit(event)
            }
        }
        // Sin log de advertencia cuando no hay socket, es normal
    }

    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    fun on(event: String, callback: (Array<Any>) -> Unit) {
        socket?.on(event) { args ->
            callback(args)
        }
    }

    fun off(event: String) {
        socket?.off(event)
    }

    fun isConnected(): Boolean {
        return socket?.connected() == true
    }

    fun getSocket(): Socket? {
        return socket
    }

    private val gson = appGson
}
