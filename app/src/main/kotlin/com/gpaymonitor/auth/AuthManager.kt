package com.gpaymonitor.auth

import android.content.Context
import android.content.Intent
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase email-link (passwordless) authentication.
 *
 * Flow:
 *  1. User enters email → sendSignInLink()
 *  2. User taps link in email → app opens via deep link
 *  3. handleSignInLink() verifies and signs the user in
 */
class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Returns the currently authenticated user, or null if not signed in. */
    val currentUser: FirebaseUser? get() = auth.currentUser

    /**
     * Send a sign-in link to [email].
     * The link points to your Firebase Dynamic Link domain.
     */
    suspend fun sendSignInLink(email: String): Result<Unit> = runCatching {
        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setUrl("https://gpay-monitor.page.link/signin")   // ← your Dynamic Link URL
            .setHandleCodeInApp(true)
            .setAndroidPackageName(
                context.packageName,
                true,   // installIfNotAvailable
                null    // minimumVersion
            )
            .build()

        auth.sendSignInLinkToEmail(email, actionCodeSettings).await()

        // Persist email so we can use it when the link is tapped
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pending_email", email)
            .apply()
    }

    /**
     * Call this from MainActivity.onNewIntent / onCreate when the app
     * is opened via the email link deep link.
     */
    suspend fun handleSignInLink(intent: Intent): Result<FirebaseUser> = runCatching {
        val link = intent.data?.toString()
            ?: throw IllegalArgumentException("No link in intent")

        if (!auth.isSignInWithEmailLink(link)) {
            throw IllegalArgumentException("Not a valid sign-in link")
        }

        val email = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .getString("pending_email", null)
            ?: throw IllegalStateException("No pending email — ask user to re-enter")

        val result = auth.signInWithEmailLink(email, link).await()

        // Clear pending email
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("pending_email")
            .apply()

        result.user ?: throw IllegalStateException("Sign-in returned null user")
    }

    fun signOut() = auth.signOut()

    /** True if the given intent contains a valid email sign-in link. */
    fun isSignInLink(intent: Intent): Boolean {
        val link = intent.data?.toString() ?: return false
        return auth.isSignInWithEmailLink(link)
    }
}
