package com.inventoria.app.ui.main

import androidx.lifecycle.ViewModel
import com.inventoria.app.data.repository.FirebaseSyncRepository
import com.inventoria.app.data.repository.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    syncRepository: FirebaseSyncRepository
) : ViewModel() {
    val syncStatus: StateFlow<SyncStatus> = syncRepository.syncStatus
}
