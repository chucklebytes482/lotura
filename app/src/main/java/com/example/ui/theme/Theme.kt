package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

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
    val colorScheme = if (isSystemInDarkTheme()) PrecisionSlateDarkScheme else PrecisionSlateLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SyncBebasTypography,
        shapes = SyncBebasShapes,
        content = content
    )
}
