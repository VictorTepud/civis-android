package com.civis.app.utils

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.IOException

/**
 * RequestBody que reporta progreso de subida via callback.
 * Compatible con OkHttp 4.x / okio 3.x.
 */
class ProgressRequestBody(
    private val requestBody: RequestBody,
    private val onProgress: (percent: Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = requestBody.contentType()

    override fun contentLength(): Long = requestBody.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val contentLength = contentLength()
        val wrappedSink = object : ForwardingSink(sink) {
            private var bytesWritten = 0L

            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                bytesWritten += byteCount
                if (contentLength > 0) {
                    val percent = (bytesWritten * 100 / contentLength).toInt()
                    onProgress(percent)
                }
            }
        }.buffer()
        requestBody.writeTo(wrappedSink)
        wrappedSink.flush()
    }
}
