package com.projectmediapro.app.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable UI state for the Home screen in Step 0.
 */
data class HomeUiState(
    val hasSavedProjects: Boolean = false,
)

/**
 * Unidirectional ViewModel for the Home screen.
 */
class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
