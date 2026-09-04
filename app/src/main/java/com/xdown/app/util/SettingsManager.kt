package com.xdown.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        private val SAVE_NOTIFICATIONS = booleanPreferencesKey("save_notifications")
        private val DOWNLOAD_PATH = stringPreferencesKey("download_path")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE] ?: true
    }

    val defaultQuality: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_QUALITY] ?: "best"
    }

    val saveNotifications: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SAVE_NOTIFICATIONS] ?: true
    }

    val downloadPath: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DOWNLOAD_PATH] ?: "Downloads/XDown"
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE] = enabled
        }
    }

    suspend fun setDefaultQuality(quality: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_QUALITY] = quality
        }
    }

    suspend fun setSaveNotifications(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SAVE_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setDownloadPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_PATH] = path
        }
    }
}
