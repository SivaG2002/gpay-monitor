package com.gpaymonitor.data

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.gpaymonitor.GPayMonitorApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager [CoroutineWorker] that uploads a [PaymentEvent] to Firestore.
 *
 * Why WorkManager?
 *  - Survives process death, screen-off, and Doze mode
 *  - Built-in exponential backoff retry
 *  - Runs even if triggered while offline (queued until network available)
 *
 * Input data keys match [KEY_*] constants below.
 */
class NotificationUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = FirestoreRepository()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId   = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result.failure()   // not signed in — don't retry
        val deviceId = GPayMonitorApp.getDeviceId(applicationContext)
        val amount   = inputData.getString(KEY_AMOUNT)   ?: return@withContext Result.failure()
        val rawText  = inputData.getString(KEY_RAW_TEXT) ?: return@withContext Result.failure()
        val hash     = inputData.getString(KEY_HASH)     ?: return@withContext Result.failure()
        val epochMs  = inputData.getLong(KEY_EPOCH_MS, System.currentTimeMillis())

        val event = PaymentEvent(
            userId    = userId,
            deviceId  = deviceId,
            amount    = amount,
            rawText   = rawText,
            hash      = hash,
            createdAt = Timestamp(epochMs / 1000, ((epochMs % 1000) * 1_000_000).toInt())
        )

        return@withContext try {
            repository.savePaymentEvent(event)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed, will retry", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_AMOUNT   = "amount"
        const val KEY_RAW_TEXT = "raw_text"
        const val KEY_HASH     = "hash"
        const val KEY_EPOCH_MS = "epoch_ms"
        private const val MAX_RETRIES = 5
        private const val TAG = "UploadWorker"

        /**
         * Enqueue a one-time upload with exponential back-off.
         * Requires NETWORK connectivity.
         */
        fun enqueue(
            context: Context,
            amount: String,
            rawText: String,
            hash: String,
            epochMs: Long
        ) {
            val data = workDataOf(
                KEY_AMOUNT   to amount,
                KEY_RAW_TEXT to rawText,
                KEY_HASH     to hash,
                KEY_EPOCH_MS to epochMs
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<NotificationUploadWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                // Use hash as unique work name to avoid duplicate queuing
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "upload_$hash",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
