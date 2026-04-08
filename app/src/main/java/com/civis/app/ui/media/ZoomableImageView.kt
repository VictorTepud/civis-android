package com.civis.app.ui.media

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.animation.ValueAnimator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * ImageView personalizado con soporte para:
 * - Pinch-to-zoom
 * - Double-tap para zoom
 * - Pan/drag cuando está ampliado
 * - Doble toque para volver al tamaño original
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 5.0f
        private const val DOUBLE_TAP_SCALE = 2.5f
        private const val ANIMATION_DURATION = 200L
    }

    private val matrix = Matrix()
    private val lastTouchPoint = PointF()
    private var mode = Mode.NONE
    private var lastScale = 1f

    private enum class Mode { NONE, DRAG, ZOOM }

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    private var currentScale = 1f
    private var isZoomed = false

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = matrix

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.OnScaleGestureListener {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newScale = currentScale * scaleFactor

                if (newScale >= baseScale * 0.5f && newScale <= baseScale * MAX_SCALE) {
                    lastScale = scaleFactor
                    matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                    currentScale = newScale
                    isZoomed = currentScale > baseScale + 0.01f
                    imageMatrix = matrix
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {}
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isZoomed) {
                    // Volver al tamaño original con animación
                    animateZoom(MIN_SCALE, width / 2f, height / 2f)
                } else {
                    // Zoom al punto tocado
                    animateZoom(DOUBLE_TAP_SCALE, e.x, e.y)
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Notificar al listener del toque simple
                onSingleTapListener?.invoke()
                return true
            }
        })

        setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchPoint.set(event.x, event.y)
                    mode = Mode.DRAG
                }

                MotionEvent.ACTION_MOVE -> {
                    if (mode == Mode.DRAG && isZoomed) {
                        val dx = event.x - lastTouchPoint.x
                        val dy = event.y - lastTouchPoint.y

                        val matrixValues = FloatArray(9)
                        matrix.getValues(matrixValues)
                        val transX = matrixValues[Matrix.MTRANS_X]
                        val transY = matrixValues[Matrix.MTRANS_Y]
                        val scaleX = matrixValues[Matrix.MSCALE_X]
                        val scaleY = matrixValues[Matrix.MSCALE_Y]

                        // Calcular límites del pan
                        val scaledWidth = drawable?.intrinsicWidth?.times(scaleX) ?: width.toFloat()
                        val scaledHeight = drawable?.intrinsicHeight?.times(scaleY) ?: height.toFloat()
                        val boundsWidth = width.toFloat()
                        val boundsHeight = height.toFloat()

                        // Permitir pan solo cuando la imagen es más grande que la vista
                        val newX = when {
                            scaledWidth <= boundsWidth -> boundsWidth / 2f - scaledWidth / 2f
                            else -> transX + dx
                        }
                        val newY = when {
                            scaledHeight <= boundsHeight -> boundsHeight / 2f - scaledHeight / 2f
                            else -> transY + dy
                        }

                        // Limitar el pan para no salir de los bordes
                        val clampedX = when {
                            scaledWidth <= boundsWidth -> newX
                            else -> newX.coerceIn(boundsWidth - scaledWidth, 0f)
                        }
                        val clampedY = when {
                            scaledHeight <= boundsHeight -> newY
                            else -> newY.coerceIn(boundsHeight - scaledHeight, 0f)
                        }

                        matrix.postTranslate(clampedX - transX, clampedY - transY)
                        imageMatrix = matrix
                    }
                    lastTouchPoint.set(event.x, event.y)
                }

                MotionEvent.ACTION_UP -> {
                    mode = Mode.NONE
                    // Si el scale bajó por debajo del baseScale, resetear
                    if (currentScale < baseScale * 0.8f) {
                        resetZoom()
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    mode = Mode.NONE
                }
            }
            true // Consumir el evento
        }
    }

    /** Listener para toque simple */
    var onSingleTapListener: (() -> Unit)? = null

    /** El scale base calculado por fitCenter */
    private var baseScale = 1f

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        // Re-centrar la imagen cuando se carga
        post { resetZoom() }
    }

    /** Resetear al tamaño original centrado */
    fun resetZoom() {
        if (width == 0 || height == 0) return
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val drawableWidth = d.intrinsicWidth.toFloat()
        val drawableHeight = d.intrinsicHeight.toFloat()

        if (drawableWidth <= 0 || drawableHeight <= 0) return

        val scale = min(viewWidth / drawableWidth, viewHeight / drawableHeight)
        baseScale = scale
        val dx = (viewWidth - drawableWidth * scale) / 2f
        val dy = (viewHeight - drawableHeight * scale) / 2f

        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        imageMatrix = matrix
        currentScale = scale
        isZoomed = false
    }

    /** Animación suave de zoom a un scale objetivo */
    private fun animateZoom(targetScale: Float, focusX: Float, focusY: Float) {
        val d = drawable ?: return
        val drawableWidth = d.intrinsicWidth.toFloat()
        val drawableHeight = d.intrinsicHeight.toFloat()

        val actualTarget = if (targetScale == MIN_SCALE) baseScale else baseScale * targetScale

        // Guardar estado actual
        val startScale = currentScale
        val matrixValues = FloatArray(9)
        matrix.getValues(matrixValues)
        val startTransX = matrixValues[Matrix.MTRANS_X]
        val startTransY = matrixValues[Matrix.MTRANS_Y]

        // Calcular posición final
        val endTransX: Float
        val endTransY: Float
        if (targetScale == MIN_SCALE) {
            // Centrar
            val scaledW = drawableWidth * baseScale
            val scaledH = drawableHeight * baseScale
            endTransX = (width.toFloat() - scaledW) / 2f
            endTransY = (height.toFloat() - scaledH) / 2f
        } else {
            // Mantener foco en el punto tocado
            endTransX = focusX - (focusX - startTransX) * (actualTarget / startScale)
            endTransY = focusY - (focusY - startTransY) * (actualTarget / startScale)
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animator ->
                val frac = animator.animatedValue as Float
                val interpolatedScale = startScale + (actualTarget - startScale) * frac
                val interpTransX = startTransX + (endTransX - startTransX) * frac
                val interpTransY = startTransY + (endTransY - startTransY) * frac

                matrix.reset()
                matrix.postScale(interpolatedScale, interpolatedScale)
                matrix.postTranslate(interpTransX, interpTransY)
                imageMatrix = matrix
                currentScale = interpolatedScale
                isZoomed = interpolatedScale > baseScale + 0.01f
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { resetZoom() }
    }
}
