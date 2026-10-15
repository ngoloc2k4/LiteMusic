package com.litemusic.app.core.ai

import android.content.Context
import com.litemusic.app.core.ai.model.CachedModelCatalog
import com.litemusic.app.core.ai.provider.GeminiProvider
import com.litemusic.app.core.ai.provider.MockLlmProvider
import com.litemusic.app.core.ai.provider.OpenRouterProvider
import com.litemusic.app.core.ai.security.AiKeyStorage

class LlmRouter(private val context: Context) {

    val geminiProvider = GeminiProvider()
    val openRouterProvider = OpenRouterProvider()
    val mockProvider = MockLlmProvider()

    fun getProvider(providerId: String): LlmProvider {
        return when (providerId) {
            "gemini" -> geminiProvider
            "openrouter" -> openRouterProvider
            else -> mockProvider
        }
    }

    fun getApiKeyForProvider(providerId: String): String {
        return when (providerId) {
            "gemini" -> AiKeyStorage.getGeminiKey(context)
            "openrouter" -> AiKeyStorage.getOpenRouterKey(context)
            else -> ""
        }
    }

    /**
     * Get available models with 3-day cache strategy
     */
    suspend fun getModelsWith3DayCache(providerId: String, forceRefresh: Boolean = false): List<String> {
        val cached = AiKeyStorage.getCachedModels(context, providerId)
        val apiKey = getApiKeyForProvider(providerId)

        if (!forceRefresh && cached != null && !cached.isExpired && cached.models.isNotEmpty()) {
            return cached.models
        }

        // Needs refresh or cache expired
        val provider = getProvider(providerId)
        val freshModels = provider.getAvailableModels(apiKey, forceRefresh = true)

        if (freshModels.isNotEmpty()) {
            val newCatalog = CachedModelCatalog(
                providerId = providerId,
                models = freshModels,
                lastFetchedTimestamp = System.currentTimeMillis()
            )
            AiKeyStorage.saveCachedModels(context, newCatalog)
        }

        return freshModels
    }

    /**
     * Executes generation with automatic fallback to secondary provider or local
     */
    suspend fun generateWithFallback(request: LlmRequest): LlmResult {
        val selectedProviderId = AiKeyStorage.getSelectedProvider(context)
        val primaryProvider = getProvider(selectedProviderId)
        val primaryKey = getApiKeyForProvider(selectedProviderId)
        val selectedModel = AiKeyStorage.getSelectedModel(context, selectedProviderId)

        val activeRequest = request.copy(model = selectedModel)

        // If no API key configured for primary, check if we can fallback to OpenRouter or local
        if (primaryKey.isBlank()) {
            if (selectedProviderId != "openrouter" && AiKeyStorage.getOpenRouterKey(context).isNotBlank()) {
                val orKey = AiKeyStorage.getOpenRouterKey(context)
                val orModel = AiKeyStorage.getSelectedModel(context, "openrouter")
                return openRouterProvider.generate(orKey, request.copy(model = orModel))
            }
            return LlmResult.Error("Chưa cài đặt API Key cho AI.", isRetryable = false)
        }

        // Try primary
        val result = primaryProvider.generate(primaryKey, activeRequest)

        if (result is LlmResult.Success) {
            return result
        }

        // Handle fallback on retryable error (like 429 quota exhausted or 503 service unavailable)
        if (result is LlmResult.Error && result.isRetryable && AiKeyStorage.isAutoFallbackLocal(context)) {
            if (selectedProviderId == "gemini" && AiKeyStorage.getOpenRouterKey(context).isNotBlank()) {
                val orKey = AiKeyStorage.getOpenRouterKey(context)
                val orModel = AiKeyStorage.getSelectedModel(context, "openrouter")
                val fallbackResult = openRouterProvider.generate(orKey, request.copy(model = orModel))
                if (fallbackResult is LlmResult.Success) return fallbackResult
            }
        }

        return result
    }
}
