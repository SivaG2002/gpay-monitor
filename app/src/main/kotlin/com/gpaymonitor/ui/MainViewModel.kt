package com.gpaymonitor.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.gpaymonitor.auth.AuthManager
import com.gpaymonitor.data.FirestoreRepository
import com.gpaymonitor.GPayMonitorApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class LinkSent(val email: String) : AuthState()
    data class SignedIn(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val authManager   = AuthManager(app)
    private val repository    = FirestoreRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    init {
        // Restore existing session
        authManager.currentUser?.let { user ->
            _authState.value = AuthState.SignedIn(user)
        }
    }

    fun sendSignInLink(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authManager.sendSignInLink(email)
                .onSuccess {
                    _authState.value = AuthState.LinkSent(email)
                }
                .onFailure { e ->
                    _authState.value = AuthState.Error(e.message ?: "Failed to send link")
                }
        }
    }

    fun handleSignInIntent(intent: Intent) {
        if (!authManager.isSignInLink(intent)) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            authManager.handleSignInLink(intent)
                .onSuccess { user ->
                    // Register device in Firestore
                    val deviceId = GPayMonitorApp.getDeviceId(getApplication())
                    try {
                        repository.registerDevice(user.uid, deviceId)
                    } catch (_: Exception) { /* non-fatal */ }

                    _authState.value = AuthState.SignedIn(user)
                }
                .onFailure { e ->
                    _authState.value = AuthState.Error(e.message ?: "Sign-in failed")
                }
        }
    }

    fun signOut() {
        authManager.signOut()
        _authState.value = AuthState.Idle
    }
}
