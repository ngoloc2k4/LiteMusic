package com.litemusic.app.feature.aimusic.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litemusic.app.core.ai.LlmRouter
import com.litemusic.app.core.ai.security.AiKeyStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsSection(
    isGlass: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val router = remember { LlmRouter(context) }

    var selectedProvider by remember { mutableStateOf(AiKeyStorage.getSelectedProvider(context)) }
    var geminiKey by remember { mutableStateOf(AiKeyStorage.getGeminiKey(context)) }
    var openRouterKey by remember { mutableStateOf(AiKeyStorage.getOpenRouterKey(context)) }
    var autoFallback by remember { mutableStateOf(AiKeyStorage.isAutoFallbackLocal(context)) }

    var cachedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedModel by remember { mutableStateOf(AiKeyStorage.getSelectedModel(context, selectedProvider)) }
    var isRefreshingModels by remember { mutableStateOf(false) }

    // Load cached models
    LaunchedEffect(selectedProvider) {
        val cached = AiKeyStorage.getCachedModels(context, selectedProvider)
        if (cached != null && !cached.isExpired && cached.models.isNotEmpty()) {
            cachedModels = cached.models
        } else {
            coroutineScope.launch {
                isRefreshingModels = true
                cachedModels = router.getModelsWith3DayCache(selectedProvider)
                isRefreshingModels = false
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.2.dp, Color(0xFFA855F7).copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFA855F7))
                Text(
                    text = "🤖 Cấu hình AI Music Multi-Provider",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Hệ thống AI độc lập giúp tự động tạo danh sách phát, phân tích lời nhạc và tìm kiếm ngữ nghĩa.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )

            // Provider Selector
            Text("Nhà cung cấp LLM chính:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("gemini" to "Gemini Free", "openrouter" to "OpenRouter", "mock" to "Cục bộ (Offline)").forEach { (id, label) ->
                    val isSelected = selectedProvider == id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedProvider = id
                            AiKeyStorage.setSelectedProvider(context, id)
                            selectedModel = AiKeyStorage.getSelectedModel(context, id)
                        },
                        label = { Text(label, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Gemini Key Field
            if (selectedProvider == "gemini") {
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = {
                        geminiKey = it
                        AiKeyStorage.setGeminiKey(context, it)
                    },
                    label = { Text("Google Gemini API Key (Miễn phí)") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (selectedProvider == "openrouter") {
                OutlinedTextField(
                    value = openRouterKey,
                    onValueChange = {
                        openRouterKey = it
                        AiKeyStorage.setOpenRouterKey(context, it)
                    },
                    label = { Text("OpenRouter API Key") },
                    placeholder = { Text("sk-or-v1-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3-Day Model Cache Display & Manual Reload
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cacheInfo = AiKeyStorage.getCachedModels(context, selectedProvider)
                    val statusText = if (cacheInfo != null && !cacheInfo.isExpired) {
                        "Cache còn ${cacheInfo.remainingHours}h / 3 ngày"
                    } else {
                        "Cache 3 ngày tự động"
                    }

                    Text("Danh sách Model ($statusText):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isRefreshingModels = true
                                cachedModels = router.getModelsWith3DayCache(selectedProvider, forceRefresh = true)
                                isRefreshingModels = false
                                Toast.makeText(context, "Đã cập nhật danh sách model mới!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        if (isRefreshingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới cache", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (cachedModels.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            cachedModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model, fontSize = 12.sp) },
                                    onClick = {
                                        selectedModel = model
                                        AiKeyStorage.setSelectedModel(context, selectedProvider, model)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Fallback toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tự động fallback thuật toán cục bộ", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("Khi không có API Key hoặc hết quota, tự động tạo playlist trên máy", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = autoFallback,
                    onCheckedChange = {
                        autoFallback = it
                        AiKeyStorage.setAutoFallbackLocal(context, it)
                    }
                )
            }
        }
    }
}
