package com.vibecheck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val VibeCheckColors = lightColorScheme(
    primary = Color(0xFF235FA4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF00264A),
    secondary = Color(0xFF4E5E79),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF141C28),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141C28),
    surfaceVariant = Color(0xFFE5EAF2),
    onSurfaceVariant = Color(0xFF39404C),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val BaseTypography = Typography(
    headlineLarge = TextStyle(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)

@Immutable
data class VibeCheckDesignTokens(
    val spacing: VibeCheckSpacing,
    val sizes: VibeCheckSizes
)

@Composable
fun VibeCheckTheme(
    content: @Composable () -> Unit
) {
    val tokens = remember { VibeCheckDesignTokens(spacing = VibeCheckSpacing(), sizes = VibeCheckSizes()) }

    CompositionLocalProvider(
        LocalSpacing provides tokens.spacing,
        LocalSizes provides tokens.sizes
    ) {
        MaterialTheme(
            colorScheme = VibeCheckColors,
            typography = BaseTypography,
            content = content
        )
    }
}

@Stable
object VibeCheckThemeTokens {
    val spacing: VibeCheckSpacing
        @Composable get() = LocalSpacing.current

    val sizes: VibeCheckSizes
        @Composable get() = LocalSizes.current
}
