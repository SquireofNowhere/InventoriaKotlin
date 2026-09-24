package com.inventoria.web.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.inventoria.shared.model.TaskKind

// The Android app's palette (app/.../ui/theme/Color.kt), so both clients look like one product.
private val PurplePrimary = Color(0xFF8B5CF6)
private val PurplePrimaryDark = Color(0xFF7C3AED)
private val PurplePrimaryLight = Color(0xFFA78BFA)
private val PurpleSecondary = Color(0xFFC084FC)
private val PurpleSecondaryDark = Color(0xFFA855F7)
private val PurpleSecondaryLight = Color(0xFFD8B4FE)
private val PurpleAccent = Color(0xFFE879F9)
private val PurpleAccentLight = Color(0xFFF0ABFC)
private val ErrorRed = Color(0xFFEF4444)

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurplePrimaryLight,
    onPrimaryContainer = PurplePrimaryDark,
    secondary = PurpleSecondary,
    onSecondary = Color.White,
    secondaryContainer = PurpleSecondaryLight,
    onSecondaryContainer = PurpleSecondaryDark,
    tertiary = PurpleAccent,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF1E293B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = Color.Black,
    primaryContainer = PurplePrimaryDark,
    onPrimaryContainer = PurplePrimaryLight,
    secondary = PurpleSecondaryLight,
    onSecondary = Color.Black,
    secondaryContainer = PurpleSecondaryDark,
    onSecondaryContainer = PurpleSecondaryLight,
    tertiary = PurpleAccentLight,
    onTertiary = Color.Black,
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = ErrorRed
)

@Composable
fun InventoriaTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

val TaskKind.color: Color get() = Color(colorValue.toInt())

/**
 * The kind's name without its leading emoji: the web build ships no emoji font, so the glyph would
 * draw as a box. The colour dot next to it carries the same meaning.
 */
val TaskKind.label: String get() = displayName.substringAfter(' ')
