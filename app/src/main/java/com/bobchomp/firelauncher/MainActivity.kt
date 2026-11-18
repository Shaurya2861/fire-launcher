package com.bobchomp.firelauncher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * The experimental app is a bridge, not a second launcher UI.
 * Opening it manually should produce the same Couchy hand-off as pressing Home.
 */
class MainActivity : ComponentActivity() {

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HomeWatcherService.start(this)
        LauncherOverlay.show(this)

        main.postDelayed({
            val launchIntent = packageManager.getLaunchIntentForPackage("com.conreo.couchytv")
            if (launchIntent == null) {
                Log.e(TAG, "Couchy TV is not installed or has no launch activity")
                LauncherOverlay.hide()
                finish()
                return@postDelayed
            }

            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            runCatching { startActivity(launchIntent) }
                .onFailure {
                    Log.e(TAG, "Couldn't launch Couchy TV", it)
                    LauncherOverlay.hide()
                }
            finish()
        }, 250L)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CouchyBridge"
    }
}
