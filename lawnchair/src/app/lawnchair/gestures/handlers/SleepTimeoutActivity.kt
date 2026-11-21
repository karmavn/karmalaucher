package app.lawnchair.gestures.handlers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Turns the screen off by temporarily lowering the system screen timeout over a pure black screen,
 * avoiding Accessibility services so biometric unlock stays available and security-sensitive apps
 * do not flag the launcher.
 */
class SleepTimeoutActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val safetyTimeoutRunnable = Runnable { restoreAndFinish() }
    private var receiverRegistered = false
    private var finished = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                restoreAndFinish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.System.canWrite(this)) {
            finish()
            return
        }

        saveOriginalTimeoutIfAbsent()

        setContentView(
            View(this).apply {
                setBackgroundColor(Color.BLACK)
                setOnClickListener { restoreAndFinish() }
            },
        )

        window.attributes = window.attributes.apply {
            screenBrightness = 0.01f
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true

        try {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                MIN_TIMEOUT_MS,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to shorten screen off timeout", e)
            restoreAndFinish()
            return
        }

        handler.postDelayed(safetyTimeoutRunnable, MAX_WAIT_MS)
    }

    override fun onStop() {
        super.onStop()
        val isInteractive = getSystemService(PowerManager::class.java)?.isInteractive == true
        if (!isChangingConfigurations && isInteractive) {
            restoreAndFinish()
        }
    }

    override fun onRestart() {
        super.onRestart()
        restoreAndFinish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(safetyTimeoutRunnable)
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver was already unregistered.
            }
            receiverRegistered = false
        }
        restorePendingTimeoutIfNeeded(this)
        super.onDestroy()
    }

    private fun saveOriginalTimeoutIfAbsent() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.contains(KEY_ORIGINAL_TIMEOUT)) return
        val current = Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            DEFAULT_TIMEOUT_MS,
        )
        val safeTimeout = if (current < MIN_RESTORE_TIMEOUT_MS) DEFAULT_TIMEOUT_MS else current
        prefs.edit(commit = true) {
            putInt(KEY_ORIGINAL_TIMEOUT, safeTimeout)
        }
    }

    private fun restoreAndFinish() {
        if (finished) return
        finished = true
        handler.removeCallbacks(safetyTimeoutRunnable)
        restorePendingTimeoutIfNeeded(this)
        if (!isFinishing && !isDestroyed) {
            finish()
        }
    }

    companion object {
        private const val TAG = "SleepTimeoutActivity"
        private const val PREFS_NAME = "sleep_timeout_restore"
        private const val KEY_ORIGINAL_TIMEOUT = "original_screen_off_timeout"
        private const val MIN_TIMEOUT_MS = 1_000
        private const val MIN_RESTORE_TIMEOUT_MS = 15_000
        private const val DEFAULT_TIMEOUT_MS = 30_000
        private const val MAX_WAIT_MS = 20_000L

        fun restorePendingTimeoutIfNeeded(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            if (!prefs.contains(KEY_ORIGINAL_TIMEOUT)) return
            if (!Settings.System.canWrite(context)) return

            val saved = prefs.getInt(KEY_ORIGINAL_TIMEOUT, DEFAULT_TIMEOUT_MS)
            val safeRestore = if (saved < MIN_RESTORE_TIMEOUT_MS) DEFAULT_TIMEOUT_MS else saved
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    safeRestore,
                )
                prefs.edit(commit = true) {
                    remove(KEY_ORIGINAL_TIMEOUT)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore screen off timeout", e)
            }
        }
    }
}
