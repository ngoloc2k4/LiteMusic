package com.litemusic.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode(val displayName: String) {
    DARK_OLED("Dark OLED (#000)"),
    DARK_STANDARD("Standard Dark M3"),
    LIGHT("Light Mode"),
    SYSTEM("System Default")
}

enum class AppColorPalette(val displayName: String, val seedColor: Color) {
    SYSTEM_MONET("System Monet", Color(0xFF52E196)),
    MINT("Botanical Mint", MintPrimaryDark),
    SAPPHIRE("Sapphire Blue", SapphirePrimaryDark),
    AMETHYST("Amethyst Purple", AmethystPrimaryDark),
    AMBER("Amber Sunset", AmberPrimaryDark),
    ROSE("Rose Ruby", RosePrimaryDark),
    OCEAN("Ocean Cyan", OceanPrimaryDark)
}

enum class AppThemeStyle(val displayName: String) {
    MATERIAL_3("Standard M3"),
    LIQUID_GLASS("Liquid Glass")
}

val LocalThemeStyle = staticCompositionLocalOf { AppThemeStyle.MATERIAL_3 }
val LocalIsOled = staticCompositionLocalOf { true }

@Composable
fun LiteMusicTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_OLED,
    colorPalette: AppColorPalette = AppColorPalette.SYSTEM_MONET,
    themeStyle: AppThemeStyle = AppThemeStyle.MATERIAL_3,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        AppThemeMode.DARK_OLED -> true
        AppThemeMode.DARK_STANDARD -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemInDark
    }

    val isOled = themeMode == AppThemeMode.DARK_OLED && isDark

    // Android 12+ (API 31+) Monet wallpaper dynamic scheme or static curated palettes
    val baseScheme: ColorScheme = if (colorPalette == AppColorPalette.SYSTEM_MONET && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (colorPalette) {
            AppColorPalette.MINT, AppColorPalette.SYSTEM_MONET -> if (isDark) MintDarkScheme else MintLightScheme
            AppColorPalette.SAPPHIRE -> if (isDark) SapphireDarkScheme else SapphireLightScheme
            AppColorPalette.AMETHYST -> if (isDark) AmethystDarkScheme else AmethystLightScheme
            AppColorPalette.AMBER -> if (isDark) AmberDarkScheme else AmberLightScheme
            AppColorPalette.ROSE -> if (isDark) RoseDarkScheme else RoseLightScheme
            AppColorPalette.OCEAN -> if (isDark) OceanDarkScheme else OceanLightScheme
        }
    }

    val finalColorScheme = when {
        isOled -> baseScheme.copy(
            background = OledBlack,
            surface = OledBlack,
            surfaceContainerLowest = OledSurfaceContainerLowest,
            surfaceContainerLow = OledSurfaceContainerLow,
            surfaceContainer = OledSurfaceContainer,
            surfaceContainerHigh = OledSurfaceContainerHigh,
            surfaceContainerHighest = OledSurfaceContainerHighest
        )
        themeStyle == AppThemeStyle.LIQUID_GLASS -> {
            if (isDark) {
                baseScheme.copy(
                    background = Color(0xFF070B12),
                    surface = GlassSurfaceDark,
                    surfaceContainerLowest = Color(0x1F131C2D),
                    surfaceContainerLow = Color(0x33172236),
                    surfaceContainer = GlassSurfaceDark,
                    surfaceContainerHigh = Color(0x4D202E46),
                    surfaceContainerHighest = Color(0x66293B58)
                )
            } else {
                baseScheme.copy(
                    background = Color(0xFFEDF2F7),
                    surface = GlassSurfaceLight,
                    surfaceContainerLowest = Color(0x80FFFFFF),
                    surfaceContainerLow = Color(0x99FFFFFF),
                    surfaceContainer = GlassSurfaceLight,
                    surfaceContainerHigh = Color(0xB3FFFFFF),
                    surfaceContainerHighest = Color(0xCCFFFFFF)
                )
            }
        }
        else -> baseScheme
    }

    CompositionLocalProvider(
        LocalThemeStyle provides themeStyle,
        LocalIsOled provides isOled
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            content = content
        )
    }
}
