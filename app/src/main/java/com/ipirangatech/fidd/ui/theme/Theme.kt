package com.ipirangatech.fidd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = NoisOrange,
        onPrimary = Color(0xFF000000),
        primaryContainer = NoisOrangeDark,
        onPrimaryContainer = NoisOrangeLight,
        secondary = NoisOrangeLight,
        tertiary = NoisGreen,
        onTertiary = Color(0xFF000000),
        background = NoisBackgroundDark,
        onBackground = NoisOnDark,
        surface = NoisSurfaceDark,
        onSurface = NoisOnDark,
        surfaceVariant = NoisSurfaceVariantDark,
        onSurfaceVariant = NoisOnSurfaceVariantDark,
        error = NoisRed,
        onError = Color(0xFF000000),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = NoisOrangeDark,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = NoisOrangeLight,
        onPrimaryContainer = NoisOrangeDark,
        secondary = NoisOrangeDark,
        tertiary = NoisGreen,
        onTertiary = Color(0xFFFFFFFF),
        background = NoisBackgroundLight,
        onBackground = NoisOnLight,
        surface = NoisSurfaceLight,
        onSurface = NoisOnLight,
        surfaceVariant = NoisSurfaceVariantLight,
        onSurfaceVariant = NoisOnLight,
        error = NoisRed,
        onError = Color(0xFFFFFFFF),
    )

@Composable
fun FiddTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
