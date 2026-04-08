package com.civis.app.ui.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Vista personalizada que muestra barras de onda de audio (estilo WhatsApp).
 * Genera barras con alturas pseudo-aleatorias para simular una onda de audio.
 * Muestra progreso de reproducción coloreando las barras reproducidas de diferente color.
 * Soporta touch para seek (avanzar/retroceder la reproducción).
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Progreso de reproducción (0.0 = inicio, 1.0 = fin) */
    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** Color de las barras NO reproducidas */
    var inactiveColor: Int = 0xFF999999.toInt()
        set(value) { field = value; barPaintInactive.color = value; invalidate() }

    /** Color de las barras reproducidas */
    var activeColor: Int = 0xFFFFFFFF.toInt()
        set(value) { field = value; barPaintActive.color = value; invalidate() }

    /** Callback cuando el usuario toca para hacer seek */
    var onSeek: ((Float) -> Unit)? = null

    /** Indica si se puede hacer seek (solo cuando el audio se está reproduciendo) */
    var isSeekable: Boolean = false

    // Datos de las barras (alturas generadas con seed)
    private val barHeights = mutableListOf<Float>()
    private val barCount = 45

    private val barPaintInactive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inactiveColor
        style = Paint.Style.FILL
    }
    private val barPaintActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = activeColor
        style = Paint.Style.FILL
    }

    private val barRect = RectF()

    private var barsGenerated = false

    init {
        // No generar aquí — generar cuando se asigna la seed
    }

    /**
     * Genera alturas de barras basadas en un seed (ID del mensaje o URL).
     * Así cada audio tiene una onda consistente y diferente.
     */
    fun generateBars(seed: String = "") {
        barHeights.clear()
        // Crear un hash numérico simple del seed para que sea reproducible
        val hash = if (seed.isNotEmpty()) {
            seed.fold(0L) { acc, c -> (acc * 31 + c.code) and 0xFFFFFFFFL }
        } else {
            (Math.random() * Long.MAX_VALUE).toLong()
        }

        var rng = hash
        for (i in 0 until barCount) {
            // Extraer pseudo-aleatorio del hash
            rng = (rng * 1103515245L + 12345L) and 0x7FFFFFFFL
            val rand = (rng % 1000) / 1000f

            // Crear forma de onda natural: más alto en el centro, más bajo en los extremos
            val normalizedPos = i.toFloat() / (barCount - 1) // 0.0 a 1.0
            val centerDist = Math.abs(normalizedPos - 0.5f) * 2f // 0.0 (centro) a 1.0 (extremo)
            val envelope = 1f - centerDist * 0.6f // Atenuación en extremos

            // Añadir variación sinusoidal para que parezca onda real
            val wave = 0.5f + 0.5f * sin(i * 0.4f + (hash % 100) * 0.1f)

            // Combinar todo
            val minHeight = 0.15f + 0.1f * envelope
            val maxHeight = 0.4f + 0.6f * envelope
            val height = minHeight + (maxHeight - minHeight) * (rand * 0.6f + wave * 0.4f)

            barHeights.add(height.coerceIn(0.1f, 1.0f))
        }
        barsGenerated = true
        invalidate()
    }

    /**
     * Establece las alturas de las barras a partir de amplitudes reales del audio.
     * Normaliza los valores al rango [0.05, 1.0].
     */
    fun setBars(amplitudes: List<Float>) {
        barHeights.clear()
        if (amplitudes.isEmpty()) {
            generateBars("")
            return
        }

        // Encontrar el valor máximo para normalizar
        val maxAmp = amplitudes.maxOrNull() ?: 1f
        if (maxAmp <= 0f) {
            generateBars("")
            return
        }

        // Muestrear/downsample a barCount barras
        val samplesPerPixel = amplitudes.size.toFloat() / barCount
        for (i in 0 until barCount) {
            val start = (i * samplesPerPixel).toInt()
            val end = ((i + 1) * samplesPerPixel).toInt().coerceAtMost(amplitudes.size)
            // Tomar el promedio de las amplitudes en este rango
            var sum = 0f
            var count = 0
            for (j in start until end) {
                sum += amplitudes[j]
                count++
            }
            val avg = if (count > 0) sum / count else 0f
            // Normalizar: mapear a rango visible [0.08, 1.0]
            val normalized = (avg / maxAmp)
            val height = 0.08f + normalized * 0.92f
            barHeights.add(height.coerceIn(0.08f, 1.0f))
        }
        barsGenerated = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!barsGenerated || barHeights.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val totalBarWidth = w / barCount
        val gap = totalBarWidth * 0.35f
        val actualBarWidth = totalBarWidth - gap
        val activeBarIndex = (progress * barCount).roundToInt().coerceIn(0, barCount)

        for (i in 0 until barCount) {
            val barHeight = barHeights[i] * h * 0.85f
            val x = i * totalBarWidth + gap / 2f
            val y = (h - barHeight) / 2f

            barRect.set(x, y, x + actualBarWidth, y + barHeight)

            val paint = if (i < activeBarIndex) barPaintActive else barPaintInactive
            canvas.drawRoundRect(barRect, actualBarWidth / 2f, actualBarWidth / 2f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSeekable) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val newProgress = (event.x / width).coerceIn(0f, 1f)
                progress = newProgress
                onSeek?.invoke(newProgress)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Obtener la posición de progreso desde una coordenada X */
    fun getProgressFromX(x: Float): Float {
        return (x / width).coerceIn(0f, 1f)
    }
}
