package com.bobchomp.firelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight, GPU-friendly cinematic reveal for the original Couchy TV app icon.
 * Designed for older Fire TV hardware: no video, large bitmaps, or Compose tree.
 */
class CouchyIntroView(context: Context) : View(context) {
    private val startAt = SystemClock.uptimeMillis()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = dp(17f)
        letterSpacingCompat = 0.22f
    }
    private var icon: Drawable? = runCatching {
        context.packageManager.getApplicationIcon("com.conreo.couchytv").mutate()
    }.getOrNull()
    private var glowShader: Shader? = null
    private var spectrumShader: Shader? = null
    private val matrix = Matrix()
    private val bounds = RectF()
    private var cx = 0f
    private var cy = 0f
    private var iconSize = 0f

    private val frame = object : Runnable {
        override fun run() {
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postOnAnimation(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h * 0.47f
        iconSize = min(w * 0.23f, h * 0.30f).coerceAtLeast(dp(92f))
        glowShader = RadialGradient(
            cx, cy, min(w, h) * 0.62f,
            intArrayOf(Color.rgb(29, 52, 94), Color.rgb(9, 15, 31), Color.rgb(3, 5, 13)),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        spectrumShader = LinearGradient(
            -w.toFloat(), 0f, w.toFloat(), 0f,
            intArrayOf(
                Color.TRANSPARENT, Color.rgb(63, 155, 255),
                Color.rgb(156, 119, 255), Color.rgb(255, 111, 190),
                Color.rgb(255, 205, 128), Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.22f, 0.43f, 0.62f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        icon?.setBounds(0, 0, iconSize.toInt(), iconSize.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val elapsed = (SystemClock.uptimeMillis() - startAt) / 1000f
        val reveal = (elapsed / 4.6f).coerceIn(0f, 1f)
        val idlePulse = 0.5f + 0.5f * sin(elapsed * 1.15f)

        bgPaint.shader = glowShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null

        // Soft orbital glass rings expand during the reveal, then settle into a slow pulse.
        val ringProgress = if (elapsed < 4.6f) reveal else 1f
        for (i in 0..2) {
            val phase = (ringProgress + i * 0.19f) % 1f
            val radius = iconSize * (0.70f + phase * 1.22f)
            ringPaint.color = Color.argb(
                (22 + 30 * (1f - phase) * (0.55f + idlePulse * 0.45f)).toInt(),
                166, 205, 255
            )
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }

        // A single prismatic light sweep crosses the icon like refracted glass.
        if (elapsed < 5.0f) {
            val x = -width * 0.8f + width * 2.2f * (elapsed / 5f)
            matrix.setTranslate(x, 0f)
            spectrumShader?.setLocalMatrix(matrix)
            sweepPaint.shader = spectrumShader
            sweepPaint.alpha = (190 * (1f - (elapsed / 5f) * 0.25f)).toInt()
            val bandY = cy - iconSize * 0.72f
            canvas.drawRect(0f, bandY, width.toFloat(), bandY + iconSize * 1.45f, sweepPaint)
            sweepPaint.shader = null
        }

        val iconAlpha = (reveal * 255).toInt()
        val scale = 0.72f + 0.28f * reveal + (if (elapsed >= 4.6f) idlePulse * 0.012f else 0f)
        val size = iconSize * scale
        icon?.let { drawable ->
            val save = canvas.save()
            canvas.translate(cx - size / 2f, cy - size / 2f)
            canvas.scale(size / iconSize, size / iconSize)
            drawable.alpha = iconAlpha
            drawable.setBounds(0, 0, iconSize.toInt(), iconSize.toInt())
            drawable.draw(canvas)
            canvas.restoreToCount(save)
            drawable.alpha = 255
        }

        val titleAlpha = (reveal * 255).toInt()
        textPaint.alpha = titleAlpha
        textPaint.textSize = dp(17f)
        canvas.drawText("COUCHY TV", cx, cy + iconSize * 0.78f, textPaint)
        textPaint.alpha = 255
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    // Paint.letterSpacing is unavailable on older API levels; keep this as a no-op.
    private var Paint.letterSpacingCompat: Float
        get() = 0f
        set(@Suppress("UNUSED_PARAMETER") value) {}
}
