package com.litemusic.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Pure OLED Black & Surface Container tokens
val OledBlack = Color(0xFF000000)
val OledSurfaceContainerLowest = Color(0xFF050505)
val OledSurfaceContainerLow = Color(0xFF0B0B0B)
val OledSurfaceContainer = Color(0xFF121212)
val OledSurfaceContainerHigh = Color(0xFF1A1A1A)
val OledSurfaceContainerHighest = Color(0xFF242424)

// Liquid Glass Design Tokens (Frosted Glassmorphism & Cyber Aero)
val GlassSurfaceDark = Color(0x66111827)
val GlassSurfaceLight = Color(0xB8FFFFFF)
val GlassBorderDark = Color(0x4093C5FD)
val GlassBorderLight = Color(0x80FFFFFF)
val GlassBottomNavDark = Color(0xE6080C14)
val GlassBottomNavLight = Color(0xE6F8FAFC)
val GlassCardBackground = Color(0x521E293B)
val GlassGlow = Color(0x2E38BDF8)

// Outlines & Neutrals (Dark)
val M3Outline = Color(0xFF8B938A)
val M3OutlineVariant = Color(0xFF414942)
val M3OnSurface = Color(0xFFE1E3DE)
val M3OnSurfaceVariant = Color(0xFFC1C9BF)

// Outlines & Neutrals (Light)
val M3LightOutline = Color(0xFF717971)
val M3LightOutlineVariant = Color(0xFFC1C9BF)
val M3LightOnSurface = Color(0xFF191C19)
val M3LightOnSurfaceVariant = Color(0xFF414942)
val M3LightBackground = Color(0xFFF7FBF4)
val M3LightSurface = Color(0xFFF7FBF4)
val M3LightSurfaceContainer = Color(0xFFECEFE7)
val M3LightSurfaceContainerHigh = Color(0xFFE6EAE2)
val M3LightSurfaceContainerHighest = Color(0xFFE0E5DC)

// Source Badges
val BadgeYouTube = Color(0xFFFF6B6B)
val BadgeSoundCloud = Color(0xFFFF8533)
val BadgeSpotify = Color(0xFF1ED760)

// 1. Botanical Mint Palette (Android 14 Default)
val MintPrimaryDark = Color(0xFF52E196)
val MintPrimaryLight = Color(0xFF006D3E)
val MintDarkScheme = darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = Color(0xFF00381E),
    primaryContainer = Color(0xFF00522E),
    onPrimaryContainer = Color(0xFF77FEAF),
    secondary = Color(0xFFB6CCB9),
    onSecondary = Color(0xFF223527),
    secondaryContainer = Color(0xFF374B3D),
    onSecondaryContainer = Color(0xFFD2E8D4),
    tertiary = Color(0xFFA4CDDC),
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
val MintLightScheme = lightColorScheme(
    primary = MintPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF77FEAF),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF4E6354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E8D4),
    onSecondaryContainer = Color(0xFF0B1F13),
    tertiary = Color(0xFF3C6473),
    background = M3LightBackground,
    surface = M3LightSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5ED),
    surfaceContainer = M3LightSurfaceContainer,
    surfaceContainerHigh = M3LightSurfaceContainerHigh,
    surfaceContainerHighest = M3LightSurfaceContainerHighest,
    outline = M3LightOutline,
    outlineVariant = M3LightOutlineVariant,
    onSurface = M3LightOnSurface,
    onSurfaceVariant = M3LightOnSurfaceVariant
)

// 2. Sapphire Blue Palette
val SapphirePrimaryDark = Color(0xFF9ECAFF)
val SapphirePrimaryLight = Color(0xFF0061A4)
val SapphireDarkScheme = darkColorScheme(
    primary = SapphirePrimaryDark,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3C4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    background = OledBlack,
    surface = OledBlack,
    surfaceContainerLowest = OledSurfaceContainerLowest,
    surfaceContainerLow = OledSurfaceContainerLow,
    surfaceContainer = OledSurfaceContainer,
    surfaceContainerHigh = OledSurfaceContainerHigh,
    surfaceContainerHighest = OledSurfaceContainerHighest,
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    onSurface = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFFC2C7CF)
)
val SapphireLightScheme = lightColorScheme(
    primary = SapphirePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    background = Color(0xFFFDFBFF),
    surface = Color(0xFFFDFBFF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC2C7CF),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF42474E)
)

// 3. Amethyst Purple Palette
val AmethystPrimaryDark = Color(0xFFD0BCFF)
val AmethystPrimaryLight = Color(0xFF6750A4)
val AmethystDarkScheme = darkColorScheme(
    primary = AmethystPrimaryDark,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    background = OledBlack,
    surface = OledBlack,
    surfaceContainerLowest = OledSurfaceContainerLowest,
    surfaceContainerLow = OledSurfaceContainerLow,
    surfaceContainer = OledSurfaceContainer,
    surfaceContainerHigh = OledSurfaceContainerHigh,
    surfaceContainerHighest = OledSurfaceContainerHighest,
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCAC4D0)
)
val AmethystLightScheme = lightColorScheme(
    primary = AmethystPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F2F7),
    surfaceContainer = Color(0xFFF1ECF1),
    surfaceContainerHigh = Color(0xFFEBE6EC),
    surfaceContainerHighest = Color(0xFFE5E0E6),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F)
)

