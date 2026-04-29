package com.gpaymonitor.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.gpaymonitor.data.NotificationUploadWorker
import java.security.MessageDigest

/**
 * [NotificationListenerService] that captures Google Pay payment notifications.
 *
 * Lifecycle notes:
 *  - Android binds this service automatically once the user grants
 *    Notification Access in Settings > Apps > Special app access.
 *  - The service runs in the same process as the app but in a separate
 *    thread pool managed by the OS.
 *  - It survives screen-off and DND mode because the OS controls the binding.
 *
 * GPay package: com.google.android.apps.nbu.paisa.user
 */
class GPayNotificationService : NotificationListenerService() {

    private val auth = FirebaseAuth.getInstance()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // Filter: only process GPay notifications
        if (sbn.packageName != GPAY_PACKAGE) return

        // Skip if user isn't signed in — event will be lost, which is acceptable
        // because we can't attach a userId. WorkManager auth-check will also catch this.
        if (auth.currentUser == null) {
            Log.w(TAG, "Notification received but user not signed in — skipping")
            return
        }

        val extras = sbn.notification?.extras ?: return
        val title  = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text   = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // Prefer the richer big-text; fall back to normal text
        val fullText = if (bigText.isNotBlank()) bigText else text

        // Only process notifications that look like payment receipts
        if (!isPaymentNotification(title, fullText)) return

        val amount    = extractAmount(title + " " + fullText)
        val rawText   = buildRawText(title, fullText)
        val epochMs   = sbn.postTime
        val hash      = computeHash(rawText, epochMs)

        Log.i(TAG, "GPay payment captured: $amount | hash=$hash")

        // Delegate upload to WorkManager (handles network failures + Doze)
        NotificationUploadWorker.enqueue(
            context  = applicationContext,
            amount   = amount,
            rawText  = rawText,
            hash     = hash,
            epochMs  = epochMs
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op — we don't need to track removals
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Return true if the notification looks like a payment receipt.
     * GPay uses phrases like "You paid", "Paid to", "Money received", etc.
     */
    private fun isPaymentNotification(title: String, text: String): Boolean {
        val combined = (title + " " + text).lowercase()
        return PAYMENT_KEYWORDS.any { keyword -> combined.contains(keyword) } &&
               AMOUNT_REGEX.containsMatchIn(combined)
    }

    /**
     * Extract the rupee amount from notification text.
     * Handles formats:
     *   ₹1,500.00  |  Rs. 500  |  ₹10  |  INR 2000
     */
    private fun extractAmount(text: String): String {
        return AMOUNT_REGEX.find(text)?.value?.trim() ?: "Unknown"
    }

    private fun buildRawText(title: String, text: String): String =
        if (title.isNotBlank()) "$title\n$text" else text

    /**
     * SHA-256 hash of rawText + epoch to create a stable dedup key.
     * Using epoch prevents collisions when the same amount is paid twice
     * within a few seconds (very unlikely but possible).
     */
    private fun computeHash(rawText: String, epochMs: Long): String {
        val input = "$rawText|$epochMs"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "GPayNotifService"
        private const val GPAY_PACKAGE = "com.google.android.apps.nbu.paisa.user"

        private val PAYMENT_KEYWORDS = listOf(
            "you paid", "paid to", "payment of", "money received",
            "received from", "sent ₹", "sent rs", "payment successful",
            "debit", "transaction"
        )

        /**
         * Matches:
         *   ₹1,500.00  ₹500  ₹10.5
         *   Rs. 500    Rs 1,000
         *   INR 2000
         */
        private val AMOUNT_REGEX = Regex(
            """(₹|Rs\.?\s*|INR\s*)[\d,]+(\.\d{1,2})?""",
            RegexOption.IGNORE_CASE
        )
    }
}
