package com.civis.app.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * Extrae las amplitudes reales de un archivo de audio para generar la onda visual.
 * Usa MediaExtractor + MediaCodec para decodificar a PCM y obtener RMS real.
 * Cache en memoria + disco (archivo .waveform al lado del audio) para persistir.
 */
object AudioWaveformExtractor {

    /** Cache en memoria: key = filePath, value = amplitudes normalizadas */
    private val cache = mutableMapOf<String, List<Float>>()

    /** Archivo donde se guardan las amplitudes cacheadas en disco */
    private fun getWaveformFile(audioFile: File): File {
        return File(audioFile.parent, "${audioFile.name}.waveform")
    }

    /** Leer amplitudes desde el archivo .waveform en disco */
    private fun readFromDisk(file: File): List<Float>? {
        val wfFile = getWaveformFile(file)
        if (!wfFile.exists()) return null
        return try {
            val text = wfFile.readText().trim()
            if (text.isEmpty()) return null
            text.split(",").mapNotNull { it.trim().toFloatOrNull() }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /** Guardar amplitudes al archivo .waveform en disco */
    private fun saveToDisk(file: File, amplitudes: List<Float>) {
        try {
            val wfFile = getWaveformFile(file)
            wfFile.writeText(amplitudes.joinToString(","))
        } catch (_: Exception) {}
    }

    /**
     * Extrae amplitudes de un archivo de audio.
     * Retorna una lista de floats (valores RMS normalizados 0.0-1.0).
     * Si hay error, retorna null.
     */
    fun extract(file: File, targetSamples: Int = 200): List<Float>? {
        val key = file.absolutePath

        // 1. Cache en memoria
        cache[key]?.let { return it }

        // 2. Cache en disco
        val diskCached = readFromDisk(file)
        if (diskCached != null) {
            cache[key] = diskCached
            return diskCached
        }

        // 3. Extraer del audio
        val result = extractFromAudio(file, targetSamples) ?: return null

        // Guardar en memoria y disco
        cache[key] = result
        saveToDisk(file, result)
        return result
    }

    /** Extrae amplitudes reales decodificando el audio con MediaCodec */
    private fun extractFromAudio(file: File, targetSamples: Int): List<Float>? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)

            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: run {
                extractor.release()
                null
            } ?: return null

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                extractor.release()
                return null
            }

            if (durationUs <= 0) {
                extractor.release()
                return null
            }

            val mimeType = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val pcmChunks = mutableListOf<ShortArray>()
            var totalSamples = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize <= 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                    outputBufferIndex >= 0 -> {
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val remaining = bufferInfo.size / 2
                                if (remaining > 0) {
                                    val chunk = ShortArray(remaining)
                                    for (i in 0 until remaining) {
                                        if (outputBuffer.remaining() >= 2) {
                                            chunk[i] = outputBuffer.short
                                        }
                                    }
                                    pcmChunks.add(chunk)
                                    totalSamples += remaining
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            if (totalSamples == 0) return null

            val segmentCount = targetSamples.coerceAtMost(totalSamples)
            val samplesPerSegment = totalSamples.toFloat() / segmentCount
            val rmsValues = FloatArray(segmentCount)

            var globalSampleIndex = 0
            for (chunk in pcmChunks) {
                for (sample in chunk) {
                    val normalizedSample = sample.toFloat() / Short.MAX_VALUE
                    val segIndex = (globalSampleIndex / samplesPerSegment).toInt()
                        .coerceIn(0, segmentCount - 1)
                    rmsValues[segIndex] += normalizedSample * normalizedSample
                    globalSampleIndex++
                }
            }

            for (i in 0 until segmentCount) {
                val countInSegment = when {
                    i == segmentCount - 1 -> totalSamples - (i * totalSamples / segmentCount)
                    else -> totalSamples / segmentCount
                }
                if (countInSegment > 0) {
                    rmsValues[i] = kotlin.math.sqrt(rmsValues[i] / countInSegment)
                }
            }

            var maxRms = 0f
            for (v in rmsValues) { if (v > maxRms) maxRms = v }
            if (maxRms <= 0f) return null

            val amplitudes = rmsValues.map { it / maxRms }

            // Suavizado con media móvil de 3 puntos
            val smoothed = mutableListOf<Float>()
            for (i in amplitudes.indices) {
                val prev = amplitudes.getOrElse(i - 1) { amplitudes[i] }
                val curr = amplitudes[i]
                val next = amplitudes.getOrElse(i + 1) { amplitudes[i] }
                smoothed.add((prev + curr * 2 + next) / 4f)
            }

            smoothed
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Extraer desde un path de archivo */
    fun extract(filePath: String, targetSamples: Int = 200): List<Float>? {
        return extract(File(filePath), targetSamples)
    }

    /** Limpiar caches */
    fun clearCache() {
        cache.clear()
    }
}
