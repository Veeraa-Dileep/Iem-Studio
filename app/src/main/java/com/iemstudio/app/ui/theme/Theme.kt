package com.iemstudio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241703),
    primaryContainer = AmberGlow,
    onPrimaryContainer = Amber,
    secondary = AmberDeep,
    onSecondary = Color(0xFF241703),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A3E49),
    outlineVariant = Color(0xFF2A2E38),
    error = Danger,
    onError = Color(0xFF2B0A0A),
)

/** The app is a fixed dark theme — audio tools live in the dark. */
@Composable
fun IemTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content,
    )
}
