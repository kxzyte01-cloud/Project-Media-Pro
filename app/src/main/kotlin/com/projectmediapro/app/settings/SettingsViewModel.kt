package com.projectmediapro.app.settings

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.projectmediapro.feature.captions.WhisperModelManager
import com.projectmediapro.feature.captions.WhisperModelState
import java.io.IOException
import kotlinx.coroutines.Job
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
    val whisperModelState: WhisperModelState = WhisperModelState.NotInstalled,
)

/**
 * User events emitted from the Settings screen.
 */
sealed interface SettingsEvent {
    data class SelectTheme(val preference: ThemePreference) : SettingsEvent
    data object DownloadWhisperModel : SettingsEvent
    data object CancelWhisperDownload : SettingsEvent
    data object DeleteWhisperModel : SettingsEvent
}

/**
 * Unidirectional ViewModel for Settings. Loads the offline privacy policy via [PrivacyPolicyDataSource]
 * and manages the optional on-device Open Whisper model.
 */
class SettingsViewModel(
    private val themePreferencesStore: ThemePreferencesStore,
    private val privacyPolicyDataSource: PrivacyPolicyDataSource,
    private val appVersionName: String,
    private val whisperModelManager: WhisperModelManager? = null,
) : ViewModel() {

    private val privacyPolicyFlow = MutableStateFlow("")
    private val fallbackWhisperFlow = MutableStateFlow<WhisperModelState>(WhisperModelState.NotInstalled)
    private var whisperDownloadJob: Job? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        themePreferencesStore.themePreference,
        privacyPolicyFlow,
        whisperModelManager?.state ?: fallbackWhisperFlow,
    ) { preference, policyText, whisperState ->
        SettingsUiState(
            themePreference = preference,
            privacyPolicyText = policyText,
            appVersionName = appVersionName,
            whisperModelState = whisperState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(
            themePreference = ThemePreference.SYSTEM,
            privacyPolicyText = "",
            appVersionName = appVersionName,
            whisperModelState = whisperModelManager?.state?.value ?: WhisperModelState.NotInstalled,
        ),
    )

    init {
        whisperModelManager?.refreshState()
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
            SettingsEvent.DownloadWhisperModel -> {
                val manager = whisperModelManager ?: return
                if (whisperDownloadJob?.isActive == true) return
                whisperDownloadJob = viewModelScope.launch {
                    manager.downloadModel()
                }
            }
            SettingsEvent.CancelWhisperDownload -> {
                whisperDownloadJob?.cancel()
                whisperDownloadJob = null
            }
            SettingsEvent.DeleteWhisperModel -> {
                whisperDownloadJob?.cancel()
                whisperDownloadJob = null
                whisperModelManager?.deleteModel()
            }
        }
    }

    override fun onCleared() {
        whisperDownloadJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun provideFactory(
            themePreferencesStore: ThemePreferencesStore,
            privacyPolicyDataSource: PrivacyPolicyDataSource,
            appVersionName: String,
            whisperModelManager: WhisperModelManager? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    themePreferencesStore = themePreferencesStore,
                    privacyPolicyDataSource = privacyPolicyDataSource,
                    appVersionName = appVersionName,
                    whisperModelManager = whisperModelManager,
                ) as T
            }
        }
    }
}
