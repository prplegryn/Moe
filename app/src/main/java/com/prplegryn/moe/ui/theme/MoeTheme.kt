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
    primary = Color(0xFFB42835),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DD),
    onPrimaryContainer = Color(0xFF410007),
    secondary = Color(0xFF735C2C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA6),
    onSecondaryContainer = Color(0xFF261900),
    tertiary = Color(0xFF315F72),
    onTertiary = Color.White,
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1D1B18),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1D1B18),
    surfaceVariant = Color(0xFFE7E1D8),
    onSurfaceVariant = Color(0xFF4A463F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB3B9),
    onPrimary = Color(0xFF690011),
    primaryContainer = Color(0xFF8F1422),
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = Color(0xFFE8C27B),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF594319),
    onSecondaryContainer = Color(0xFFFFDEA6),
    tertiary = Color(0xFFA4CDDE),
    onTertiary = Color(0xFF003544),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFE8E1D8),
    surface = Color(0xFF0D0F12),
    onSurface = Color(0xFFE8E1D8),
    surfaceVariant = Color(0xFF272A2F),
    onSurfaceVariant = Color(0xFFC8C5BD),
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
