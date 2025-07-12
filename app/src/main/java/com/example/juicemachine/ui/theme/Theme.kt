package com.example.juicemachine.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = FreshOrange,
    onPrimary = Color.White,
    primaryContainer = LightFruit,
    onPrimaryContainer = DeepFruit,
    secondary = FreshGreen,
    onSecondary = Color.White,
    secondaryContainer = FreshCream,
    onSecondaryContainer = DeepFruit,
    tertiary = FreshYellow,
    onTertiary = Color.White,
    background = WarmBeige,
    onBackground = DarkGrey,
    surface = Color.White,
    onSurface = DarkGrey,
    surfaceVariant = LightGrey,
    onSurfaceVariant = DarkGrey,
    error = StatusRed,
    onError = Color.White
)

@Composable
fun JuiceMachineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme // App is always in light mode as per design
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
} 