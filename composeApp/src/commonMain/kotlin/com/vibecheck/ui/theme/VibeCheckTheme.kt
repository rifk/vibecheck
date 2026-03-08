package com.vibecheck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val VibeCheckColors = lightColorScheme(
    primary = Color(0xFFB35A2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4D7BF),
    onPrimaryContainer = Color(0xFF40200F),
    secondary = Color(0xFF1F5C63),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5EEDF),
    onBackground = Color(0xFF1D1A16),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF1D1A16),
    surfaceVariant = Color(0xFFE7D9C9),
    onSurfaceVariant = Color(0xFF5E5347),
    tertiary = Color(0xFF5B6B34),
    onTertiary = Color(0xFFFFFFFF),
    outline = Color(0xFFC2AF99),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val BaseTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 33.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 29.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.7.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp
    )
)

private val VibeCheckShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
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
            shapes = VibeCheckShapes,
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
