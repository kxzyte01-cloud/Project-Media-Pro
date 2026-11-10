package com.projectmediapro.app.settings

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Immutable UI state for the Settings screen.
 */
data class SettingsUiState(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val privacyPolicyText: String = "",
    val appVersionName: String = "",
)

/**
 * User events emitted from the Settings screen.
 */
sealed interface SettingsEvent {
    data class SelectTheme(val preference: ThemePreference) : SettingsEvent
}

/**
 * Unidirectional ViewModel for Settings. Loads the offline privacy policy via [PrivacyPolicyDataSource].
 */
class SettingsViewModel(
    private val themePreferencesStore: ThemePreferencesStore,
    private val privacyPolicyDataSource: PrivacyPolicyDataSource,
    private val appVersionName: String,
) : ViewModel() {

    private val privacyPolicyFlow = MutableStateFlow("")

    val uiState: StateFlow<SettingsUiState> = combine(
        themePreferencesStore.themePreference,
        privacyPolicyFlow,
    ) { preference, policyText ->
        SettingsUiState(
            themePreference = preference,
            privacyPolicyText = policyText,
            appVersionName = appVersionName,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(
            themePreference = ThemePreference.SYSTEM,
            privacyPolicyText = "",
            appVersionName = appVersionName,
        ),
    )

    init {
        viewModelScope.launch {
            privacyPolicyFlow.value = try {
                privacyPolicyDataSource.loadPrivacyPolicy()
            } catch (_: Resources.NotFoundException) {
                ""
            } catch (_: IOException) {
                ""
            }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SelectTheme -> {
                viewModelScope.launch {
                    try {
                        themePreferencesStore.setThemePreference(event.preference)
                    } catch (_: IOException) {
                        // The write failed, so the previously saved preference stays in effect.
                    }
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            themePreferencesStore: ThemePreferencesStore,
            privacyPolicyDataSource: PrivacyPolicyDataSource,
            appVersionName: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    themePreferencesStore = themePreferencesStore,
                    privacyPolicyDataSource = privacyPolicyDataSource,
                    appVersionName = appVersionName,
                ) as T
            }
        }
    }
}
