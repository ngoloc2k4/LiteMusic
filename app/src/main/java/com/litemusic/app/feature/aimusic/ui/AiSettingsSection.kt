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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litemusic.app.R
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
    var groqKey by remember { mutableStateOf(AiKeyStorage.getGroqKey(context)) }
    var customOpenAiKey by remember { mutableStateOf(AiKeyStorage.getCustomOpenAiKey(context)) }
    var customOpenAiUrl by remember { mutableStateOf(AiKeyStorage.getCustomOpenAiUrl(context)) }
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
                    text = stringResource(R.string.ai_settings_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(R.string.ai_settings_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )

            // Provider Selector
            Text(stringResource(R.string.ai_settings_provider_label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val providers = listOf(
                    "groq" to "⚡ Groq (Llama 3.3 Free)",
                    "gemini" to "✨ Gemini Free",
                    "openrouter" to "🌐 OpenRouter",
                    "custom_openai" to "⚙️ Custom OpenAI",
                    "mock" to "📴 Offline"
                )
                items(providers.size) { index ->
                    val (id, label) = providers[index]
                    val isSelected = selectedProvider == id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedProvider = id
                            AiKeyStorage.setSelectedProvider(context, id)
                            selectedModel = AiKeyStorage.getSelectedModel(context, id)
                        },
                        label = { Text(label, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // Provider Specific Fields
            when (selectedProvider) {
                "groq" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = groqKey,
                            onValueChange = {
                                groqKey = it
                                AiKeyStorage.setGroqKey(context, it)
                            },
                            label = { Text(stringResource(R.string.ai_settings_groq_label)) },
                            placeholder = { Text("gsk_...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.ai_settings_groq_desc), fontSize = 11.sp, color = Color(0xFF10B981))
                    }
                }
                "gemini" -> {
                    OutlinedTextField(
                        value = geminiKey,
                        onValueChange = {
                            geminiKey = it
                            AiKeyStorage.setGeminiKey(context, it)
                        },
                        label = { Text(stringResource(R.string.ai_settings_gemini_label)) },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "openrouter" -> {
                    OutlinedTextField(
                        value = openRouterKey,
                        onValueChange = {
                            openRouterKey = it
                            AiKeyStorage.setOpenRouterKey(context, it)
                        },
                        label = { Text(stringResource(R.string.ai_settings_openrouter_label)) },
                        placeholder = { Text("sk-or-v1-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "custom_openai" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customOpenAiUrl,
                            onValueChange = {
                                customOpenAiUrl = it
                                AiKeyStorage.setCustomOpenAiUrl(context, it)
                            },
                            label = { Text(stringResource(R.string.ai_settings_openai_url_label)) },
                            placeholder = { Text("https://api.openai.com/v1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customOpenAiKey,
                            onValueChange = {
                                customOpenAiKey = it
                                AiKeyStorage.setCustomOpenAiKey(context, it)
                            },
                            label = { Text(stringResource(R.string.ai_settings_openai_key_label)) },
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
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
                        stringResource(R.string.ai_settings_cache_remaining, cacheInfo.remainingHours)
                    } else {
                        stringResource(R.string.ai_settings_cache_auto)
                    }

                    Text(stringResource(R.string.ai_settings_models_label, statusText), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isRefreshingModels = true
                                cachedModels = router.getModelsWith3DayCache(selectedProvider, forceRefresh = true)
                                isRefreshingModels = false
                                Toast.makeText(context, context.getString(R.string.ai_settings_models_refreshed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        if (isRefreshingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.ai_settings_models_refresh_btn), modifier = Modifier.size(18.dp))
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
                    Text(stringResource(R.string.ai_settings_fallback_title), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.ai_settings_fallback_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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
