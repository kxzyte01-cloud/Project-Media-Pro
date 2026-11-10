package com.projectmediapro.app.settings

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Proves that ThemePreference.fromStorageKey maps known keys accurately and falls back to SYSTEM on unknown or null input.
    @Test
    fun themePreference_fromStorageKey_mapsKnownAndFallbackValues() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorageKey("system"))
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromStorageKey("light"))
        assertEquals(ThemePreference.DARK, ThemePreference.fromStorageKey("dark"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorageKey("unexpected_value"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorageKey(null))
    }

    // Proves that SettingsViewModel loads privacy policy text via PrivacyPolicyDataSource and updates themePreference when SelectTheme events arrive.
    @Test
    fun settingsViewModel_selectThemeEvent_updatesUiStateViaFakeStoreAndDataSource() = runTest(testDispatcher) {
        val fakeStore = FakeThemePreferencesStore()
        val fakePrivacySource = FakePrivacyPolicyDataSource("Offline policy text")
        val viewModel = SettingsViewModel(
            themePreferencesStore = fakeStore,
            privacyPolicyDataSource = fakePrivacySource,
            appVersionName = "0.1.0",
        )

        val collectorJob = backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }

        assertEquals(ThemePreference.SYSTEM, viewModel.uiState.value.themePreference)
        assertEquals("Offline policy text", viewModel.uiState.value.privacyPolicyText)
        assertEquals("0.1.0", viewModel.uiState.value.appVersionName)

        viewModel.onEvent(SettingsEvent.SelectTheme(ThemePreference.DARK))
        assertEquals(ThemePreference.DARK, viewModel.uiState.value.themePreference)

        viewModel.onEvent(SettingsEvent.SelectTheme(ThemePreference.LIGHT))
        assertEquals(ThemePreference.LIGHT, viewModel.uiState.value.themePreference)

        collectorJob.cancel()
    }

    // Proves that an IOException from the privacy policy source is handled: the loader ran exactly once and the text stays empty without crashing.
    @Test
    fun settingsViewModel_whenPrivacyPolicyThrowsIoException_leavesTextEmpty() = runTest(testDispatcher) {
        val fakeStore = FakeThemePreferencesStore()
        val failingPrivacySource = FailingPrivacyPolicyDataSource()
        val viewModel = SettingsViewModel(
            themePreferencesStore = fakeStore,
            privacyPolicyDataSource = failingPrivacySource,
            appVersionName = "0.1.0",
        )

        val collectorJob = backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }

        assertEquals(1, failingPrivacySource.callCount)
        assertEquals("", viewModel.uiState.value.privacyPolicyText)
        collectorJob.cancel()
    }

    // Proves that a failed theme write was attempted exactly once, is swallowed, and leaves the displayed theme preference unchanged.
    @Test
    fun settingsViewModel_whenThemeWriteFails_keepsCurrentPreferenceWithoutCrashing() = runTest(testDispatcher) {
        val failingStore = FailingWriteThemePreferencesStore()
        val viewModel = SettingsViewModel(
            themePreferencesStore = failingStore,
            privacyPolicyDataSource = FakePrivacyPolicyDataSource("Offline policy text"),
            appVersionName = "0.1.0",
        )

        val collectorJob = backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }

        viewModel.onEvent(SettingsEvent.SelectTheme(ThemePreference.DARK))

        assertEquals(1, failingStore.writeAttempts)
        assertEquals(ThemePreference.SYSTEM, viewModel.uiState.value.themePreference)
        collectorJob.cancel()
    }
}

private class FakeThemePreferencesStore(
    initial: ThemePreference = ThemePreference.SYSTEM,
) : ThemePreferencesStore {
    private val state = MutableStateFlow(initial)
    override val themePreference: Flow<ThemePreference> = state

    override suspend fun setThemePreference(preference: ThemePreference) {
        state.value = preference
    }
}

private class FakePrivacyPolicyDataSource(
    private val policyText: String,
) : PrivacyPolicyDataSource {
    override suspend fun loadPrivacyPolicy(): String = policyText
}

private class FailingPrivacyPolicyDataSource : PrivacyPolicyDataSource {
    var callCount = 0
        private set

    override suspend fun loadPrivacyPolicy(): String {
        callCount++
        throw IOException("Simulated read failure")
    }
}

private class FailingWriteThemePreferencesStore : ThemePreferencesStore {
    var writeAttempts = 0
        private set

    override val themePreference: Flow<ThemePreference> = MutableStateFlow(ThemePreference.SYSTEM)

    override suspend fun setThemePreference(preference: ThemePreference) {
        writeAttempts++
        throw IOException("Simulated write failure")
    }
}
