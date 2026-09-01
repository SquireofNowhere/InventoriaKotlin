package com.inventoria.app.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.inventoria.app.data.model.FocusArea
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.TodoAlarmStyle
import com.inventoria.app.data.model.TodoPriority
import com.inventoria.app.data.repository.FirebaseAuthRepository
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.InviteCode
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

    val focusArea: StateFlow<FocusArea> = settingsRepository.getFocusArea()
        .map { FocusArea.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusArea.INVENTORY)

    val isDarkMode: StateFlow<Boolean> = settingsRepository.isDarkMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notificationsEnabled: StateFlow<Boolean> = settingsRepository.getNotificationsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val todoAlarmStyle: StateFlow<TodoAlarmStyle> = settingsRepository.getTodoAlarmStyle()
        .map { TodoAlarmStyle.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodoAlarmStyle.ALARM)

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

    /** The owner's own code, expired or not -- the UI shows an expired one as needing replacing. */
    private val _generatedInviteCode = MutableStateFlow<InviteCode?>(null)
    val generatedInviteCode: StateFlow<InviteCode?> = _generatedInviteCode.asStateFlow()

    /** Failures from *using* someone else's code, shown under the code entry field. */
    private val _inviteCodeError = MutableStateFlow<String?>(null)
    val inviteCodeError: StateFlow<String?> = _inviteCodeError.asStateFlow()

    /**
     * Failures from managing *your own* code -- generating, retiring, revoking a joiner.
     *
     * Separate from [inviteCodeError] because they shared one channel: a failed "Generate Invite
     * Code" surfaced as the error text under "Enter Invite Code", blaming a field the user had not
     * touched, and could only be cleared by typing into that unrelated field.
     */
    private val _inviteCodeGenerationError = MutableStateFlow<String?>(null)
    val inviteCodeGenerationError: StateFlow<String?> = _inviteCodeGenerationError.asStateFlow()

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

    fun setFocusArea(area: FocusArea) {
        viewModelScope.launch {
            settingsRepository.setFocusArea(area.name)
        }
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

    fun setTodoAlarmStyle(style: TodoAlarmStyle) {
        viewModelScope.launch {
            settingsRepository.setTodoAlarmStyle(style.name)
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
    
    /** Disconnects from an external account and goes back to this device's own database. */
    fun clearExternalSync() {
        viewModelScope.launch { switchSyncTarget(null) }
    }

    /**
     * Points the device at a different database, emptying Room on the way.
     *
     * Room is a cache of exactly one account, and saving the new id was previously the *whole*
     * operation -- the old account's rows stayed, and the next full push (which sends every row,
     * on every backgrounding) uploaded them into the new target. Joining an invite code therefore
     * copied the joiner's entire inventory into the inviter's account, and disconnecting copied the
     * inviter's back into the joiner's.
     *
     * Sync stops first so no listener is pushing while the tables are emptied, and the new target's
     * data arrives from its own node as soon as the listeners come back up. Anything already synced
     * survives in whichever cloud node owns it.
     */
    private suspend fun switchSyncTarget(syncId: String?) {
        syncRepository.stopSync()
        localDataRepository.clearSyncedData()
        settingsRepository.saveManualSyncId(syncId)
    }

    fun revokeSharedAccess(joinerUid: String) {
        viewModelScope.launch {
            val result = authRepository.revokeSharedAccess(joinerUid)
            if (result.isFailure) {
                _inviteCodeGenerationError.value =
                    "Couldn't revoke that connection: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            }
        }
    }

    /**
     * Retires the current code. Revoking a person leaves the code they used still working, so
     * without this there is no way to actually cut off someone who kept it -- they simply paste it
     * again.
     */
    fun revokeInviteCode() {
        viewModelScope.launch {
            _inviteCodeGenerationError.value = null
            // The displayed code, not a fresh read: a failed read would look like "no code to
            // retire" and the UI would clear a code that is still live on the server.
            val code = _generatedInviteCode.value?.code ?: return@launch
            val result = authRepository.revokeInviteCode(code)
            if (result.isSuccess) {
                _generatedInviteCode.value = null
            } else {
                _inviteCodeGenerationError.value =
                    "Couldn't retire the code: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            }
        }
    }

    fun createInviteCode() {
        viewModelScope.launch {
            _inviteCodeGenerationError.value = null
            try {
                val code = authRepository.generateInviteCode()
                _generatedInviteCode.value = code
            } catch (e: Exception) {
                _inviteCodeGenerationError.value = e.message ?: "Couldn't create an invite code"
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
            val normalized = FirebaseAuthRepository.normalizeInviteCode(code)
            if (normalized.length != FirebaseAuthRepository.INVITE_CODE_LENGTH) {
                _inviteCodeError.value =
                    "An invite code is ${FirebaseAuthRepository.INVITE_CODE_LENGTH} letters and numbers."
                return@launch
            }
            try {
                val targetUserId = authRepository.getUserIdFromInviteCode(normalized)
                if (targetUserId == null) {
                    _inviteCodeError.value = "Invalid or expired invite code"
                    return@launch
                }
                if (targetUserId == authRepository.getCurrentUserId()) {
                    _inviteCodeError.value = "That's your own invite code — this device is already on that database."
                    return@launch
                }
                // The result used to be discarded, and manualSyncId was saved regardless. When the
                // rules refused the link -- a retired code, a revoked user -- the device still
                // switched to a database it had no permission to read, and every sync from then on
                // failed while the UI cheerfully reported "Synced to an External Account".
                val link = authRepository.linkToUser(targetUserId, normalized)
                if (link.isFailure) {
                    _inviteCodeError.value =
                        "That account wouldn't accept the link — the code may have been retired. (${link.exceptionOrNull()?.message ?: "permission denied"})"
                    return@launch
                }
                switchSyncTarget(targetUserId)
            } catch (e: Exception) {
                _inviteCodeError.value = "Error: ${e.message}"
            }
        }
    }

    fun clearInviteCodeError() {
        _inviteCodeError.value = null
    }

    fun clearInviteCodeGenerationError() {
        _inviteCodeGenerationError.value = null
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
