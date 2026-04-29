package com.gpaymonitor.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gpaymonitor.databinding.ActivityMainBinding
import com.gpaymonitor.notification.NotificationPermissionHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Single-activity app.
 *
 * Screens (managed via View visibility, not fragments for simplicity):
 *   1. Login screen  — email input + "Send Link" button
 *   2. Link sent     — instructions to check email
 *   3. Home screen   — shows user info, permission status, sign-out
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeAuthState()

        // Handle email link if app was opened from one
        viewModel.handleSignInIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        viewModel.handleSignInIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission badge when user returns from settings
        updatePermissionStatus()
    }

    // -------------------------------------------------------------------------
    // Click listeners
    // -------------------------------------------------------------------------

    private fun setupClickListeners() {
        binding.btnSendLink.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                binding.etEmail.error = "Enter your email"
                return@setOnClickListener
            }
            viewModel.sendSignInLink(email)
        }

        binding.btnGrantPermission.setOnClickListener {
            NotificationPermissionHelper.openSettings(this)
        }

        binding.btnSignOut.setOnClickListener {
            viewModel.signOut()
        }
    }

    // -------------------------------------------------------------------------
    // State observation
    // -------------------------------------------------------------------------

    private fun observeAuthState() {
        lifecycleScope.launch {
            viewModel.authState.collectLatest { state ->
                when (state) {
                    is AuthState.Idle    -> showLoginScreen()
                    is AuthState.Loading -> showLoading(true)
                    is AuthState.LinkSent -> {
                        showLoading(false)
                        showLinkSentScreen(state.email)
                    }
                    is AuthState.SignedIn -> {
                        showLoading(false)
                        showHomeScreen(state.user.email ?: "Unknown")
                    }
                    is AuthState.Error   -> {
                        showLoading(false)
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Screen helpers
    // -------------------------------------------------------------------------

    private fun showLoginScreen() {
        binding.layoutLogin.visibility     = View.VISIBLE
        binding.layoutLinkSent.visibility  = View.GONE
        binding.layoutHome.visibility      = View.GONE
    }

    private fun showLinkSentScreen(email: String) {
        binding.layoutLogin.visibility    = View.GONE
        binding.layoutLinkSent.visibility = View.VISIBLE
        binding.layoutHome.visibility     = View.GONE
        binding.tvLinkSentMessage.text    = "A sign-in link has been sent to $email.\nTap it from this device."
    }

    private fun showHomeScreen(email: String) {
        binding.layoutLogin.visibility    = View.GONE
        binding.layoutLinkSent.visibility = View.GONE
        binding.layoutHome.visibility     = View.VISIBLE
        binding.tvUserEmail.text          = "Signed in as: $email"
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val granted = NotificationPermissionHelper.isGranted(this)
        if (granted) {
            binding.tvPermissionStatus.text = "✅ Notification Access: Granted — monitoring active"
            binding.btnGrantPermission.visibility = View.GONE
        } else {
            binding.tvPermissionStatus.text = "⚠️ Notification Access: Not granted"
            binding.btnGrantPermission.visibility = View.VISIBLE
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnSendLink.isEnabled  = !show
    }
}
