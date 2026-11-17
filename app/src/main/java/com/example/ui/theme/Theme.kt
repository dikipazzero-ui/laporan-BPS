package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = OnTealPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = OnTealPrimaryContainer,
    secondary = AmberSecondary,
    onSecondary = OnAmberSecondary,
    secondaryContainer = AmberSecondaryContainer,
    onSecondaryContainer = OnAmberSecondaryContainer,
    tertiary = SageTertiary,
    onTertiary = OnSageTertiary,
    tertiaryContainer = SageTertiaryContainer,
    onTertiaryContainer = OnSageTertiaryContainer,
    background = AlabasterBackground,
    onBackground = OnAlabasterText,
    surface = AlabasterSurface,
    onSurface = OnAlabasterText,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = OnAlabasterMuted,
    outline = OutlineSubtle,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorRedContainer,
    onErrorContainer = OnErrorRedContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = OnDarkTealPrimary,
    primaryContainer = DarkTealPrimaryContainer,
    onPrimaryContainer = OnDarkTealPrimaryContainer,
    secondary = DarkAmberSecondary,
    onSecondary = OnDarkAmberSecondary,
    secondaryContainer = DarkAmberSecondaryContainer,
    onSecondaryContainer = OnDarkAmberSecondaryContainer,
    tertiary = DarkSageTertiary,
    onTertiary = OnDarkSageTertiary,
    tertiaryContainer = DarkSageTertiaryContainer,
    onTertiaryContainer = OnDarkSageTertiaryContainer,
    background = DarkSlateBackground,
    onBackground = OnDarkText,
    surface = DarkSlateSurface,
    onSurface = OnDarkText,
    surfaceVariant = DarkSlateSurfaceVariant,
    onSurfaceVariant = OnDarkMuted,
    outline = DarkOutline
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LaporanBpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