// 4. Amber Sunset Palette
val AmberPrimaryDark = Color(0xFFFFB77C)
val AmberPrimaryLight = Color(0xFF8F4E00)
val AmberDarkScheme = darkColorScheme(
    primary = AmberPrimaryDark,
    onPrimary = Color(0xFF4D2700),
    primaryContainer = Color(0xFF6D3A00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE5BFA8),
    onSecondary = Color(0xFF422B1B),
    secondaryContainer = Color(0xFF5B4130),
    onSecondaryContainer = Color(0xFFFFDCC5),
    tertiary = Color(0xFFCECA97),
    background = OledBlack,
    surface = OledBlack,
    surfaceContainerLowest = OledSurfaceContainerLowest,
    surfaceContainerLow = OledSurfaceContainerLow,
    surfaceContainer = OledSurfaceContainer,
    surfaceContainerHigh = OledSurfaceContainerHigh,
    surfaceContainerHighest = OledSurfaceContainerHighest,
    outline = Color(0xFF9E8E81),
    outlineVariant = Color(0xFF51453A),
    onSurface = Color(0xFFECE0DA),
    onSurfaceVariant = Color(0xFFD6C3B6)
)
val AmberLightScheme = lightColorScheme(
    primary = AmberPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF755946),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC5),
    onSecondaryContainer = Color(0xFF2B1709),
    tertiary = Color(0xFF626033),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9EFE9),
    surfaceContainer = Color(0xFFF3E9E3),
    surfaceContainerHigh = Color(0xFFEDE4DE),
    surfaceContainerHighest = Color(0xFFE7DED8),
    outline = Color(0xFF84746A),
    outlineVariant = Color(0xFFD6C3B6),
    onSurface = Color(0xFF201A16),
    onSurfaceVariant = Color(0xFF51453A)
)

// 5. Rose Ruby Palette
val RosePrimaryDark = Color(0xFFFFB2B8)
val RosePrimaryLight = Color(0xFFB90041)
val RoseDarkScheme = darkColorScheme(
    primary = RosePrimaryDark,
    onPrimary = Color(0xFF660020),
    primaryContainer = Color(0xFF8F0030),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFFE5BDC0),
    onSecondary = Color(0xFF43292C),
    secondaryContainer = Color(0xFF5C3F42),
    onSecondaryContainer = Color(0xFFFFD9DC),
    tertiary = Color(0xFFEAB999),
    background = OledBlack,
    surface = OledBlack,
    surfaceContainerLowest = OledSurfaceContainerLowest,
    surfaceContainerLow = OledSurfaceContainerLow,
    surfaceContainer = OledSurfaceContainer,
    surfaceContainerHigh = OledSurfaceContainerHigh,
    surfaceContainerHighest = OledSurfaceContainerHighest,
    outline = Color(0xFF9E8C8E),
    outlineVariant = Color(0xFF524345),
    onSurface = Color(0xFFECE0E0),
    onSurfaceVariant = Color(0xFFD6C2C3)
)
val RoseLightScheme = lightColorScheme(
    primary = RosePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DC),
    onPrimaryContainer = Color(0xFF400012),
    secondary = Color(0xFF765659),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9DC),
    onSecondaryContainer = Color(0xFF2C1518),
    tertiary = Color(0xFF78593F),
    background = Color(0xFFFFF8F7),
    surface = Color(0xFFFFF8F7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9EEEF),
    surfaceContainer = Color(0xFFF3E8E9),
    surfaceContainerHigh = Color(0xFFEDE3E4),
    surfaceContainerHighest = Color(0xFFE7DDDE),
    outline = Color(0xFF847375),
    outlineVariant = Color(0xFFD6C2C3),
    onSurface = Color(0xFF211A1B),
    onSurfaceVariant = Color(0xFF524345)
)

// 6. Ocean Cyan Palette
val OceanPrimaryDark = Color(0xFF4FD8EB)
val OceanPrimaryLight = Color(0xFF006877)
val OceanDarkScheme = darkColorScheme(
    primary = OceanPrimaryDark,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5A),
    onPrimaryContainer = Color(0xFF98F0FF),
    secondary = Color(0xFFB1CBD0),
    onSecondary = Color(0xFF1C3438),
    secondaryContainer = Color(0xFF334B4F),
    onSecondaryContainer = Color(0xFFCCE8ED),
    tertiary = Color(0xFFBAC6EA),
    background = OledBlack,
    surface = OledBlack,
    surfaceContainerLowest = OledSurfaceContainerLowest,
    surfaceContainerLow = OledSurfaceContainerLow,
    surfaceContainer = OledSurfaceContainer,
    surfaceContainerHigh = OledSurfaceContainerHigh,
    surfaceContainerHighest = OledSurfaceContainerHighest,
    outline = Color(0xFF899294),
    outlineVariant = Color(0xFF3F484A),
    onSurface = Color(0xFFE0E3E3),
    onSurfaceVariant = Color(0xFFBFC8CA)
)
val OceanLightScheme = lightColorScheme(
    primary = OceanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF98F0FF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = Color(0xFF4A6267),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8ED),
    onSecondaryContainer = Color(0xFF051F23),
    tertiary = Color(0xFF535E7E),
    background = Color(0xFFF8FDFF),
    surface = Color(0xFFF8FDFF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5F6),
    surfaceContainer = Color(0xFFEAEFF0),
    surfaceContainerHigh = Color(0xFFE5EAEB),
    surfaceContainerHighest = Color(0xFFDFE4E5),
    outline = Color(0xFF6F797B),
    outlineVariant = Color(0xFFBFC8CA),
    onSurface = Color(0xFF191C1D),
    onSurfaceVariant = Color(0xFF3F484A)
)
