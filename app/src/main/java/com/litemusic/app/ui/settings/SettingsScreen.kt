package com.litemusic.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.litemusic.app.R
import com.litemusic.app.core.i18n.LocaleHelper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litemusic.app.core.audio.AudioCacheManager
import com.litemusic.app.ui.theme.*

@Composable
fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    currentColorPalette: AppColorPalette,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    currentThemeStyle: AppThemeStyle,
    onThemeStyleChange: (AppThemeStyle) -> Unit,
    isDspEnabled: Boolean,
    onDspToggle: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    currentSkipDuration: Int = 10,
    onSkipDurationChange: (Int) -> Unit = {},
    isDebugMode: Boolean = false,
    onDebugModeToggle: (Boolean) -> Unit = {},
    debugServerUrl: String = "http://localhost:8888",
    onCopyLogcat: () -> Unit = {},
    onClearLogcat: () -> Unit = {},
    isInfinitePlayback: Boolean = true,
    onInfinitePlaybackToggle: (Boolean) -> Unit = {},
    infiniteBatchSize: Int = 25,
    onInfiniteBatchSizeChange: (Int) -> Unit = {},
    isAutoQueueRelated: Boolean = true,
    onAutoQueueRelatedToggle: (Boolean) -> Unit = {},
    onOpenBlocklist: () -> Unit = {}
) {
    val context = LocalContext.current
    val isGlass = currentThemeStyle == AppThemeStyle.LIQUID_GLASS

    // Dialog States
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCacheDialog by remember { mutableStateOf(false) }
    var showSkipDialog by remember { mutableStateOf(false) }
    var showInfiniteBatchDialog by remember { mutableStateOf(false) }

    var currentLanguage by remember { mutableStateOf(ThemePreferences.getAppLanguage(context)) }

    // Cache metrics state
    var maxCacheMb by remember { mutableIntStateOf(AudioCacheManager.getMaxCacheSizeMb(context)) }
    var usedCacheMb by remember { mutableDoubleStateOf(AudioCacheManager.getUsedCacheSizeMb(context)) }

    // Dialogs
    if (showThemeDialog) {
        ThemeCustomizationDialog(
            currentThemeStyle = currentThemeStyle,
            onThemeStyleChange = onThemeStyleChange,
            currentThemeMode = currentThemeMode,
            onThemeModeChange = onThemeModeChange,
            currentColorPalette = currentColorPalette,
            onColorPaletteChange = onColorPaletteChange,
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onSelectLanguage = { newLang ->
                currentLanguage = newLang
                ThemePreferences.setAppLanguage(context, newLang)
                LocaleHelper.applyLocale(context, newLang)
                (context as? Activity)?.recreate()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showCacheDialog) {
        CacheManagementDialog(
            usedCacheMb = usedCacheMb,
            maxCacheMb = maxCacheMb,
            onMaxCacheChange = { newMax ->
                maxCacheMb = newMax
                AudioCacheManager.setMaxCacheSizeMb(context, newMax)
            },
            onClear = {
                val freedBytes = AudioCacheManager.clearAllCache(context)
                val freedMb = String.format(java.util.Locale.US, "%.1f", freedBytes.toDouble() / (1024.0 * 1024.0))
                usedCacheMb = 0.0
                Toast.makeText(context, context.getString(R.string.settings_cache_freed, freedMb), Toast.LENGTH_SHORT).show()
                onClearCache()
            },
            onDismiss = { showCacheDialog = false }
        )
    }

    if (showSkipDialog) {
        SkipDurationDialog(
            currentDuration = currentSkipDuration,
            onSelectDuration = onSkipDurationChange,
            onDismiss = { showSkipDialog = false }
        )
    }

    if (showInfiniteBatchDialog) {
        InfiniteBatchDialog(
            currentBatchSize = infiniteBatchSize,
            onSelectBatchSize = onInfiniteBatchSizeChange,
            onDismiss = { showInfiniteBatchDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 110.dp, top = 8.dp)
    ) {
        // SaaS Hero Header
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.settings_title),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_subtitle),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "v1.6 Beta",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Section 1: Giao diện & Chủ đề (Appearance)
        item {
            SettingsGroupHeader(title = stringResource(R.string.settings_group_appearance))
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = "${currentThemeStyle.displayName} • ${currentThemeMode.displayName}",
                    onClick = { showThemeDialog = true },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(currentColorPalette.seedColor)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            )
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                        }
                    }
                )
                SettingsInsetDivider()
                val currentLanguageName = when (currentLanguage) {
                    "vi" -> "Tiếng Việt"
                    "en" -> "English"
                    "ja" -> "日本語"
                    "ko" -> "한국어"
                    "zh-rCN" -> "简体中文"
                    "zh-rTW" -> "繁體中文"
                    "fr" -> "Français"
                    "de" -> "Deutsch"
                    "es" -> "Español"
                    "it" -> "Italiano"
                    "pt-rBR" -> "Português"
                    "ru" -> "Русский"
                    "ar" -> "العربية"
                    "th" -> "ไทย"
                    "in" -> "Bahasa Indonesia"
                    "hi" -> "हिन्दी"
                    "tr" -> "Türkçe"
                    "pl" -> "Polski"
                    "nl" -> "Nederlands"
                    "uk" -> "Українська"
                    "sv" -> "Svenska"
                    else -> stringResource(R.string.language_system)
                }
                SettingsRow(
                    icon = Icons.Default.Language,
                    iconBgColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = stringResource(R.string.language),
                    subtitle = currentLanguageName,
                    onClick = { showLanguageDialog = true },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }

        // Section 2: Âm thanh & Phát nhạc (Audio & Playback Engine)
        item {
            SettingsGroupHeader(title = stringResource(R.string.settings_group_audio))
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                // DSP Loudness
                SettingsRow(
                    icon = Icons.Default.GraphicEq,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = stringResource(R.string.settings_dsp_title),
                    subtitle = stringResource(R.string.settings_dsp_subtitle),
                    trailingContent = {
                        Switch(
                            checked = isDspEnabled,
                            onCheckedChange = onDspToggle
                        )
                    }
                )

                SettingsInsetDivider()

                // Skip Duration
                SettingsRow(
                    icon = Icons.Default.FastForward,
                    iconBgColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = stringResource(R.string.settings_skip_duration_title),
                    subtitle = stringResource(R.string.settings_skip_duration_subtitle),
                    onClick = { showSkipDialog = true },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "${currentSkipDuration}s",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                        }
                    }
                )

                SettingsInsetDivider()

                // Infinite Playback
                SettingsRow(
                    icon = Icons.Default.AllInclusive,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.settings_infinite_title),
                    subtitle = if (isInfinitePlayback) stringResource(R.string.settings_infinite_subtitle, infiniteBatchSize) else stringResource(R.string.settings_infinite_off),
                    onClick = if (isInfinitePlayback) { { showInfiniteBatchDialog = true } } else null,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isInfinitePlayback) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.clickable { showInfiniteBatchDialog = true }
                                ) {
                                    Text(
                                        text = "+$infiniteBatchSize",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Switch(
                                checked = isInfinitePlayback,
                                onCheckedChange = onInfinitePlaybackToggle
                            )
                        }
                    }
                )

                SettingsInsetDivider()

                // Auto-queue related tracks
                SettingsRow(
                    icon = Icons.Default.QueueMusic,
                    iconBgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    title = stringResource(R.string.settings_auto_related_title),
                    subtitle = stringResource(R.string.settings_auto_related_subtitle),
                    trailingContent = {
                        Switch(
                            checked = isAutoQueueRelated,
                            onCheckedChange = onAutoQueueRelatedToggle
                        )
                    }
                )

                SettingsInsetDivider()

                // Blocklist management
                SettingsRow(
                    icon = Icons.Default.Block,
                    iconBgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.error,
                    title = stringResource(R.string.settings_blocklist_title),
                    subtitle = stringResource(R.string.settings_blocklist_subtitle),
                    onClick = onOpenBlocklist,
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }

        // Section 3: Dữ liệu & Bộ nhớ (Storage & Cache Management)
        item {
            SettingsGroupHeader(title = stringResource(R.string.manage_cache).uppercase())
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                val usedFormatted = String.format(java.util.Locale.US, "%.1f", usedCacheMb)
                val percent = ((usedCacheMb / maxCacheMb.toDouble()) * 100).toInt().coerceIn(0, 100)

                SettingsRow(
                    icon = Icons.Default.Storage,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.settings_cache_dialog_title),
                    subtitle = stringResource(R.string.settings_cache_used_summary, usedFormatted, maxCacheMb, percent),
                    onClick = { showCacheDialog = true },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (percent > 85) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "$percent%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (percent > 85) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                        }
                    }
                )
            }
        }

        // Section 4: AI Music & Search Engine
        item {
            com.litemusic.app.feature.aimusic.ui.AiSettingsSection(isGlass = isGlass)
        }

        // Section 5: Nhà phát triển & Gỡ lỗi (Developer Console & Localhost)
        item {
            SettingsGroupHeader(title = stringResource(R.string.settings_group_dev))
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                SettingsRow(
                    icon = Icons.Default.BugReport,
                    iconBgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = stringResource(R.string.settings_debug_title),
                    subtitle = if (isDebugMode) stringResource(R.string.settings_debug_server_running, debugServerUrl) else stringResource(R.string.settings_debug_subtitle),
                    trailingContent = {
                        Switch(
                            checked = isDebugMode,
                            onCheckedChange = onDebugModeToggle
                        )
                    }
                )

                AnimatedVisibility(
                    visible = isDebugMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsInsetDivider()

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("🟢", fontSize = 10.sp)
                                    Text(stringResource(R.string.debug_dashboard_title), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    text = debugServerUrl,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.settings_debug_server_hint),
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onCopyLogcat,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_copy_logcat), fontSize = 11.5.sp)
                            }
                            OutlinedButton(
                                onClick = onClearLogcat,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(0.7f)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.settings_clear_logcat), fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section 6: Đóng góp & Thông tin (Community & About)
        item {
            SettingsGroupHeader(title = stringResource(R.string.settings_group_community))
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                // Crowdin Translation
                SettingsRow(
                    icon = Icons.Default.Translate,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = stringResource(R.string.settings_crowdin_title),
                    subtitle = stringResource(R.string.settings_crowdin_subtitle),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://crowdin.com/project/litemusic"))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.settings_crowdin_open), Toast.LENGTH_LONG).show()
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                    }
                )

                SettingsInsetDivider()

                // About app
                SettingsRow(
                    icon = Icons.Default.Info,
                    iconBgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.outline,
                    title = stringResource(R.string.settings_about_title),
                    subtitle = "Jetpack Compose M3 • Android Media3 ExoPlayer 1.5.1",
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "FOSS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * Modern SaaS Settings Group Header
 */
@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp)
    )
}

