package com.bobchomp.firelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/** Sends Home directly to Couchy while the overlay covers Fire OS's delayed transition. */
object HomeRedirect {
    private const val TAG = "HomeRedirect"
    private const val DEBOUNCE_MS = 700L
    private const val COUCHY_PACKAGE = "com.conreo.couchytv"
    @Volatile private var lastLaunchAt = 0L

    class StockHome(val packages: Set<String>, val activityClasses: Set<String>, val components: Set<String>)

    fun findStockHome(context: Context): StockHome {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val activities = context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName && !it.name.endsWith("FallbackHome") }
        val classes = HashSet<String>()
        val components = HashSet<String>()
        for (info in activities) {
            for (cls in listOfNotNull(info.name, info.targetActivity)) {
                classes += cls
                val component = ComponentName(info.packageName, cls)
                components += component.flattenToString()
                components += component.flattenToShortString()
            }
        }
        return StockHome(activities.mapTo(HashSet()) { it.packageName }, classes, components)
    }

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
        }.onFailure { Log.w(TAG, "Couldn't launch Couchy TV", it) }
    }
}
