package com.civis.app.utils

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

object SocketManager {

    private const val TAG = "SocketManager"
    private const val SERVER_URL = "http://10.0.2.2:3000"

    private var socket: Socket? = null

    fun connect() {
        if (socket?.connected() == true) return
        try {
            val token = TokenManager.getInstance().getToken()
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                timeout = 30000
                forceNew = true
                if (!token.isNullOrEmpty()) {
                    this.query = "token=$token"
                }
            }
            socket = IO.socket(SERVER_URL, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Conectado al servidor")
                emit("user_online", null)
            }

            socket?.on(Socket.EVENT_DISCONNECT) { args ->
                Log.d(TAG, "Desconectado del servidor: ${args.joinToString()}")
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Error de conexión: ${args.joinToString()}")
            }

            socket?.on("reconnect") { args ->
                Log.d(TAG, "Reconectado: ${args.joinToString()}")
                emit("user_online", null)
            }

            socket?.on("reconnect_error") { args ->
                Log.e(TAG, "Error de reconexión: ${args.joinToString()}")
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e(TAG, "URL inválida: ${e.message}")
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    fun emit(event: String, data: Any?) {
        if (socket?.connected() == true) {
            if (data != null) {
                socket?.emit(event, if (data is JSONObject) data else JSONObject(gson.toJson(data)))
            } else {
                socket?.emit(event)
            }
        } else {
            Log.w(TAG, "Socket no conectado. No se puede emitir: $event")
        }
    }

    fun emit(event: String, data: JSONObject) {
        if (socket?.connected() == true) {
            socket?.emit(event, data)
        } else {
            Log.w(TAG, "Socket no conectado. No se puede emitir: $event")
        }
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

    private val gson = com.google.gson.Gson()
}
