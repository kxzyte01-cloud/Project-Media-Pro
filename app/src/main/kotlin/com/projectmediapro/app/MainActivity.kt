package com.projectmediapro.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectmediapro.app.navigation.AppNavHost
import com.projectmediapro.app.settings.ThemePreference
import com.projectmediapro.core.designsystem.theme.ProjectMediaProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MediaProApplication).container

        setContent {
            val themePreference by container.themePreferencesStore.themePreference
                .collectAsStateWithLifecycle(initialValue = ThemePreference.SYSTEM)

            val useDarkTheme = when (themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            DisposableEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { useDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LIGHT_NAVIGATION_SCRIM,
                        DARK_NAVIGATION_SCRIM,
                    ) { useDarkTheme },
                )
                onDispose {}
            }

            ProjectMediaProTheme(darkTheme = useDarkTheme) {
                AppNavHost(container = container)
            }
        }
    }

    private companion object {
        val LIGHT_NAVIGATION_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_NAVIGATION_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
