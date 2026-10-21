package com.jarvis.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF00E5FF), onPrimary = Color(0xFF00252B),
    secondary = Color(0xFF2979FF), onSecondary = Color.White,
    background = Color(0xFF050A14), onBackground = Color(0xFFE0F7FA),
    surface = Color(0xFF0B1426), onSurface = Color(0xFFE0F7FA),
    surfaceVariant = Color(0xFF0F1D38), onSurfaceVariant = Color(0xFF9FB8D0),
    outline = Color(0xFF1E88E5), error = Color(0xFFFF5252)
)

private val Light = lightColorScheme(
    primary = Color(0xFF006C80), onPrimary = Color.White,
    secondary = Color(0xFF1B5FD6), onSecondary = Color.White,
    background = Color(0xFFF2F9FC), onBackground = Color(0xFF0A1A24),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0A1A24),
    surfaceVariant = Color(0xFFDDEEF5), onSurfaceVariant = Color(0xFF3B5566),
    outline = Color(0xFF1E88E5), error = Color(0xFFB3261E)
)

@Composable
fun JarvisTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
