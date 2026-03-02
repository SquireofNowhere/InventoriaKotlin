package com.inventoria.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[IS_DARK_MODE] ?: false }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[NOTIFICATIONS_ENABLED] ?: true }

    val showValueOnDashboard: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[SHOW_VALUE_ON_DASHBOARD] ?: true }

    suspend fun toggleDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[IS_DARK_MODE] = enabled }
    }

    suspend fun toggleNotifications(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun toggleShowValue(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_VALUE_ON_DASHBOARD] = enabled }
    }
}
