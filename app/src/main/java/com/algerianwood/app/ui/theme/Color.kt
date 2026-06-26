package com.algerianwood.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Legacy defaults (night-adjacent) — kept for overlay draw code not migrated
val ForestGreen = Color(0xFFC7853E)
val ForestGreenLight = Color(0xFFD79A52)
val ForestGreenDark = Color(0xFF8F5B28)
val MintGreen = Color(0xFFE8BC84)
val PaleGreen = Color(0xFFF7E5CE)
val WoodBrown = Color(0xFF7A4B1D)
val WoodBrownLight = Color(0xFFA56A32)
val WoodBrownDark = Color(0xFF4B2C10)
val SandBrown = Color(0xFFE2B67E)
val DarkForest = Color(0xFF0F0C09)
val DarkForestSurface = Color(0xFF1A140F)
val DarkForestCard = Color(0xFF241B13)
val GoldenAmber = Color(0xFFE0A24F)
val SoftWhite = Color(0xFFF8F2E9)
val ErrorRed = Color(0xFFEF5350)
val TextPrimary = Color(0xFFF3D9B7)
val TextSecondary = Color(0xFFD0A06A)
val TextOnPrimary = Color(0xFF1A120A)

data class AppPalette(
    val background: Color,
    val surface: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnPrimary: Color,
    val forestGreen: Color,
    val forestGreenLight: Color,
    val forestGreenDark: Color,
    val mintGreen: Color,
    val paleGreen: Color,
    val woodBrown: Color,
    val woodBrownLight: Color,
    val woodBrownDark: Color,
    val sandBrown: Color,
    val goldenAmber: Color,
    val softWhite: Color,
    val errorRed: Color,
    val isLight: Boolean
)

private val NightPalette = AppPalette(
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
    card = Color(0xFF1E1E1E),
    textPrimary = Color(0xFFE8E8E8),
    textSecondary = Color(0xFF9E9E9E),
    textOnPrimary = Color(0xFF0A0A0A),
    forestGreen = Color(0xFFB87838),
    forestGreenLight = Color(0xFFCC8F48),
    forestGreenDark = Color(0xFF7A4E24),
    mintGreen = Color(0xFFD4A870),
    paleGreen = Color(0xFFE8D4B8),
    woodBrown = Color(0xFF6B4220),
    woodBrownLight = Color(0xFF8F5C30),
    woodBrownDark = Color(0xFF3D2510),
    sandBrown = Color(0xFFC49A68),
    goldenAmber = Color(0xFFD49540),
    softWhite = Color(0xFFF0F0F0),
    errorRed = Color(0xFFEF5350),
    isLight = false
)

private val DayPalette = AppPalette(
    background = Color(0xFFF5EDE3),
    surface = Color(0xFFFFFBF6),
    card = Color(0xFFFFF4E8),
    textPrimary = Color(0xFF3D2814),
    textSecondary = Color(0xFF8B5E34),
    textOnPrimary = Color(0xFFFFFBF6),
    forestGreen = Color(0xFFD99248),
    forestGreenLight = Color(0xFFE8A858),
    forestGreenDark = Color(0xFFA66A30),
    mintGreen = Color(0xFFF0C890),
    paleGreen = Color(0xFFFFF0DC),
    woodBrown = Color(0xFF8F5528),
    woodBrownLight = Color(0xFFB87238),
    woodBrownDark = Color(0xFF5C3818),
    sandBrown = Color(0xFFEEC898),
    goldenAmber = Color(0xFFF0B058),
    softWhite = Color(0xFFFFFBF6),
    errorRed = Color(0xFFD32F2F),
    isLight = true
)

fun paletteFor(mode: AppThemeMode): AppPalette = when (mode) {
    AppThemeMode.NIGHT -> NightPalette
    AppThemeMode.DAY -> DayPalette
}

val LocalAppPalette = staticCompositionLocalOf { NightPalette }
