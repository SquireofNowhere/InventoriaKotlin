package com.inventoria.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color


private val LightColorScheme = lightColorScheme(
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
    tertiaryContainer = PurpleAccentLight,
    onTertiaryContainer = PurplePrimaryDark,
    
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    background = LightBackground,
    onBackground = LightOnSurface,
    
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    
    scrim = Color.Black,
    
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnSurface,
    inversePrimary = PurplePrimaryLight,
    
    surfaceTint = PurplePrimary
)

private val DarkColorScheme = darkColorScheme(
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
    tertiaryContainer = PurpleAccent,
    onTertiaryContainer = PurpleAccentLight,
    
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    background = DarkBackground,
    onBackground = DarkOnSurface,
    
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    
    scrim = Color.Black,
    
    inverseSurface = LightSurface,
    inverseOnSurface = LightOnSurface,
    inversePrimary = PurplePrimary,
    
    surfaceTint = PurplePrimaryLight
)

@Composable
fun InventoriaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
