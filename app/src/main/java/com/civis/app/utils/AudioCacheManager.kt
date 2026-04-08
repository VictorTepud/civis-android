package com.civis.app.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor centralizado de cache de audio.
 * Descarga audios en segundo plano y notifica progreso al adapter.
 * Los audios se guardan en filesDir/audio_cache/ (persistente, NO es borrado por el sistema).
 */
object AudioCacheManager {

    private val cacheDirMap = ConcurrentHashMap<String, File>()
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private val progressListeners = ConcurrentHashMap<String, (Int) -> Unit>()
    private val progressMap = ConcurrentHashMap<String, Int>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Obtener directorio de cache para un contexto */
    private fun getCacheDir(context: Context): File {
        return cacheDirMap.getOrPut(context.filesDir.absolutePath) {
            val dir = File(context.filesDir, "audio_cache")
            if (!dir.exists()) dir.mkdirs()
            dir
        }
    }

    /** Verificar si un audio ya está descargado */
    fun isCached(context: Context, url: String): Boolean {
        val file = getCachedFile(context, url)
        return file != null && file.exists() && file.length() > 0
    }

    /** Obtener el archivo cacheado si existe */
    fun getCachedFile(context: Context, url: String): File? {
        val cacheName = getCacheFileName(url)
        val file = File(getCacheDir(context), cacheName)
        return if (file.exists() && file.length() > 0) file else null
    }

    /** Construir URL completa a partir de URL relativa */
    private fun buildFullUrl(url: String): String {
        return url.toGlideUrl()
    }

    /** Generar nombre de archivo cache */
    private fun getCacheFileName(url: String): String {
        val name = url.substringAfterLast("/")
        val cleanName = name.substringBefore("?")
        return if (cleanName.contains(".")) cleanName else "${System.currentTimeMillis()}.aac"
    }

    /**
     * Registrar listener de progreso para una URL.
     */
    fun setProgressListener(url: String, listener: ((Int) -> Unit)?) {
        if (listener != null) {
            progressListeners[url] = listener
        } else {
            progressListeners.remove(url)
        }
    }

    /** Obtener porcentaje de descarga actual (0 si no está descargando) */
    fun getProgress(url: String): Int {
        return progressMap[url] ?: 0
    }

    /**
     * Descargar audio para cache offline.
     * Si ya está cacheado, notifica 100% inmediatamente.
     */
    fun download(
        context: Context,
        url: String,
        onProgress: ((Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        // Si ya está cacheado, notificar y terminar
        if (isCached(context, url)) {
            mainHandler.post {
                onProgress?.invoke(100)
                onComplete?.invoke()
            }
            return
        }

        // Si ya se está descargando, solo registrar listener
        if (activeDownloads.containsKey(url)) {
            if (onProgress != null) {
                progressListeners[url] = onProgress
                val current = progressMap[url] ?: 0
                mainHandler.post { onProgress(current) }
            }
            return
        }

        progressListeners[url] = onProgress ?: {}
        progressMap[url] = 0

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val fullUrl = buildFullUrl(url)
                val cacheName = getCacheFileName(url)
                val destFile = File(getCacheDir(context), cacheName)

                if (destFile.exists() && destFile.length() > 0) {
                    withContext(Dispatchers.Main) {
                        progressMap[url] = 100
                        onProgress?.invoke(100)
                        onComplete?.invoke()
                    }
                    return@launch
                }

                val connection = URL(fullUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.connect()
                val totalSize = connection.contentLength

                if (connection.responseCode == 200) {
                    val tempFile = File(getCacheDir(context), "$cacheName.part")
                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                ensureActive()
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalSize > 0) {
                                    val percent = ((downloaded * 100) / totalSize).toInt()
                                    progressMap[url] = percent
                                    mainHandler.post {
                                        progressListeners[url]?.invoke(percent)
                                    }
                                }
                            }
                        }
                    }
                    connection.disconnect()
                    tempFile.renameTo(destFile)

                    progressMap[url] = 100
                    mainHandler.post {
                        progressListeners[url]?.invoke(100)
                        onComplete?.invoke()
                    }
                } else {
                    connection.disconnect()
                    mainHandler.post { onComplete?.invoke() }
                }
            } catch (e: CancellationException) {
                val cacheName = getCacheFileName(url)
                val tempFile = File(getCacheDir(context), "$cacheName.part")
                tempFile.delete()
            } catch (_: Exception) {
                mainHandler.post { onComplete?.invoke() }
            } finally {
                activeDownloads.remove(url)
                progressListeners.remove(url)
                progressMap.remove(url)
            }
        }

        activeDownloads[url] = job
    }

    /** Cancelar descarga en curso */
    fun cancelDownload(url: String) {
        activeDownloads[url]?.cancel()
        activeDownloads.remove(url)
        progressMap.remove(url)
        progressListeners.remove(url)
    }
}
