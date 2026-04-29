package com.gpaymonitor.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED broadcast.
 *
 * NotificationListenerService is automatically rebound by the OS after
 * a reboot — we don't need to manually start it. However this receiver
 * is a good place to re-schedule any pending WorkManager jobs that
 * survived serialisation across reboots (WorkManager does this automatically
 * as of 2.7, so this is mostly a safety net and logging hook).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed — NotificationListenerService will be rebound by OS")
            // WorkManager re-enqueues persisted work automatically after reboot.
            // No manual action required unless you need custom logic here.
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
