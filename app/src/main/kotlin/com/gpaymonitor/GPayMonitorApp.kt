package com.gpaymonitor

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import java.util.UUID

/**
 * Application class.
 * Initialises Firebase and creates a stable device UUID on first run.
 */
class GPayMonitorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        ensureDeviceId()
    }

    /**
     * Generate a UUID once and persist it in SharedPreferences.
     * This survives app restarts but NOT a factory-reset/reinstall.
     */
    private fun ensureDeviceId() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DEVICE_ID, null) == null) {
            prefs.edit()
                .putString(KEY_DEVICE_ID, UUID.randomUUID().toString())
                .apply()
        }
    }

    companion object {
        const val PREFS_NAME = "gpay_monitor_prefs"
        const val KEY_DEVICE_ID = "device_id"

        fun getDeviceId(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_DEVICE_ID, null)
                ?: UUID.randomUUID().toString().also { id ->
                    prefs.edit().putString(KEY_DEVICE_ID, id).apply()
                }
        }
    }
}
