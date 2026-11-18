package com.bobchomp.firelauncher

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Sends Home directly to Couchy TV. Fire OS still owns the HOME role, so the
 * watcher displays the cinematic cover while Android brings Couchy forward.
 */
object HomeRedirect {
    private const val TAG = "HomeRedirect"
    private const val DEBOUNCE_MS = 700L
    private const val COUCHY_PACKAGE = "com.conreo.couchytv"

    @Volatile private var lastLaunchAt = 0L

    fun isShortlyAfterBoot() = SystemClock.elapsedRealtime() < 3 * 60_000L

    fun bringLauncherToFront(context: Context, fromHomeKey: Boolean) {
        synchronized(this) {
            val now = SystemClock.uptimeMillis()
            if (now - lastLaunchAt < DEBOUNCE_MS) return
            lastLaunchAt = now
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(COUCHY_PACKAGE)
        if (launchIntent == null) {
            Log.e(TAG, "Couchy TV is not installed or has no launchable activity")
            return
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching {
            context.startActivity(launchIntent)
            Log.i(TAG, "Launching Couchy TV")
        }.onFailure {
            Log.w(TAG, "Couldn't launch Couchy TV", it)
        }
    }
}
