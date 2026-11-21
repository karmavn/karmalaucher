package app.lawnchair.gestures.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Toast
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenNotificationsHandler(context: Context) : GestureHandler(context) {

    @SuppressLint("WrongConstant")
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .apply { isAccessible = true }
                .invoke(context.getSystemService("statusbar"))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to expand notifications panel", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(launcher, R.string.status_bar_expand_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val TAG = "OpenNotificationsHandler"
    }
}
