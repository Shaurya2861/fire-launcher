package com.bobchomp.firelauncher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bobchomp.firelauncher.ui.LauncherScreen
import com.bobchomp.firelauncher.ui.LauncherTheme

class MainActivity : ComponentActivity() {

    private var homePresses by mutableIntStateOf(0)
    private var homeRedirectOn by mutableStateOf(true)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LauncherModel.init(this)

        setContent {
            BackHandler {}
            LauncherTheme {
                LauncherScreen(
                    apps = LauncherModel.apps,
                    artLoader = LauncherModel.artLoader,
                    homePresses = homePresses,
                    homeRedirectOn = homeRedirectOn,
                    onLaunch = { LauncherModel.launch(this, it) },
                    onOpenSettings = { LauncherModel.openSettings(this) },
                )
            }
        }

        HomeWatcherService.start(this)

        // Keep the original launcher as a real fallback, but hand off to Couchy
        // immediately whenever this bridge activity itself is opened.
        LauncherOverlay.show(this)
        mainHandler.post {
            val couchyIntent = packageManager.getLaunchIntentForPackage(COUCHY_PACKAGE)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (couchyIntent == null) {
                Log.w(TAG, "Couchy TV is not installed; keeping the original launcher visible")
                LauncherOverlay.hide()
                return@post
            }
            runCatching { startActivity(couchyIntent) }
                .onFailure {
                    Log.w(TAG, "Couldn't open Couchy TV; keeping the original launcher visible", it)
                    LauncherOverlay.hide()
                }
        }
    }

    override fun onResume() {
        super.onResume()
        homeRedirectOn = HomeWatcherService.canReadLogs(this) || HomeButtonService.isEnabled(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FROM_HOME_KEY, false)) homePresses++
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val COUCHY_PACKAGE = "com.conreo.couchytv"
        const val EXTRA_FROM_HOME_KEY = "com.bobchomp.firelauncher.FROM_HOME_KEY"
    }
}
