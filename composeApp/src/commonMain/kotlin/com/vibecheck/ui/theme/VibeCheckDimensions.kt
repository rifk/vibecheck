package com.vibecheck.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class VibeCheckSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp
)

@Immutable
data class VibeCheckSizes(
    val minTouchTarget: Dp = 44.dp,
    val compactMaxWidth: Dp = 599.dp,
    val mediumMaxWidth: Dp = 1023.dp,
    val contentMaxWidth: Dp = 1040.dp
)

internal val LocalSpacing = staticCompositionLocalOf { VibeCheckSpacing() }
internal val LocalSizes = staticCompositionLocalOf { VibeCheckSizes() }
