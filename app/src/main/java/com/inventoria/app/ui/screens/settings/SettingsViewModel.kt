package com.inventoria.app.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.inventoria.app.data.repository.FirebaseAuthRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: FirebaseAuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isDarkMode: StateFlow<Boolean> = settingsRepository.isDarkMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.getNotificationsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showValueOnDashboard: StateFlow<Boolean> = settingsRepository.getShowValueOnDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val customUsername: StateFlow<String?> = settingsRepository.customUsername
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currencyCode: StateFlow<String> = settingsRepository.getCurrencyCode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "USD")

    val autoCurrencyEnabled: StateFlow<Boolean> = settingsRepository.isAutoCurrencyEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
        
    val manualSyncId: StateFlow<String?> = settingsRepository.manualSyncId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _generatedInviteCode = MutableStateFlow<String?>(null)
    val generatedInviteCode: StateFlow<String?> = _generatedInviteCode.asStateFlow()

    private val _inviteCodeError = MutableStateFlow<String?>(null)
    val inviteCodeError: StateFlow<String?> = _inviteCodeError.asStateFlow()

    init {
        checkCurrentUser()
        loadExistingInviteCode()
    }

    private fun checkCurrentUser() {
        val user = authRepository.getCurrentUser()
        if (user != null && !user.isAnonymous) {
            _authState.value = AuthState.Authenticated(user)
        } else {
            _authState.value = AuthState.Idle
        }
    }

    private fun loadExistingInviteCode() {
        viewModelScope.launch {
            val code = authRepository.getExistingInviteCode()
            _generatedInviteCode.value = code
        }
    }

    fun onGoogleSignInSuccess(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val user = authRepository.signInWithGoogle(idToken)
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                    loadExistingInviteCode()
                } else {
                    _authState.value = AuthState.Error("Sign in failed")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun getGoogleSignInIntent(): Intent {
        return authRepository.getGoogleSignInIntent()
    }

    fun signOut() {
        authRepository.signOut()
        _authState.value = AuthState.Idle
        _generatedInviteCode.value = null
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.deleteUserAccount()
            if (result.isSuccess) {
                _authState.value = AuthState.Idle
                _generatedInviteCode.value = null
            } else {
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Failed to delete account")
            }
        }
    }

    fun clearAuthState() {
        _authState.value = AuthState.Idle
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.toggleDarkMode(enabled)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.toggleNotifications(enabled)
        }
    }

    fun toggleShowValue(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.toggleShowValue(enabled)
        }
    }

    fun updateCustomUsername(name: String) {
        viewModelScope.launch {
            settingsRepository.saveCustomUsername(name)
        }
    }

    fun updateCurrencyCode(code: String) {
        viewModelScope.launch {
            settingsRepository.saveCurrencyCode(code)
        }
    }

    fun toggleAutoCurrency(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoCurrencyEnabled(enabled)
        }
    }
    
    fun setManualSyncId(syncId: String?) {
        viewModelScope.launch {
            settingsRepository.saveManualSyncId(syncId)
        }
    }

    fun createInviteCode() {
        viewModelScope.launch {
            try {
                val code = authRepository.generateInviteCode()
                _generatedInviteCode.value = code
            } catch (e: Exception) {
                _inviteCodeError.value = e.message
            }
        }
    }

    fun useInviteCode(code: String) {
        viewModelScope.launch {
            _inviteCodeError.value = null
            try {
                val targetUserId = authRepository.getUserIdFromInviteCode(code)
                if (targetUserId != null) {
                    // Inform the backend/owner that we want to link
                    // We pass the code itself so the backend rules can verify it
                    authRepository.linkToUser(targetUserId, code)
                    // Set local sync ID to the owner's ID
                    settingsRepository.saveManualSyncId(targetUserId)
                } else {
                    _inviteCodeError.value = "Invalid or expired invite code"
                }
            } catch (e: Exception) {
                _inviteCodeError.value = "Error: ${e.message}"
            }
        }
    }

    fun clearInviteCodeError() {
        _inviteCodeError.value = null
    }
    
    fun getCurrentUserId(): String? = authRepository.getCurrentUserId()
}
