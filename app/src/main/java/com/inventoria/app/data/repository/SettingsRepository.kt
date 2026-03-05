package com.inventoria.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val SHOW_VALUE_ON_DASHBOARD = booleanPreferencesKey("show_value_on_dashboard")
    
    // Inventory Filter Persistence
    private val INVENTORY_SORT_OPTION = stringPreferencesKey("inv_sort_option")
    private val INVENTORY_GROUP_OPTION = stringPreferencesKey("inv_group_option")
    private val INVENTORY_HIDDEN_CATEGORIES = stringSetPreferencesKey("inv_hidden_cats")
    private val INVENTORY_HIDDEN_COLLECTIONS = stringSetPreferencesKey("inv_hidden_colls")
    private val INVENTORY_HARD_FILTER = booleanPreferencesKey("inv_hard_filter")
    private val INVENTORY_EXPANDED_ITEMS = stringSetPreferencesKey("inv_expanded_items")
    
    // Custom Username
    private val CUSTOM_USERNAME = stringPreferencesKey("custom_username")

    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[IS_DARK_MODE] ?: false }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[NOTIFICATIONS_ENABLED] ?: true }

    val showValueOnDashboard: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SHOW_VALUE_ON_DASHBOARD] ?: true }

    // Inventory Filter Flows
    val inventorySortOption: Flow<String?> = context.dataStore.data
        .map { it[INVENTORY_SORT_OPTION] }
    
    val inventoryGroupOption: Flow<String?> = context.dataStore.data
        .map { it[INVENTORY_GROUP_OPTION] }
        
    val hiddenCategories: Flow<Set<String>> = context.dataStore.data
        .map { it[INVENTORY_HIDDEN_CATEGORIES] ?: emptySet() }
        
    val hiddenCollections: Flow<Set<String>> = context.dataStore.data
        .map { it[INVENTORY_HIDDEN_COLLECTIONS] ?: emptySet() }

    val isHardFilterEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[INVENTORY_HARD_FILTER] ?: true }

    val expandedItemIds: Flow<Set<String>> = context.dataStore.data
        .map { it[INVENTORY_EXPANDED_ITEMS] ?: emptySet() }
        
    val customUsername: Flow<String?> = context.dataStore.data
        .map { it[CUSTOM_USERNAME] }

    suspend fun toggleDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[IS_DARK_MODE] = enabled }
    }

    suspend fun toggleNotifications(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun toggleShowValue(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_VALUE_ON_DASHBOARD] = enabled }
    }

    // Persist Inventory Filters
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

    suspend fun saveExpandedItems(itemIds: Set<String>) {
        context.dataStore.edit { it[INVENTORY_EXPANDED_ITEMS] = itemIds }
    }
    
    suspend fun saveCustomUsername(username: String?) {
        context.dataStore.edit { prefs ->
            if (username.isNullOrBlank()) {
                prefs.remove(CUSTOM_USERNAME)
            } else {
                prefs[CUSTOM_USERNAME] = username
            }
        }
    }
}
