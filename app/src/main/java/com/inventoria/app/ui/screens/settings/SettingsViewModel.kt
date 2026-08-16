package com.inventoria.app.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.data.repository.FirebaseAuthRepository
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.LocalDataRepository
import com.inventoria.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: FirebaseAuthRepository,
    private val localDataRepository: LocalDataRepository,
    private val syncRepository: FirebaseSyncRepository
) : ViewModel() {

    /**
     * Who is signed in, straight from Firebase's own auth state listener rather than sampled once
     * in init.
     *
     * The anonymous local account is created lazily -- on the splash, or by InventoryRepository's
     * init -- so it routinely arrives *after* this ViewModel is constructed, and a snapshot taken
     * at construction went on claiming there was no account long after there was one. It also
     * changes under us on sign-out and on delete. Nothing here is a guess about what Firebase did;
     * it is what Firebase reports.
     */
    val authState: StateFlow<AuthState> = authRepository.authStateFlow
        .map { it.toAuthState() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            authRepository.getCurrentUser().toAuthState()
        )

    /** The uid of whatever account currently exists on this device, tracked live for the same reason. */
    val currentUserId: StateFlow<String?> = authRepository.authStateFlow
        .map { it?.uid }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            authRepository.getCurrentUserId()
        )

    /** Kept apart from [authState] so an in-flight or failed action never masks the real account. */
    private val _authOperation = MutableStateFlow<AuthOperation?>(null)
    val authOperation: StateFlow<AuthOperation?> = _authOperation.asStateFlow()

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

    val innerTaskEnabled: StateFlow<Boolean> = settingsRepository.isInnerTaskEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val procrastinationTodoEnabled: StateFlow<Boolean> = settingsRepository.isProcrastinationTodoEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val procrastinationTodoCutoff: StateFlow<TodoPriority> = settingsRepository.getProcrastinationTodoCutoff()
        .map { try { TodoPriority.valueOf(it) } catch (e: IllegalArgumentException) { TodoPriority.B1 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodoPriority.B1)

    val procrastinationTaskEnabled: StateFlow<Boolean> = settingsRepository.isProcrastinationTaskEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val procrastinationTaskKinds: StateFlow<Set<TaskKind>> = settingsRepository.getProcrastinationTaskKinds()
        .map { names -> names.mapNotNull { try { TaskKind.valueOf(it) } catch (e: IllegalArgumentException) { null } }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val procrastinationPenaltyAmount: StateFlow<Int> = settingsRepository.getProcrastinationPenaltyAmount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    private val _generatedInviteCode = MutableStateFlow<String?>(null)
    val generatedInviteCode: StateFlow<String?> = _generatedInviteCode.asStateFlow()

    private val _inviteCodeError = MutableStateFlow<String?>(null)
    val inviteCodeError: StateFlow<String?> = _inviteCodeError.asStateFlow()

    /**
     * Emitted once [deleteAccount] has finished wiping the device. The UI has to restart from the
     * splash on this: every screen behind Settings is still showing the deleted account's data,
     * and the splash is where the replacement account gets created.
     */
    private val _accountWiped = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val accountWiped: SharedFlow<Unit> = _accountWiped.asSharedFlow()

    // Map of {joinerUid -> inviteCode} for accounts currently synced to *my* database.
    val sharedWithUsers: StateFlow<Map<String, String>> = authRepository.authStateFlow
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid != null) authRepository.getSharedWithFlow(uid) else flowOf(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Keyed on the uid rather than read once: there may be no account yet when Settings is
        // first built, and the uid changes on sign-in and on delete. A one-shot read in init left
        // the invite code permanently blank for anyone whose account arrived a moment later.
        viewModelScope.launch {
            authRepository.authStateFlow
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { loadExistingInviteCode() }
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
            if (settingsRepository.manualSyncId.first() != null) {
                _authOperation.value = AuthOperation.Failed("Clear the external sync connection first — local, Google, and external-sync are separate states and shouldn't overlap.")
                return@launch
            }
            _authOperation.value = AuthOperation.InProgress
            try {
                val user = authRepository.signInWithGoogle(idToken)
                if (user != null) {
                    // authState follows Firebase's listener, which has already fired by now, and
                    // the init block above reloads the invite code off the same signal.
                    _authOperation.value = null
                } else {
                    _authOperation.value = AuthOperation.Failed("Sign in failed")
                }
            } catch (e: Exception) {
                _authOperation.value = AuthOperation.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun getGoogleSignInIntent(): Intent {
        return authRepository.getGoogleSignInIntent()
    }

    fun signOut() {
        authRepository.signOut()
        _authOperation.value = null
        _generatedInviteCode.value = null
    }

    /**
     * Deletes the account everywhere it exists -- remote *and* on this device.
     *
     * deleteUserAccount() only ever touched Firebase, so this used to leave the entire Room
     * database and every preference behind; on a local account, where the button reads "Wipe
     * Local Account Data", that meant it visibly did nothing at all. The local wipe is
     * conditional on the remote delete having succeeded: if it failed we are still signed in, and
     * wiping would just hand the next sync an empty device to re-fill from the cloud.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            _authOperation.value = AuthOperation.InProgress
            val result = authRepository.deleteUserAccount()
            if (result.isSuccess) {
                // Before the wipe, so no listener is still holding the node that just went away.
                syncRepository.stopSync()
                localDataRepository.wipeAllLocalData()
                _generatedInviteCode.value = null
                _authOperation.value = null
                _accountWiped.emit(Unit)
            } else {
                _authOperation.value = AuthOperation.Failed(result.exceptionOrNull()?.message ?: "Failed to delete account")
            }
        }
    }

    /** Dismisses a failed sign-in or delete. The account state underneath it never needed clearing. */
    fun clearAuthState() {
        _authOperation.value = null
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

    fun toggleInnerTask(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setInnerTaskEnabled(enabled)
            // Toggling it directly here counts as having seen the explanation.
            settingsRepository.setInnerTaskPromptShown(true)
        }
    }

    fun toggleProcrastinationTodo(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setProcrastinationTodoEnabled(enabled) }
    }

    fun setProcrastinationTodoCutoff(priority: TodoPriority) {
        viewModelScope.launch { settingsRepository.setProcrastinationTodoCutoff(priority.name) }
    }

    fun toggleProcrastinationTask(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setProcrastinationTaskEnabled(enabled) }
    }

    fun toggleProcrastinationTaskKind(kind: TaskKind) {
        viewModelScope.launch {
            val current = procrastinationTaskKinds.value
            val next = if (kind in current) current - kind else current + kind
            settingsRepository.saveProcrastinationTaskKinds(next.map { it.name }.toSet())
        }
    }

    fun setProcrastinationPenaltyAmount(amount: Int) {
        viewModelScope.launch { settingsRepository.setProcrastinationPenaltyAmount(amount) }
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

    fun revokeSharedAccess(joinerUid: String) {
        viewModelScope.launch {
            authRepository.revokeSharedAccess(joinerUid)
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
            // Asks Firebase rather than reading authState.value: this is a correctness guard, and
            // authState is a WhileSubscribed flow whose cached value goes stale once Settings is
            // off screen.
            if (isGoogleSignedIn()) {
                _inviteCodeError.value = "Sign out of your Google account first — local, Google, and external-sync are separate states and shouldn't overlap."
                return@launch
            }
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

    /** Firebase's live answer, for guards that must not act on a cached flow value. */
    private fun isGoogleSignedIn(): Boolean =
        authRepository.getCurrentUser()?.isAnonymous == false

    /**
     * The one place the "is this a real account or the anonymous local one" question is answered,
     * so [authState] and everything keyed off it cannot drift apart.
     */
    private fun FirebaseUser?.toAuthState(): AuthState =
        if (this != null && !this.isAnonymous) AuthState.Authenticated(this) else AuthState.Idle
}
