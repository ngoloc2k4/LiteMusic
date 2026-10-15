package com.litemusic.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
    var showCacheDialog by remember { mutableStateOf(false) }
    var showSkipDialog by remember { mutableStateOf(false) }
    var showInfiniteBatchDialog by remember { mutableStateOf(false) }

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
                Toast.makeText(context, "Đã dọn dẹp $freedMb MB bộ nhớ đệm!", Toast.LENGTH_SHORT).show()
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
                                text = "Cài đặt hệ thống",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tùy biến trải nghiệm âm nhạc cá nhân hóa",
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
            SettingsGroupHeader(title = "GIAO DIỆN & MÀU SẮC")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Chủ đề & Bảng màu",
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
            }
        }

        // Section 2: Âm thanh & Phát nhạc (Audio & Playback Engine)
        item {
            SettingsGroupHeader(title = "ÂM THANH & PHÁT NHẠC")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                // DSP Loudness
                SettingsRow(
                    icon = Icons.Default.GraphicEq,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "Cân bằng âm lượng DSP (-14 LUFS)",
                    subtitle = "Chuẩn hóa âm lượng tự động, chống giật âm giữa các nguồn",
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
                    title = "Cử chỉ chạm đúp để tua",
                    subtitle = "Tua nhanh hoặc lùi khi chạm 2 lần vào màn hình phát",
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
                    title = "Chế độ phát vô hạn (Infinite Mode)",
                    subtitle = if (isInfinitePlayback) "Tự động gợi ý nối tiếp +$infiniteBatchSize bài khi hết danh sách" else "Đang tắt phát vô hạn",
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
                    title = "Tự động nối bài liên quan",
                    subtitle = "Tạo danh sách chờ tương đồng khi mở 1 bài hát đơn lẻ",
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
                    title = "Danh sách chặn (Kênh, Nghệ sĩ, Bài hát)",
                    subtitle = "Quản lý từ khóa và nghệ sĩ bị lọc khỏi ứng dụng",
                    onClick = onOpenBlocklist,
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }

        // Section 3: Dữ liệu & Bộ nhớ (Storage & Cache Management)
        item {
            SettingsGroupHeader(title = "BỘ NHỚ & DỮ LIỆU")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                val usedFormatted = String.format(java.util.Locale.US, "%.1f", usedCacheMb)
                val percent = ((usedCacheMb / maxCacheMb.toDouble()) * 100).toInt().coerceIn(0, 100)

                SettingsRow(
                    icon = Icons.Default.Storage,
                    iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Bộ nhớ đệm âm thanh (Audio Cache)",
                    subtitle = "Đã dùng: $usedFormatted MB / $maxCacheMb MB ($percent%)",
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
            SettingsGroupHeader(title = "CÔNG CỤ NHÀ PHÁT TRIỂN & CHẨN ĐOÁN")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                SettingsRow(
                    icon = Icons.Default.BugReport,
                    iconBgColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Chế độ Gỡ lỗi (Debug & Logcat Hub)",
                    subtitle = if (isDebugMode) "Máy chủ nội bộ đang chạy: $debugServerUrl" else "Khởi chạy local HTTP server để theo dõi log trực tiếp",
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
                                    Text("Web Dashboard Localhost:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    text = debugServerUrl,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Mở link trên trình duyệt để theo dõi logcat, xem lỗi hoặc lọc theo tag.",
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
                                Text("Sao chép Logcat", fontSize = 11.5.sp)
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
                                Text("Xóa Log", fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // Section 6: Đóng góp & Thông tin (Community & About)
        item {
            SettingsGroupHeader(title = "CỘNG ĐỒNG & THÔNG TIN")
            Spacer(modifier = Modifier.height(6.dp))
            SettingsGroupCard(isGlass = isGlass) {
                // Crowdin Translation
                SettingsRow(
                    icon = Icons.Default.Translate,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "Đóng góp dịch thuật (Crowdin)",
                    subtitle = "Hỗ trợ bản địa hóa LiteMusic sang nhiều ngôn ngữ",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://crowdin.com/project/litemusic"))
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Mở trình duyệt: https://crowdin.com/project/litemusic", Toast.LENGTH_LONG).show()
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
                    title = "LiteMusic Open Source",
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
                Text("Xong", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Tùy biến Giao diện", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category 1: Phong cách thiết kế
                Text("1. Phong cách thiết kế", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val styles = listOf(
                        AppThemeStyle.MATERIAL_3 to ("M3 Chuẩn" to Icons.Default.Layers),
                        AppThemeStyle.LIQUID_GLASS to ("Liquid Glass" to Icons.Default.BlurOn)
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
                Text("2. Chế độ hiển thị", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                val modes = listOf(
                    AppThemeMode.DARK_OLED to "Tối OLED (#000)",
                    AppThemeMode.DARK_STANDARD to "Tối chuẩn M3",
                    AppThemeMode.LIGHT to "Sáng (Light Mode)",
                    AppThemeMode.SYSTEM to "Theo hệ thống Android"
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
                Text("3. Bảng màu điểm nhấn", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                Text("Lưu & Đóng", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Bộ nhớ đệm âm thanh", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                            Text("Dung lượng sử dụng:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text("$usedFormatted MB / ${localMax.toInt()} MB ($percent%)", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }

                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = if (ratio > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )

                        Text(
                            text = "Khi bộ nhớ đầy, hệ thống tự động xóa các bài hát cũ nhất để nhường chỗ cho bài mới tải.",
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
                    Text("Dọn sạch toàn bộ bộ nhớ đệm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Slider for Max Cache (200MB to 1500MB)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Giới hạn dung lượng tối đa:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            TextButton(onClick = onDismiss) { Text("Đóng", fontWeight = FontWeight.Bold) }
        },
        title = {
            Text("Thời gian chạm đúp để tua", fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                                text = "$sec giây",
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
            TextButton(onClick = onDismiss) { Text("Đóng", fontWeight = FontWeight.Bold) }
        },
        title = {
            Text("Số lượng bài hát gợi ý phát vô hạn", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Khi phát đến bài cuối cùng trong hàng đợi, hệ thống sẽ tự động tìm kiếm và nối tiếp thêm số bài hát này:",
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
                                text = "+$count bài tiếp theo",
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
