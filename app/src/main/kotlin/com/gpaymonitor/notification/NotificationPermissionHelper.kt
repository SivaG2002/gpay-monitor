package com.gpaymonitor.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * Utility for checking and requesting Notification Listener permission.
 *
 * Unlike regular runtime permissions, Notification Access requires the user
 * to navigate to a system settings screen. We can only check if it's granted
 * and redirect if not.
 */
object NotificationPermissionHelper {

    /**
     * Returns true if this app has notification listener permission.
     */
    fun isGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val cn = ComponentName(context, GPayNotificationService::class.java)
        return flat.split(":").any { component ->
            try {
                ComponentName.unflattenFromString(component) == cn
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Open the system Notification Access settings screen.
     * The user must toggle on your app manually.
     */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
