package com.controlldeck.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark Material3 color scheme — the only theme for the MVP (top-level
 * spec: dark theme dashboard visuals). No light-mode branch by design.
 */
private val ControlDeckAccent = Color(0xFF7C5CFF)
private val ControlDeckAccentVariant = Color(0xFF35D0BA)
private val ControlDeckBackground = Color(0xFF0E1116)
private val ControlDeckSurface = Color(0xFF161B22)
private val ControlDeckSurfaceVariant = Color(0xFF1F2630)
private val ControlDeckError = Color(0xFFFF6B6B)
private val ControlDeckOnColor = Color(0xFFEAEEF5)
private val ControlDeckOffline = Color(0xFF6B7280)

val ControlDeckColorScheme = darkColorScheme(
    primary = ControlDeckAccent,
    onPrimary = Color.White,
    secondary = ControlDeckAccentVariant,
    onSecondary = Color.Black,
    background = ControlDeckBackground,
    onBackground = ControlDeckOnColor,
    surface = ControlDeckSurface,
    onSurface = ControlDeckOnColor,
    surfaceVariant = ControlDeckSurfaceVariant,
    onSurfaceVariant = ControlDeckOnColor.copy(alpha = 0.7f),
    error = ControlDeckError,
    onError = Color.White,
    outline = ControlDeckOffline,
)

/** Consistent color for an "offline" badge/disabled widget, per docs/ARCHITECTURE.md §7. */
val OfflineIndicatorColor = ControlDeckOffline

@Composable
fun ControlDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ControlDeckColorScheme,
        content = content,
    )
}
