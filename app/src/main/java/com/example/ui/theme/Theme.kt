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

private val GoogleDarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = SyncedDark,
    onSecondary = Color(0xFF0D3B1E),
    secondaryContainer = Color(0xFF1E4D2B),
    onSecondaryContainer = Color(0xFFCEEAD6),
    tertiary = PendingColor,
    onTertiary = Color(0xFF3B2F00),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    error = AlertDark,
    onError = Color(0xFF601410)
)

private val GoogleLightScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = GoogleGreen,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6F4EA),
    onSecondaryContainer = Color(0xFF0D652D),
    tertiary = PendingColor,
    onTertiary = Color(0xFF3B2F00),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = AlertLight,
    onError = Color(0xFFFFFFFF)
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
    val colorScheme = if (darkTheme) GoogleDarkScheme else GoogleLightScheme
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
