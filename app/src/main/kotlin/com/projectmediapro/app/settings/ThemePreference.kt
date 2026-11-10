package com.projectmediapro.app.settings

/**
 * User-selectable appearance modes.
 */
enum class ThemePreference(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromStorageKey(value: String?): ThemePreference {
            return entries.firstOrNull { it.storageKey == value } ?: SYSTEM
        }
    }
}

