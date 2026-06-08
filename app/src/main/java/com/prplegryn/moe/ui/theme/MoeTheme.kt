package com.prplegryn.moe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0A2E6F),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F3EF),
    onSecondaryContainer = Color(0xFF073B36),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFE7E9EE),
    onSurfaceVariant = Color(0xFF4B5563),
    outlineVariant = Color(0xFFE1E4EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF08245C),
    primaryContainer = Color(0xFF163D91),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF7DD8CF),
    onSecondary = Color(0xFF052F2B),
    secondaryContainer = Color(0xFF115B55),
    onSecondaryContainer = Color(0xFFD6F3EF),
    tertiary = Color(0xFFD3B8FF),
    onTertiary = Color(0xFF31106F),
    background = Color(0xFF101114),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF16181D),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2A2D34),
    onSurfaceVariant = Color(0xFFC3C7D0),
    outlineVariant = Color(0xFF323640),
)

private val MoeShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
)

private val MoeTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        bodyMedium = bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    )
}

@Composable
fun MoeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MoeTypography,
        shapes = MoeShapes,
        content = content,
    )
}
