package com.projectmediapro.app.settings

import android.content.Context
import com.projectmediapro.app.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the bundled offline privacy policy text off the main thread.
 */
interface PrivacyPolicyDataSource {
    suspend fun loadPrivacyPolicy(): String
}

class RawResourcePrivacyPolicyDataSource(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PrivacyPolicyDataSource {

    override suspend fun loadPrivacyPolicy(): String = withContext(ioDispatcher) {
        appContext.resources
            .openRawResource(R.raw.privacy_policy)
            .bufferedReader(Charsets.UTF_8)
            .use { reader -> reader.readText() }
    }
}
