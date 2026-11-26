package com.projectmediapro.feature.news

import android.content.Context
import android.content.SharedPreferences
import com.projectmediapro.core.model.NewsBrandPreset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Offline persistence store for reusable Channel Brand Presets.
 * Ships 4 built-in broadcast presets and supports unlimited user-created presets in SharedPreferences.
 */
class NewsBrandPresetStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadAll(): List<NewsBrandPreset> = NewsBrandPreset.BUILT_IN_PRESETS + loadCustom()

    fun loadCustom(): List<NewsBrandPreset> {
        val raw = prefs.getString(KEY_CUSTOM_PRESETS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<NewsBrandPreset>>(raw)
                .map { it.copy(builtIn = false) }
        }.getOrDefault(emptyList())
    }

    fun saveCustom(preset: NewsBrandPreset): List<NewsBrandPreset> {
        val cleanName = preset.name.trim().ifBlank { "My Channel" }
        val normalized = preset.copy(
            id = preset.id.ifBlank { "custom_${System.currentTimeMillis()}" },
            name = cleanName,
            builtIn = false,
        )
        val existing = loadCustom().filterNot { it.id == normalized.id }
        val updated = listOf(normalized) + existing
        persistCustom(updated)
        return NewsBrandPreset.BUILT_IN_PRESETS + updated
    }

    fun deleteCustom(presetId: String): List<NewsBrandPreset> {
        val updated = loadCustom().filterNot { it.id == presetId }
        persistCustom(updated)
        return NewsBrandPreset.BUILT_IN_PRESETS + updated
    }

    private fun persistCustom(customPresets: List<NewsBrandPreset>) {
        val encoded = runCatching { json.encodeToString(customPresets) }.getOrNull() ?: return
        prefs.edit().putString(KEY_CUSTOM_PRESETS, encoded).apply()
    }

    companion object {
        private const val PREFS_NAME = "project_media_pro_news_brand_presets"
        private const val KEY_CUSTOM_PRESETS = "custom_presets_json_v1"
    }
}
