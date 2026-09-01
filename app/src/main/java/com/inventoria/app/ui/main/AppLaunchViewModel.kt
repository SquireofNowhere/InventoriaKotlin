package com.inventoria.app.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.BuildConfig
import com.inventoria.app.data.model.FocusArea
import com.inventoria.app.data.repository.SettingsRepository
import com.inventoria.app.ui.main.changelog.ChangelogCatalog
import com.inventoria.app.ui.main.changelog.ChangelogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The two one-time launch dialogs (focus prompt, What's New) plus the focus preference the nav
 * bar orders itself by. Hosted by InventoriaApp at the top level -- Activity-scoped, so the
 * dialogs survive tab switches, and only ever constructed after the splash has settled auth.
 *
 * The dialogs are strictly sequential (focus prompt first): the focus choice reorders the very
 * tabs and dashboard a changelog entry describes, so describing them before the user has chosen
 * would narrate a UI about to change under them. InventoriaApp renders What's New only once the
 * prompt is gone.
 */
@HiltViewModel
class AppLaunchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /**
     * Eagerly so the very first nav-bar frame almost always has the real order; the fallback
     * default only shows if a DataStore read somehow loses a race it has the whole splash
     * animation to win, and then costs one cosmetic reorder frame.
     */
    val focusArea: StateFlow<FocusArea> = settingsRepository.getFocusArea()
        .map { FocusArea.fromName(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FocusArea.DEFAULT)

    private val _showFocusPrompt = MutableStateFlow(false)
    val showFocusPrompt: StateFlow<Boolean> = _showFocusPrompt.asStateFlow()

    private val _pendingWhatsNew = MutableStateFlow<List<ChangelogEntry>?>(null)
    val pendingWhatsNew: StateFlow<List<ChangelogEntry>?> = _pendingWhatsNew.asStateFlow()

    init {
        viewModelScope.launch {
            if (!settingsRepository.hasSeenFocusPrompt().first()) {
                _showFocusPrompt.value = true
            }

            val lastSeen = settingsRepository.getLastSeenVersionCode().first()
            val current = BuildConfig.VERSION_CODE
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            when {
                // No stored version can mean either a fresh install or an upgrade from before
                // this pref existed -- only the install timestamps tell them apart. Fresh
                // installs get seeded silently: "what's new" is meaningless when everything is.
                lastSeen == 0 && pkg.firstInstallTime == pkg.lastUpdateTime ->
                    settingsRepository.setLastSeenVersionCode(current)
                lastSeen < current -> {
                    // A release with no catalog entry still advances the stored code, otherwise
                    // the empty check would re-run on every launch until the next writeup ships.
                    val entries = ChangelogCatalog.entriesSince(lastSeen)
                    if (entries.isEmpty()) settingsRepository.setLastSeenVersionCode(current)
                    else _pendingWhatsNew.value = entries
                }
            }
        }
    }

    fun chooseFocus(area: FocusArea) {
        viewModelScope.launch {
            settingsRepository.setFocusArea(area.name)
            settingsRepository.setFocusPromptShown(true)
            _showFocusPrompt.value = false
        }
    }

    /**
     * Outside-tap, back, or "Use Inventory": keep the default and never ask again -- same
     * no-re-nag contract as the inner-task prompt's "Not Now".
     */
    fun dismissFocusPrompt() {
        viewModelScope.launch {
            settingsRepository.setFocusPromptShown(true)
            _showFocusPrompt.value = false
        }
    }

    /** Persisted on dismiss rather than on show, so process death mid-dialog re-shows it. */
    fun dismissWhatsNew() {
        viewModelScope.launch {
            settingsRepository.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
            _pendingWhatsNew.value = null
        }
    }
}
