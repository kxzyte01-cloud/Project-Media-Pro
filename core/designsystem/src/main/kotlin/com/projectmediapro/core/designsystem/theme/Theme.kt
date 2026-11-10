package com.projectmediapro.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColorScheme(
    primary = MediaProTokens.Blue,
    onPrimary = MediaProTokens.Ink,
    secondary = MediaProTokens.Slate,
    onSecondary = MediaProTokens.Light,
    error = MediaProTokens.Red,
    onError = MediaProTokens.Ink,
    background = MediaProTokens.Ink,
    onBackground = MediaProTokens.Light,
    surface = MediaProTokens.Ink,
    onSurface = MediaProTokens.Light,
    surfaceVariant = MediaProTokens.Slate,
    onSurfaceVariant = MediaProTokens.Light,
    outline = MediaProTokens.Slate,
)

private val LightColorPalette = lightColorScheme(
    primary = MediaProTokens.LightPrimaryBlue,
    onPrimary = MediaProTokens.PureWhite,
    secondary = MediaProTokens.Slate,
    onSecondary = MediaProTokens.PureWhite,
    error = MediaProTokens.LightErrorRed,
    onError = MediaProTokens.PureWhite,
    background = MediaProTokens.Light,
    onBackground = MediaProTokens.Ink,
    surface = MediaProTokens.PureWhite,
    onSurface = MediaProTokens.Ink,
    surfaceVariant = MediaProTokens.Light,
    onSurfaceVariant = MediaProTokens.Slate,
    outline = MediaProTokens.Slate,
)

private val DesignSystemShapes = Shapes(
    extraSmall = MediaProShapes.Small,
    small = MediaProShapes.Small,
    medium = MediaProShapes.Medium,
    large = MediaProShapes.Medium,
    extraLarge = MediaProShapes.Medium,
)

@Composable
fun ProjectMediaProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MediaProTypography,
        shapes = DesignSystemShapes,
        content = content,
    )
}
