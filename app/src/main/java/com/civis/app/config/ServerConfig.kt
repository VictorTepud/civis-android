package com.civis.app.config

/**
 * Configuración centralizada del servidor Civis.
 *
 * IMPORTANTE: Cambia SERVER_IP a la IP de tu computadora en la red local.
 *
 * Como encontrar tu IP:
 *   - Windows: abre CMD y ejecuta  ipconfig  (busca "IPv4")
 *   - Linux/Mac: abre terminal y ejecuta  ifconfig  o  ip addr
 *
 * Para el emulador de Android Studio usa: 10.0.2.2
 * Para un dispositivo real en tu red usa: 192.168.x.x  (tu IP local)
 */
object ServerConfig {

    // >>> CAMBIA ESTA IP A LA IP DE TU COMPUTADORA <<<
    const val SERVER_IP = "192.168.0.113"

    const val SERVER_PORT = 3000

    val BASE_URL: String
        get() = "http://$SERVER_IP:$SERVER_PORT"

    val API_URL: String
        get() = "$BASE_URL/api/"
}
