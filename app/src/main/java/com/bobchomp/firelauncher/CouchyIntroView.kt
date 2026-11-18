package com.bobchomp.firelauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Short, original cinematic ident for Couchy. The reveal is ~3 seconds; after
 * that the glass continues a slow, quiet drift until Couchy takes the screen.
 */
class CouchyIntroView(context: Context) : View(context) {
    private val startAt = SystemClock.uptimeMillis()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = dp(17f)
        letterSpacingCompat = 0.2f
    }
    private val icon: Drawable? = runCatching {
        context.packageManager.getApplicationIcon("com.conreo.couchytv").mutate()
    }.getOrNull()
    private var glowShader: Shader? = null
    private var spectrumShader: Shader? = null
    private val matrix = Matrix()
    private val card = RectF()
    private val iconRect = RectF()
    private val iconPath = Path()
    private var cx = 0f
    private var cy = 0f
    private var iconSize = 0f
    private var sound: AudioTrack? = null
    private val main = Handler(Looper.getMainLooper())

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
        playOriginalImpact()
        postOnAnimation(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        main.removeCallbacksAndMessages(null)
        runCatching { sound?.stop() }
        runCatching { sound?.release() }
        sound = null
        super.onDetachedFromWindow()
    }

    private fun playOriginalImpact() {
        // Synthesised locally: a soft sub hit, a higher body hit and a short airy tail.
        // It is an original sound cue, not a recording or sample from another service.
        runCatching {
            val rate = 24000
            val count = (rate * 1.32f).toInt()
            val pcm = ShortArray(count)
            for (i in 0 until count) {
                val t = i.toFloat() / rate
                val hit1 = exp(-t * 7.5f) * sin(2.0 * Math.PI * (48.0 * t - 8.0 * t * t)).toFloat()
                val dt = t - 0.48f
                val hit2 = if (dt >= 0f) exp(-dt * 11f) * sin(2.0 * Math.PI * (92.0 * dt - 12.0 * dt * dt)).toFloat() else 0f
                val air = if (t > 0.5f) exp(-(t - 0.5f) * 5.5f) * sin(2.0 * Math.PI * (310.0 * t)).toFloat() * 0.08f else 0f
                val sample = (hit1 * 0.48f + hit2 * 0.28f + air).coerceIn(-0.9f, 0.9f)
                pcm[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack(attrs, format, pcm.size * 2, AudioTrack.MODE_STATIC, AudioTrack.AUDIO_SESSION_ID_GENERATE)
            track.write(pcm, 0, pcm.size)
            track.setVolume(0.72f)
            sound = track
            track.play()
            main.postDelayed({
                runCatching { track.stop() }
                runCatching { track.release() }
                if (sound === track) sound = null
            }, 1600L)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h * 0.45f
        iconSize = min(w * 0.25f, h * 0.31f).coerceAtLeast(dp(104f))
        card.set(cx - iconSize * 0.62f, cy - iconSize * 0.62f, cx + iconSize * 0.62f, cy + iconSize * 0.62f)
        iconRect.set(cx - iconSize * 0.50f, cy - iconSize * 0.50f, cx + iconSize * 0.50f, cy + iconSize * 0.50f)
        iconPath.reset()
        iconPath.addRoundRect(iconRect, iconSize * 0.22f, iconSize * 0.22f, Path.Direction.CW)
        glowShader = RadialGradient(
            cx, cy, min(w, h) * 0.72f,
            intArrayOf(Color.rgb(37, 64, 111), Color.rgb(10, 17, 35), Color.rgb(2, 4, 11)),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        spectrumShader = LinearGradient(
            -w.toFloat(), 0f, w.toFloat(), 0f,
            intArrayOf(Color.TRANSPARENT, Color.rgb(83, 177, 255), Color.rgb(171, 139, 255),
                Color.rgb(255, 153, 208), Color.rgb(255, 224, 174), Color.TRANSPARENT),
            floatArrayOf(0f, 0.22f, 0.43f, 0.62f, 0.78f, 1f), Shader.TileMode.CLAMP
        )
        icon?.setBounds(0, 0, iconSize.toInt(), iconSize.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val elapsed = (SystemClock.uptimeMillis() - startAt) / 1000f
        val reveal = smooth((elapsed / 2.85f).coerceIn(0f, 1f))
        val idle = (elapsed - 2.85f).coerceAtLeast(0f)
        val drift = if (elapsed < 2.85f) elapsed else 2.85f + idle
        val breathe = 0.5f + 0.5f * sin(drift * 0.62f)

        bgPaint.shader = glowShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null

        // Slow translucent ribbons keep moving gently if the app needs extra load time.
        for (i in 0..2) {
            val y = height * (0.22f + i * 0.27f) + sin(drift * 0.34f + i * 1.8f) * height * 0.055f
            val x = sin(drift * 0.23f + i * 2.1f) * width * 0.23f
            val ribbon = RectF(x - width * 0.68f, y - dp(22f), x + width * 0.68f, y + dp(22f))
            glassPaint.shader = LinearGradient(
                ribbon.left, ribbon.top, ribbon.right, ribbon.bottom,
                intArrayOf(Color.argb(0, 112, 170, 255), Color.argb(22, 145, 193, 255),
                    Color.argb(8, 220, 235, 255), Color.argb(0, 180, 130, 255)),
                null, Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.rotate(-9f + i * 7f, cx, y)
            canvas.drawRoundRect(ribbon, dp(28f), dp(28f), glassPaint)
            canvas.restore()
        }
        glassPaint.shader = null

        // A subtle halo and orbital hairlines frame the logo.
        ringPaint.color = Color.argb((18 + breathe * 18).toInt(), 177, 211, 255)
        canvas.drawCircle(cx, cy, iconSize * (0.92f + breathe * 0.035f), ringPaint)
        ringPaint.color = Color.argb(24, 205, 224, 255)
        canvas.drawCircle(cx, cy, iconSize * 1.12f, ringPaint)

        // Rounded floating-glass tile: no square app-icon corners are exposed.
        val cardAlpha = (reveal * 255).toInt()
        glassPaint.shader = LinearGradient(card.left, card.top, card.right, card.bottom,
            intArrayOf(Color.argb(58, 255, 255, 255), Color.argb(22, 150, 190, 255),
                Color.argb(42, 255, 255, 255)), null, Shader.TileMode.CLAMP)
        glassPaint.alpha = cardAlpha
        canvas.drawRoundRect(card, iconSize * 0.30f, iconSize * 0.30f, glassPaint)
        glassPaint.shader = null
        edgePaint.color = Color.argb((105 * reveal).toInt(), 222, 238, 255)
        canvas.drawRoundRect(card, iconSize * 0.30f, iconSize * 0.30f, edgePaint)

        icon?.let { drawable ->
            val save = canvas.save()
            canvas.clipPath(iconPath)
            drawable.alpha = cardAlpha
            drawable.setBounds(iconRect.left.toInt(), iconRect.top.toInt(), iconRect.right.toInt(), iconRect.bottom.toInt())
            drawable.draw(canvas)
            drawable.alpha = 255
            canvas.restoreToCount(save)
        }

        // The prismatic sweep happens once during the opening, then the ribbons drift.
        if (elapsed < 2.8f) {
            val x = -width * 0.85f + width * 2.15f * (elapsed / 2.8f)
            matrix.setTranslate(x, 0f)
            spectrumShader?.setLocalMatrix(matrix)
            sweepPaint.shader = spectrumShader
            sweepPaint.alpha = (170 * (1f - elapsed / 5.6f)).toInt()
            canvas.drawRect(0f, cy - iconSize * 0.78f, width.toFloat(), cy + iconSize * 0.78f, sweepPaint)
            sweepPaint.shader = null
        }

        titlePaint.alpha = cardAlpha
        canvas.drawText("COUCHY TV", cx, cy + iconSize * 1.02f, titlePaint)
        titlePaint.alpha = 255
    }

    private fun smooth(v: Float) = v * v * (3f - 2f * v)
    private fun dp(value: Float) = value * resources.displayMetrics.density

    private var Paint.letterSpacingCompat: Float
        get() = 0f
        set(@Suppress("UNUSED_PARAMETER") value) {}
}
