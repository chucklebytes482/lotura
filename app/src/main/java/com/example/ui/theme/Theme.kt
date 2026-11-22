package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class BrandVisualColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val cardSurface: Color,
    val border: Color
)

@Immutable
data class LoturaBrandPalette(
    val drive: BrandVisualColors,
    val github: BrandVisualColors
)

private val DarkBrandPalette = LoturaBrandPalette(
    drive = BrandVisualColors(
        accent = DriveAccentDark,
        onAccent = DriveOnAccentDark,
        container = DriveContainerDark,
        onContainer = DriveOnContainerDark,
        cardSurface = DriveCardSurfaceDark,
        border = DriveBorderDark
    ),
    github = BrandVisualColors(
        accent = GithubAccentDark,
        onAccent = GithubOnAccentDark,
        container = GithubContainerDark,
        onContainer = GithubOnContainerDark,
        cardSurface = GithubCardSurfaceDark,
        border = GithubBorderDark
    )
)

private val LightBrandPalette = LoturaBrandPalette(
    drive = BrandVisualColors(
        accent = DriveAccentLight,
        onAccent = DriveOnAccentLight,
        container = DriveContainerLight,
        onContainer = DriveOnContainerLight,
        cardSurface = DriveCardSurfaceLight,
        border = DriveBorderLight
    ),
    github = BrandVisualColors(
        accent = GithubAccentLight,
        onAccent = GithubOnAccentLight,
        container = GithubContainerLight,
        onContainer = GithubOnContainerLight,
        cardSurface = GithubCardSurfaceLight,
        border = GithubBorderLight
    )
)

val LocalBrandPalette = staticCompositionLocalOf { LightBrandPalette }

@Composable
fun providerBrandColors(isGithub: Boolean): BrandVisualColors {
    val palette = LocalBrandPalette.current
    return if (isGithub) palette.github else palette.drive
}

private val PrecisionSlateDarkScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = CyanOnAccent,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanOnContainer,
    secondary = MintSynced,
    onSecondary = SlateVoid,
    secondaryContainer = SlateCardElevated,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = AmberPending,
    background = SlateVoid,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateOutline,
    error = CoralAlert,
    onError = CoralOnAlert
)

private val PrecisionSlateLightScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = LightSurface,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = MintSyncedLight,
    onSecondary = LightSurface,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = AmberPending,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    error = CoralAlertLight,
    onError = LightSurface
)

val SyncBebasShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SyncBebasTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) PrecisionSlateDarkScheme else PrecisionSlateLightScheme
    val brandPalette = if (darkTheme) DarkBrandPalette else LightBrandPalette

    CompositionLocalProvider(LocalBrandPalette provides brandPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SyncBebasTypography,
            shapes = SyncBebasShapes,
            content = content
        )
    }
}
