package com.prplegryn.moe.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5F5C),
    onPrimary = Color.White,
    secondary = Color(0xFF7B5E23),
    onSecondary = Color.White,
    tertiary = Color(0xFF4E5F8F),
    onTertiary = Color.White,
    background = Color(0xFFFFFDF7),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFDF7),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFE3E2D8),
    onSurfaceVariant = Color(0xFF46483F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6CCC8),
    onPrimary = Color(0xFF063735),
    secondary = Color(0xFFE9C16D),
    onSecondary = Color(0xFF402D00),
    tertiary = Color(0xFFB8C7FF),
    onTertiary = Color(0xFF1D2F5F),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE5E2DA),
    surface = Color(0xFF111413),
    onSurface = Color(0xFFE5E2DA),
    surfaceVariant = Color(0xFF424841),
    onSurfaceVariant = Color(0xFFC4C8BE),
)

private val MoeShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
)

@Composable
fun MoeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MoeShapes,
        content = content,
    )
}