/**
 * Elevated SaaS Container Card
 */
@Composable
private fun SettingsGroupCard(
    isGlass: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isGlass) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isGlass) BorderStroke(1.dp, GlassBorderDark) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * Interactive SaaS Settings Row with Icon Squircle
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit
) {
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        trailingContent()
    }
}

/**
 * Inset Divider for Settings Rows
 */
@Composable
private fun SettingsInsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp, end = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * Unified Theme & Color Customization Dialog (Popup Modal)
 */
@Composable
fun ThemeCustomizationDialog(
    currentThemeStyle: AppThemeStyle,
    onThemeStyleChange: (AppThemeStyle) -> Unit,
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    currentColorPalette: AppColorPalette,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_theme_dialog_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category 1: Phong cách thiết kế
                Text(stringResource(R.string.settings_theme_section_style), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val styles = listOf(
                        AppThemeStyle.MATERIAL_3 to (stringResource(R.string.settings_theme_style_standard) to Icons.Default.Layers),
                        AppThemeStyle.LIQUID_GLASS to (stringResource(R.string.settings_theme_style_liquid) to Icons.Default.BlurOn)
                    )
                    styles.forEach { (style, pair) ->
                        val isSelected = currentThemeStyle == style
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onThemeStyleChange(style) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(pair.second, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                Text(
                                    text = pair.first,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Category 2: Chế độ hiển thị
                Text(stringResource(R.string.settings_theme_section_mode), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                val modes = listOf(
                    AppThemeMode.DARK_OLED to stringResource(R.string.settings_theme_mode_oled),
                    AppThemeMode.DARK_STANDARD to stringResource(R.string.settings_theme_mode_dark),
                    AppThemeMode.LIGHT to stringResource(R.string.settings_theme_mode_light),
                    AppThemeMode.SYSTEM to stringResource(R.string.settings_theme_mode_system)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    modes.forEach { (mode, label) ->
                        val isSelected = currentThemeMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThemeModeChange(mode) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Category 3: Bảng màu Monet & Điểm nhấn
                Text(stringResource(R.string.settings_theme_section_palette), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AppColorPalette.entries.toTypedArray()) { palette ->
                        val isSelected = currentColorPalette == palette
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onColorPaletteChange(palette) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(palette.seedColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = palette.displayName.split(" ").first(),
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Cache Management Pop-up Dialog
 */
@Composable
fun CacheManagementDialog(
    usedCacheMb: Double,
    maxCacheMb: Int,
    onMaxCacheChange: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var localMax by remember { mutableFloatStateOf(maxCacheMb.toFloat()) }
    val usedFormatted = String.format(java.util.Locale.US, "%.1f", usedCacheMb)
    val ratio = (usedCacheMb / localMax.toDouble()).toFloat().coerceIn(0f, 1f)
    val percent = (ratio * 100).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onMaxCacheChange(localMax.toInt())
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.action_save_and_close), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_cache_dialog_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Gauge summary
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.settings_cache_used_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text("$usedFormatted MB / ${localMax.toInt()} MB ($percent%)", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }

                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = if (ratio > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )

                        Text(
                            text = stringResource(R.string.settings_cache_dialog_note),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Clear Cache Action
                OutlinedButton(
                    onClick = onClear,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_cache_clear_all), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Slider for Max Cache (200MB to 1500MB)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.settings_cache_limit_label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${localMax.toInt()} MB", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }

                    Slider(
                        value = localMax,
                        onValueChange = {
                            val rounded = ((it / 50).toInt() * 50).coerceIn(200, 1500)
                            localMax = rounded.toFloat()
                        },
                        valueRange = 200f..1500f,
                        steps = 25
                    )

                    // Quick presets
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(250, 500, 750, 1000, 1500).forEach { mb ->
                            val isSel = localMax.toInt() == mb
                            FilterChip(
                                selected = isSel,
                                onClick = { localMax = mb.toFloat() },
                                label = { Text("${mb}MB", fontSize = 10.5.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Skip Duration Picker Dialog
 */
@Composable
fun SkipDurationDialog(
    currentDuration: Int,
    onSelectDuration: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(5, 10, 15, 20, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold) }
        },
        title = {
            Text(stringResource(R.string.settings_skip_duration_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { sec ->
                    val isSelected = currentDuration == sec
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectDuration(sec)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_seconds_unit, sec),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.5.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * Infinite Playback Batch Size Dialog
 */
@Composable
fun InfiniteBatchDialog(
    currentBatchSize: Int,
    onSelectBatchSize: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(25, 50, 75, 100)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold) }
        },
        title = {
            Text(stringResource(R.string.settings_infinite_batch_label), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_infinite_batch_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                options.forEach { count ->
                    val isSelected = currentBatchSize == count
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectBatchSize(count)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_batch_unit, count),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.5.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun LanguageSelectionDialog(
    currentLanguage: String,
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf(
        Triple("system", stringResource(R.string.language_system), "Hệ thống / System Default"),
        Triple("vi", stringResource(R.string.language_vi), "Tiếng Việt (Việt Nam)"),
        Triple("en", stringResource(R.string.language_en), "English (Global)"),
        Triple("ja", stringResource(R.string.language_ja), "日本語 (日本)"),
        Triple("ko", stringResource(R.string.language_ko), "한국어 (대한민국)"),
        Triple("zh-rCN", stringResource(R.string.language_zh_cn), "简体中文 (中国)"),
        Triple("zh-rTW", stringResource(R.string.language_zh_tw), "繁體中文 (台灣)"),
        Triple("fr", stringResource(R.string.language_fr), "Français (France)"),
        Triple("de", stringResource(R.string.language_de), "Deutsch (Deutschland)"),
        Triple("es", stringResource(R.string.language_es), "Español (España)"),
        Triple("it", stringResource(R.string.language_it), "Italiano (Italia)"),
        Triple("pt-rBR", stringResource(R.string.language_pt), "Português (Brasil)"),
        Triple("ru", stringResource(R.string.language_ru), "Русский (Россия)"),
        Triple("ar", stringResource(R.string.language_ar), "العربية"),
        Triple("th", stringResource(R.string.language_th), "ไทย (ประเทศไทย)"),
        Triple("in", stringResource(R.string.language_in), "Bahasa Indonesia"),
        Triple("hi", stringResource(R.string.language_hi), "हिन्दी (भारत)"),
        Triple("tr", stringResource(R.string.language_tr), "Türkçe (Türkiye)"),
        Triple("pl", stringResource(R.string.language_pl), "Polski (Polska)"),
        Triple("nl", stringResource(R.string.language_nl), "Nederlands (Nederland)"),
        Triple("uk", stringResource(R.string.language_uk), "Українська (Україна)"),
        Triple("sv", stringResource(R.string.language_sv), "Svenska (Sverige)")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.search_cancel), fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.select_language), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(languages) { (code, title, desc) ->
                    val isSelected = currentLanguage == code
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectLanguage(code)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    )
}

