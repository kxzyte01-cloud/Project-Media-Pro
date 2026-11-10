package com.projectmediapro.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Abstraction over theme preference persistence so unit tests can provide an in-memory store.
 */
interface ThemePreferencesStore {
    val themePreference: Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "project_media_pro_settings",
)

/**
 * Production implementation backed by Jetpack DataStore Preferences.
 */
class DataStoreThemePreferencesStore(
    private val appContext: Context,
) : ThemePreferencesStore {

    override val themePreference: Flow<ThemePreference> =
        appContext.settingsDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                ThemePreference.fromStorageKey(preferences[KEY_THEME_PREFERENCE])
            }

    override suspend fun setThemePreference(preference: ThemePreference) {
        appContext.settingsDataStore.edit { preferences ->
            preferences[KEY_THEME_PREFERENCE] = preference.storageKey
        }
    }

    private companion object {
        val KEY_THEME_PREFERENCE = stringPreferencesKey("appearance_theme")
    }
}
