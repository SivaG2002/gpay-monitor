package com.gpaymonitor.data

import com.google.firebase.Timestamp

/**
 * Represents a single GPay payment notification captured on device.
 *
 * This maps 1-to-1 with the Firestore document schema:
 *
 * notifications/{autoId}
 * {
 *   userId:    string   — Firebase Auth UID
 *   deviceId:  string   — stable UUID per install
 *   amount:    string   — e.g. "₹1,500.00"
 *   rawText:   string   — full notification body
 *   hash:      string   — SHA-256 of rawText+timestamp (dedup key)
 *   createdAt: Timestamp
 * }
 */
data class PaymentEvent(
    val userId: String = "",
    val deviceId: String = "",
    val amount: String = "",
    val rawText: String = "",
    val hash: String = "",
    val createdAt: Timestamp = Timestamp.now()
) {
    /**
     * Convert to a plain Map for Firestore writes.
     * Firestore SDK can also serialise data classes directly, but an
     * explicit map makes field names obvious and avoids annotation drift.
     */
    fun toMap(): Map<String, Any> = mapOf(
        "userId"    to userId,
        "deviceId"  to deviceId,
        "amount"    to amount,
        "rawText"   to rawText,
        "hash"      to hash,
        "createdAt" to createdAt
    )
}
