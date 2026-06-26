package com.algerianwood.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun materialSchemeFor(palette: AppPalette) = if (palette.isLight) {
    lightColorScheme(
        primary = palette.forestGreen,
        onPrimary = palette.textOnPrimary,
        primaryContainer = palette.forestGreenDark,
        onPrimaryContainer = palette.paleGreen,
        secondary = palette.woodBrown,
        onSecondary = palette.textOnPrimary,
        secondaryContainer = palette.woodBrownDark,
        onSecondaryContainer = palette.sandBrown,
        tertiary = palette.goldenAmber,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.card,
        onSurfaceVariant = palette.textSecondary,
        error = palette.errorRed,
        onError = palette.textOnPrimary
    )
} else {
    darkColorScheme(
        primary = palette.forestGreen,
        onPrimary = palette.textOnPrimary,
        primaryContainer = palette.forestGreenDark,
        onPrimaryContainer = palette.paleGreen,
        secondary = palette.woodBrown,
        onSecondary = palette.textOnPrimary,
        secondaryContainer = palette.woodBrownDark,
        onSecondaryContainer = palette.sandBrown,
        tertiary = palette.goldenAmber,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.card,
        onSurfaceVariant = palette.textSecondary,
        error = palette.errorRed,
        onError = palette.textOnPrimary
    )
}

@Composable
fun ARWoodTechTheme(
    mode: AppThemeMode = AppThemeMode.NIGHT,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(mode)
    val colorScheme = materialSchemeFor(palette)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = palette.isLight
        }
    }

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
