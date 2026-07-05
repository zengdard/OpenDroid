package com.raissummit.orchestrator.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "orchestrator_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val MODEL_DOWNLOADED = booleanPreferencesKey("model_downloaded")
        private val MODEL_PATH = stringPreferencesKey("model_path")
        private val MAX_TOKENS = intPreferencesKey("max_tokens")
        private val TEMPERATURE = stringPreferencesKey("temperature")
        private val ACCESSIBILITY_ENABLED = booleanPreferencesKey("accessibility_enabled")
    }

    val isModelDownloaded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[MODEL_DOWNLOADED] ?: false
    }

    val modelPath: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[MODEL_PATH]
    }

    val accessibilityEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ACCESSIBILITY_ENABLED] ?: false
    }

    suspend fun setModelDownloaded(path: String) {
        context.dataStore.edit { prefs ->
            prefs[MODEL_DOWNLOADED] = true
            prefs[MODEL_PATH] = path
        }
    }

    suspend fun clearModel() {
        context.dataStore.edit { prefs ->
            prefs[MODEL_DOWNLOADED] = false
            prefs[MODEL_PATH] = ""
        }
    }

    suspend fun setAccessibilityEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ACCESSIBILITY_ENABLED] = enabled
        }
    }
}
