package com.inventoria.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val SHOW_VALUE_ON_DASHBOARD = booleanPreferencesKey("show_value_on_dashboard")
    private val INVENTORY_SORT_OPTION = stringPreferencesKey("inv_sort_option")
    private val INVENTORY_GROUP_OPTION = stringPreferencesKey("inv_group_option")
    private val INVENTORY_HIDDEN_CATEGORIES = stringSetPreferencesKey("inv_hidden_cats")
    private val INVENTORY_HIDDEN_COLLECTIONS = stringSetPreferencesKey("inv_hidden_colls")
    private val INVENTORY_HARD_FILTER = booleanPreferencesKey("inv_hard_filter")
    private val INVENTORY_INVERT_FILTER = booleanPreferencesKey("inv_invert_filter")
    private val INVENTORY_EXPANDED_ITEMS = stringSetPreferencesKey("inv_expanded_items")
    private val CUSTOM_USERNAME = stringPreferencesKey("custom_username")
    private val CURRENCY_CODE = stringPreferencesKey("currency_code")
    private val AUTO_CURRENCY = booleanPreferencesKey("auto_currency")
    private val MANUAL_SYNC_ID = stringPreferencesKey("manual_sync_id")
    private val FLOW_MODE_ENABLED = booleanPreferencesKey("flow_mode_enabled")
    private val INNER_TASK_ENABLED = booleanPreferencesKey("inner_task_enabled")
    private val INNER_TASK_PROMPT_SHOWN = booleanPreferencesKey("inner_task_prompt_shown")
    private val TASK_HISTORY_FLAT_VIEW = booleanPreferencesKey("task_history_flat_view")
    // Separate key from the History screen's: the two lists answer different questions (what
    // happened today vs the whole record), so a view choice in one shouldn't flip the other.
    private val RECENT_SESSIONS_FLAT_VIEW = booleanPreferencesKey("recent_sessions_flat_view")
    private val PROCRASTINATION_TODO_ENABLED = booleanPreferencesKey("procrastination_todo_enabled")
    private val PROCRASTINATION_TODO_CUTOFF = stringPreferencesKey("procrastination_todo_cutoff")
    private val PROCRASTINATION_TASK_ENABLED = booleanPreferencesKey("procrastination_task_enabled")
    private val PROCRASTINATION_TASK_KINDS = stringSetPreferencesKey("procrastination_task_kinds")
    private val PROCRASTINATION_PENALTY_AMOUNT = intPreferencesKey("procrastination_penalty_amount")
    // Set once the default TaskTypes have been seeded, so deleting every type doesn't resurrect
    // them on next launch. Device-local by design; cross-device double-seeding is instead made
    // harmless by the deterministic ids in TaskType.kt.
    private val TASK_TYPES_SEEDED = booleanPreferencesKey("task_types_seeded")
    // Both are Todos-screen view state, deliberately device-local: which branches you have folded
    // away is about how you are reading the list right now, not something the other devices on the
    // account should have decided for them.
    private val TODO_HIDE_COMPLETED = booleanPreferencesKey("todo_hide_completed")
    private val TODO_COLLAPSED_IDS = stringSetPreferencesKey("todo_collapsed_ids")
    // Calendar-sourced tasks are re-read from the system calendar on every refresh and have no
    // local row to delete, so "get this off my list" can only be a list of ids to skip. Device-
    // local for the same reason the events are: the calendar is a device-level account, not ours.
    private val HIDDEN_CALENDAR_TASK_IDS = stringSetPreferencesKey("hidden_calendar_task_ids")
    // FocusArea.name -- which area the user said they mostly use the app for. Device-local like
    // the rest of this store; each install asks once via the launch prompt. Defaults to TASKS: the
    // app is pitched as time management first (v2.14), so an unanswered prompt lands there.
    private val FOCUS_AREA = stringPreferencesKey("focus_area")
    private val FOCUS_PROMPT_SHOWN = booleanPreferencesKey("focus_prompt_shown")
    // Gates the What's New dialog: entries newer than this versionCode get shown once. 0 means
    // "never recorded" -- AppLaunchViewModel tells a fresh install (seed silently) apart from a
    // pre-feature upgrade (show everything) via PackageInfo install timestamps, since the absent
    // key alone can't. clearAll() resetting this means the dialog may show once more after an
    // account wipe; accepted.
    private val LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")
    // TodoAlarmStyle.name -- whether a todo alarm arrives as an alarm-channel heads-up (alarm sound,
    // vibration, lock screen) or an ordinary notification. Device-local: how loud this device gets
    // is about this device.
    private val TODO_ALARM_STYLE = stringPreferencesKey("todo_alarm_style")

    fun isDarkMode(): Flow<Boolean> = context.dataStore.data.map { it[IS_DARK_MODE] ?: false }
    fun getNotificationsEnabled(): Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    fun getShowValueOnDashboard(): Flow<Boolean> = context.dataStore.data.map { it[SHOW_VALUE_ON_DASHBOARD] ?: true }
    fun getInventorySortOption(): Flow<String> = context.dataStore.data.map { it[INVENTORY_SORT_OPTION] ?: "DATE_DESC" }
    fun getInventoryGroupOption(): Flow<String> = context.dataStore.data.map { it[INVENTORY_GROUP_OPTION] ?: "NONE" }
    fun getHiddenCategories(): Flow<Set<String>> = context.dataStore.data.map { it[INVENTORY_HIDDEN_CATEGORIES] ?: emptySet() }
    fun getHiddenCollections(): Flow<Set<String>> = context.dataStore.data.map { it[INVENTORY_HIDDEN_COLLECTIONS] ?: emptySet() }
    fun isHardFilterEnabled(): Flow<Boolean> = context.dataStore.data.map { it[INVENTORY_HARD_FILTER] ?: true }
    fun isInvertFilterEnabled(): Flow<Boolean> = context.dataStore.data.map { it[INVENTORY_INVERT_FILTER] ?: false }
    fun getExpandedItemIds(): Flow<Set<String>> = context.dataStore.data.map { it[INVENTORY_EXPANDED_ITEMS] ?: emptySet() }
    
    val customUsername: Flow<String?> = context.dataStore.data.map { it[CUSTOM_USERNAME] }
    
    fun getCurrencyCode(): Flow<String> = context.dataStore.data.map { it[CURRENCY_CODE] ?: "USD" }
    fun isAutoCurrencyEnabled(): Flow<Boolean> = context.dataStore.data.map { it[AUTO_CURRENCY] ?: true }
    
    val manualSyncId: Flow<String?> = context.dataStore.data.map { it[MANUAL_SYNC_ID] }
    
    fun isFlowModeEnabled(): Flow<Boolean> = context.dataStore.data.map { it[FLOW_MODE_ENABLED] ?: false }
    fun isInnerTaskEnabled(): Flow<Boolean> = context.dataStore.data.map { it[INNER_TASK_ENABLED] ?: false }
    fun hasSeenInnerTaskPrompt(): Flow<Boolean> = context.dataStore.data.map { it[INNER_TASK_PROMPT_SHOWN] ?: false }
    fun isTaskHistoryFlatView(): Flow<Boolean> = context.dataStore.data.map { it[TASK_HISTORY_FLAT_VIEW] ?: false }
    fun isRecentSessionsFlatView(): Flow<Boolean> = context.dataStore.data.map { it[RECENT_SESSIONS_FLAT_VIEW] ?: false }
    fun isProcrastinationTodoEnabled(): Flow<Boolean> = context.dataStore.data.map { it[PROCRASTINATION_TODO_ENABLED] ?: false }
    fun getProcrastinationTodoCutoff(): Flow<String> = context.dataStore.data.map { it[PROCRASTINATION_TODO_CUTOFF] ?: "B1" }
    fun isProcrastinationTaskEnabled(): Flow<Boolean> = context.dataStore.data.map { it[PROCRASTINATION_TASK_ENABLED] ?: false }
    fun getProcrastinationTaskKinds(): Flow<Set<String>> = context.dataStore.data.map { it[PROCRASTINATION_TASK_KINDS] ?: emptySet() }
    fun getProcrastinationPenaltyAmount(): Flow<Int> = context.dataStore.data.map { it[PROCRASTINATION_PENALTY_AMOUNT] ?: 2 }
    fun hasSeededTaskTypes(): Flow<Boolean> = context.dataStore.data.map { it[TASK_TYPES_SEEDED] ?: false }
    fun isTodoHideCompletedEnabled(): Flow<Boolean> = context.dataStore.data.map { it[TODO_HIDE_COMPLETED] ?: true }
    fun getFocusArea(): Flow<String> = context.dataStore.data.map { it[FOCUS_AREA] ?: "TASKS" }
    fun hasSeenFocusPrompt(): Flow<Boolean> = context.dataStore.data.map { it[FOCUS_PROMPT_SHOWN] ?: false }
    fun getLastSeenVersionCode(): Flow<Int> = context.dataStore.data.map { it[LAST_SEEN_VERSION_CODE] ?: 0 }
    fun getCollapsedTodoIds(): Flow<Set<String>> = context.dataStore.data.map { it[TODO_COLLAPSED_IDS] ?: emptySet() }
    fun getTodoAlarmStyle(): Flow<String> = context.dataStore.data.map { it[TODO_ALARM_STYLE] ?: "ALARM" }

    suspend fun setTodoAlarmStyle(name: String) {
        context.dataStore.edit { it[TODO_ALARM_STYLE] = name }
    }

    suspend fun setTodoHideCompleted(enabled: Boolean) {
        context.dataStore.edit { it[TODO_HIDE_COMPLETED] = enabled }
    }

    suspend fun saveCollapsedTodoIds(ids: Set<String>) {
        context.dataStore.edit { it[TODO_COLLAPSED_IDS] = ids }
    }

    fun getHiddenCalendarTaskIds(): Flow<Set<String>> =
        context.dataStore.data.map { it[HIDDEN_CALENDAR_TASK_IDS] ?: emptySet() }

    suspend fun hideCalendarTask(taskId: String) {
        context.dataStore.edit { it[HIDDEN_CALENDAR_TASK_IDS] = (it[HIDDEN_CALENDAR_TASK_IDS] ?: emptySet()) + taskId }
    }

    suspend fun clearHiddenCalendarTasks() {
        context.dataStore.edit { it.remove(HIDDEN_CALENDAR_TASK_IDS) }
    }

    suspend fun setTaskTypesSeeded() {
        context.dataStore.edit { it[TASK_TYPES_SEEDED] = true }
    }

    suspend fun toggleDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[IS_DARK_MODE] = enabled }
    }

    suspend fun toggleNotifications(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun toggleShowValue(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_VALUE_ON_DASHBOARD] = enabled }
    }

    suspend fun saveInventorySort(option: String) {
        context.dataStore.edit { it[INVENTORY_SORT_OPTION] = option }
    }

    suspend fun saveInventoryGroup(option: String) {
        context.dataStore.edit { it[INVENTORY_GROUP_OPTION] = option }
    }

    suspend fun saveHiddenCategories(categories: Set<String>) {
        context.dataStore.edit { it[INVENTORY_HIDDEN_CATEGORIES] = categories }
    }

    suspend fun saveHiddenCollections(collectionIds: Set<String>) {
        context.dataStore.edit { it[INVENTORY_HIDDEN_COLLECTIONS] = collectionIds }
    }

    suspend fun setHardFilterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[INVENTORY_HARD_FILTER] = enabled }
    }

    suspend fun setInvertFilterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[INVENTORY_INVERT_FILTER] = enabled }
    }

    suspend fun saveExpandedItems(itemIds: Set<String>) {
        context.dataStore.edit { it[INVENTORY_EXPANDED_ITEMS] = itemIds }
    }

    suspend fun saveCustomUsername(username: String?) {
        context.dataStore.edit {
            if (username.isNullOrBlank()) it.remove(CUSTOM_USERNAME)
            else it[CUSTOM_USERNAME] = username
        }
    }

    suspend fun saveCurrencyCode(code: String) {
        context.dataStore.edit { it[CURRENCY_CODE] = code }
    }

    suspend fun setAutoCurrencyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_CURRENCY] = enabled }
    }

    suspend fun saveManualSyncId(syncId: String?) {
        context.dataStore.edit {
            if (syncId.isNullOrBlank()) it.remove(MANUAL_SYNC_ID)
            else it[MANUAL_SYNC_ID] = syncId
        }
    }

    suspend fun setFlowModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[FLOW_MODE_ENABLED] = enabled }
    }

    suspend fun setInnerTaskEnabled(enabled: Boolean) {
        context.dataStore.edit { it[INNER_TASK_ENABLED] = enabled }
    }

    suspend fun setInnerTaskPromptShown(shown: Boolean) {
        context.dataStore.edit { it[INNER_TASK_PROMPT_SHOWN] = shown }
    }

    suspend fun setFocusArea(name: String) {
        context.dataStore.edit { it[FOCUS_AREA] = name }
    }

    suspend fun setFocusPromptShown(shown: Boolean) {
        context.dataStore.edit { it[FOCUS_PROMPT_SHOWN] = shown }
    }

    suspend fun setLastSeenVersionCode(code: Int) {
        context.dataStore.edit { it[LAST_SEEN_VERSION_CODE] = code }
    }

    suspend fun setTaskHistoryFlatView(enabled: Boolean) {
        context.dataStore.edit { it[TASK_HISTORY_FLAT_VIEW] = enabled }
    }

    suspend fun setRecentSessionsFlatView(enabled: Boolean) {
        context.dataStore.edit { it[RECENT_SESSIONS_FLAT_VIEW] = enabled }
    }

    suspend fun setProcrastinationTodoEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PROCRASTINATION_TODO_ENABLED] = enabled }
    }

    suspend fun setProcrastinationTodoCutoff(priorityName: String) {
        context.dataStore.edit { it[PROCRASTINATION_TODO_CUTOFF] = priorityName }
    }

    suspend fun setProcrastinationTaskEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PROCRASTINATION_TASK_ENABLED] = enabled }
    }

    suspend fun saveProcrastinationTaskKinds(kindNames: Set<String>) {
        context.dataStore.edit { it[PROCRASTINATION_TASK_KINDS] = kindNames }
    }

    suspend fun setProcrastinationPenaltyAmount(amount: Int) {
        context.dataStore.edit { it[PROCRASTINATION_PENALTY_AMOUNT] = amount }
    }

    /**
     * Drops every stored preference, returning this store to its fresh-install state.
     *
     * Deliberately unconditional rather than a list of "account-ish" keys: MANUAL_SYNC_ID,
     * CUSTOM_USERNAME and TASK_TYPES_SEEDED would each silently carry a deleted account's state
     * into the next one, and picking which of the rest survive is a judgement the wipe shouldn't
     * be making. See [LocalDataRepository], the only caller.
     */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
