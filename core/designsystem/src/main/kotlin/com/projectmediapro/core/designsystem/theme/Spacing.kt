package com.projectmediapro.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Strict spacing scale (4 / 8 / 12 / 16 / 24 / 32 dp) and touch target floor (48dp).
 */
object MediaProSpacing {
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val MediumSmall: Dp = 12.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 24.dp
    val ExtraLarge: Dp = 32.dp
    val MinTouchTarget: Dp = 48.dp
}

/**
 * At most two corner radii across the entire design system.
 */
object MediaProShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
}

