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
}
