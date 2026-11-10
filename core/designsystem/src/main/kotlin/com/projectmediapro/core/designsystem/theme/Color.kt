package com.projectmediapro.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand and accessible UI palette for Project Media Pro.
 *
 * Computed WCAG 2.1 contrast ratios (all theme text/background pairs >= 4.5:1):
 * - dark Blue (#2F80ED) / Ink (#111418): 4.78:1
 * - dark Red (#E5484D) / Ink (#111418): 4.72:1
 * - dark Slate (#3A4452) / Light (#F5F7FA): 9.19:1
 * - Ink (#111418) / Light (#F5F7FA): 17.21:1
 * - light blue (#1A62C9) / white (#FFFFFF): 5.78:1
 * - light blue (#1A62C9) / Light (#F5F7FA): 5.38:1
 * - light red (#C8232C) / white (#FFFFFF): 5.63:1
 * - Slate (#3A4452) / white (#FFFFFF): 9.87:1
 * - Ink (#111418) / white (#FFFFFF): 18.47:1
 */
object MediaProTokens {
    val Ink = Color(0xFF111418)
    val Blue = Color(0xFF2F80ED)
    val Red = Color(0xFFE5484D)
    val Light = Color(0xFFF5F7FA)
    val Slate = Color(0xFF3A4452)
    val PureWhite = Color(0xFFFFFFFF)

    val LightPrimaryBlue = Color(0xFF1A62C9)
    val LightErrorRed = Color(0xFFC8232C)
}
