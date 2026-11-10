package com.projectmediapro.app.di

import android.content.Context
import com.projectmediapro.app.BuildConfig
import com.projectmediapro.app.settings.DataStoreThemePreferencesStore
import com.projectmediapro.app.settings.PrivacyPolicyDataSource
import com.projectmediapro.app.settings.RawResourcePrivacyPolicyDataSource
import com.projectmediapro.app.settings.ThemePreferencesStore

/**
 * Plain manual dependency container created once in [com.projectmediapro.app.MediaProApplication].
 * Keeps `AppNavHost` free of feature-specific parameters.
 */
class AppContainer(appContext: Context) {
    val themePreferencesStore: ThemePreferencesStore = DataStoreThemePreferencesStore(appContext)
    val privacyPolicyDataSource: PrivacyPolicyDataSource = RawResourcePrivacyPolicyDataSource(appContext)
    val appVersionName: String = BuildConfig.VERSION_NAME
}
