package com.gpaymonitor.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Repository that writes [PaymentEvent] documents to Firestore.
 *
 * Deduplication strategy:
 *   Before writing, query the `notifications` collection for a document
 *   with the same `hash` AND `userId`. If one exists, skip the write.
 *
 * Retry strategy:
 *   Handled upstream by WorkManager (see [NotificationUploadWorker]).
 *   This class throws on failure so WorkManager can retry.
 */
class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("notifications")

    /**
     * Save [event] to Firestore.
     * @throws Exception if the write fails (WorkManager will retry).
     */
    suspend fun savePaymentEvent(event: PaymentEvent) {
        // --- Deduplication check ---
        val existing = collection
            .whereEqualTo("userId", event.userId)
            .whereEqualTo("hash", event.hash)
            .limit(1)
            .get()
            .await()

        if (!existing.isEmpty) {
            Log.d(TAG, "Duplicate event ignored: ${event.hash}")
            return
        }

        // --- Write new document ---
        collection.add(event.toMap()).await()
        Log.d(TAG, "Payment event saved: ${event.amount}")
    }

    /**
     * Register/update a device document under users/{uid}/devices/{deviceId}.
     * Called once after login so the dashboard can show per-device info.
     */
    suspend fun registerDevice(userId: String, deviceId: String) {
        db.collection("users")
            .document(userId)
            .collection("devices")
            .document(deviceId)
            .set(mapOf(
                "deviceId"     to deviceId,
                "userId"       to userId,
                "registeredAt" to com.google.firebase.Timestamp.now()
            ))
            .await()
    }

    companion object {
        private const val TAG = "FirestoreRepository"
    }
}
