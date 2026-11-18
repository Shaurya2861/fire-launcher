package com.bobchomp.firelauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Cinematic cover shown immediately when the original Home watcher detects
 * Amazon Home. It stays over the delayed activity launch and fades when Couchy
 * (or another app) resumes. The original launcher remains available underneath.
 */
@SuppressLint("StaticFieldLeak")
object LauncherOverlay {
    private const val TAG = "LauncherOverlay"
    private const val MAX_SHOW_MS = 15_000L

    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private val autoHide = Runnable { hideOnMain() }

    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        main.post { showOnMain(context.applicationContext) }
    }

    fun hide() {
        main.post(::hideOnMain)
    }

    private fun showOnMain(context: Context) {
        main.removeCallbacks(autoHide)
        main.postDelayed(autoHide, MAX_SHOW_MS)
        if (root != null || !canShow(context)) return

        val frame = FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(3, 5, 13))
            addView(CouchyIntroView(context), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE
        )

        try {
            wm(context).addView(frame, params)
            root = frame
            Log.i(TAG, "Couchy intro shown")
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't show Couchy intro", e)
            main.removeCallbacks(autoHide)
        }
    }

    private fun hideOnMain() {
        main.removeCallbacks(autoHide)
        val view = root ?: return
        view.animate().cancel()
        view.animate().alpha(0f).setDuration(520L).withEndAction {
            if (root === view) {
                runCatching { wm(view.context).removeView(view) }
                root = null
                Log.i(TAG, "Couchy intro faded away")
            }
        }.start()
    }

    private fun wm(context: Context) =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
}
