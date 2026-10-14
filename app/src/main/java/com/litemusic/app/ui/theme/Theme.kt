package com.litemusic.app.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val OledDefaultDarkColorScheme = darkColorScheme(
    primary = MintPrimary,
    onPrimary = MintOnPrimary,
    primaryContainer = MintPrimaryContainer,
    onPrimaryContainer = MintOnPrimaryContainer,

    secondary = MintSecondary,
    secondaryContainer = MintSecondaryContainer,
    onSecondaryContainer = MintOnSecondaryContainer,

    tertiary = MintTertiary,
    tertiaryContainer = MintTertiaryContainer,

    background = OledBlack,
    surface = OledBlack,
    surfaceContainerLowest = OledSurfaceContainerLowest,
    surfaceContainerLow = OledSurfaceContainerLow,
    surfaceContainer = OledSurfaceContainer,
    surfaceContainerHigh = OledSurfaceContainerHigh,
    surfaceContainerHighest = OledSurfaceContainerHighest,

    outline = M3Outline,
    outlineVariant = M3OutlineVariant,
    onSurface = M3OnSurface,
    onSurfaceVariant = M3OnSurfaceVariant
)

@Composable
fun LiteMusicTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Android 12+ (API 31+) Dynamic Color (Monet Engine)
    val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        OledDefaultDarkColorScheme
    }

    // Force Pure OLED Black for surfaces while retaining dynamic primary & tonal accents
    val oledDynamicScheme = baseScheme.copy(
        background = OledBlack,
        surface = OledBlack,
        surfaceContainerLowest = OledSurfaceContainerLowest,
        surfaceContainerLow = OledSurfaceContainerLow,
        surfaceContainer = OledSurfaceContainer,
        surfaceContainerHigh = OledSurfaceContainerHigh,
        surfaceContainerHighest = OledSurfaceContainerHighest
    )

    MaterialTheme(
        colorScheme = oledDynamicScheme,
        content = content
    )
}
