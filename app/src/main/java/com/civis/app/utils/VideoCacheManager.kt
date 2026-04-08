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
 * Gestor centralizado de cache de videos.
 * Descarga videos en segundo plano y notifica progreso al adapter.
 * Los videos se guardan en filesDir/video_cache/ (persistente, NO es borrado por el sistema).
 */
object VideoCacheManager {

    private val cacheDirMap = ConcurrentHashMap<String, File>()
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private val progressListeners = ConcurrentHashMap<String, (Int) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Obtener directorio de cache para un contexto */
    private fun getCacheDir(context: Context): File {
        return cacheDirMap.getOrPut(context.filesDir.absolutePath) {
            val dir = File(context.filesDir, "video_cache")
            if (!dir.exists()) dir.mkdirs()
            dir
        }
    }

    /** Verificar si un video ya está descargado */
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
        return if (cleanName.contains(".")) cleanName else "${System.currentTimeMillis()}.mp4"
    }

    /**
     * Registrar listener de progreso para una URL.
     * Se llama desde el hilo principal.
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

    private val progressMap = ConcurrentHashMap<String, Int>()

    /**
     * Descargar video para cache offline.
     * Si ya se está descargando, no hace nada (usa el Job existente).
     *
     * @param context Contexto de la actividad
     * @param url URL del video (relativa o completa)
     * @param onProgress Callback con porcentaje (0-100), llamado en hilo principal
     * @param onComplete Callback cuando termina, llamado en hilo principal
     */
    fun download(
        context: Context,
        url: String,
        onProgress: ((Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        // Si ya está cacheado, notificar 100% y terminar
        if (isCached(context, url)) {
            onProgress?.let { mainHandler.post { it(100) } }
            onComplete?.let { mainHandler.post { it() } }
            return
        }

        // Si ya se está descargando, solo registrar listener
        if (activeDownloads.containsKey(url)) {
            if (onProgress != null) {
                progressListeners[url] = onProgress
                // Enviar progreso actual
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
                connection.readTimeout = 120000
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
                // Cancelado — limpiar archivo temporal
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

    /**
     * Cancelar descarga en curso para una URL.
     */
    fun cancelDownload(url: String) {
        activeDownloads[url]?.cancel()
        activeDownloads.remove(url)
        progressMap.remove(url)
        progressListeners.remove(url)
    }

    /** Obtener tamaño del archivo cacheado en bytes */
    fun getCachedSize(context: Context, url: String): Long {
        val file = getCachedFile(context, url)
        return file?.length() ?: 0L
    }

    /** Formatear bytes a texto legible */
    fun formatSize(bytes: Long): String {
        return if (bytes < 1024) "$bytes B"
        else if (bytes < 1024 * 1024) String.format("%.1f KB", bytes / 1024.0)
        else String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
