package com.litemusic.app.core.ai.security

import android.content.Context
import android.content.SharedPreferences
import com.litemusic.app.core.ai.model.CachedModelCatalog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AiKeyStorage {
    private const val PREFS_NAME = "litemusic_ai_preferences"
    private const val KEY_SELECTED_PROVIDER = "ai_selected_provider"
    private const val KEY_GEMINI_KEY = "ai_gemini_api_key"
    private const val KEY_OPENROUTER_KEY = "ai_openrouter_api_key"
    private const val KEY_GROQ_KEY = "ai_groq_api_key"
    private const val KEY_CUSTOM_OPENAI_KEY = "ai_custom_openai_key"
    private const val KEY_CUSTOM_OPENAI_URL = "ai_custom_openai_url"
    private const val KEY_GEMINI_MODEL = "ai_gemini_model"
    private const val KEY_OPENROUTER_MODEL = "ai_openrouter_model"
    private const val KEY_GROQ_MODEL = "ai_groq_model"
    private const val KEY_CUSTOM_OPENAI_MODEL = "ai_custom_openai_model"
    private const val KEY_MODEL_CACHE_PREFIX = "ai_model_cache_"
    private const val KEY_AUTO_FALLBACK_LOCAL = "ai_auto_fallback_local"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedProvider(context: Context): String {
        return getPrefs(context).getString(KEY_SELECTED_PROVIDER, "gemini") ?: "gemini"
    }

    fun setSelectedProvider(context: Context, providerId: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_PROVIDER, providerId).apply()
    }

    fun getGeminiKey(context: Context): String {
        return getPrefs(context).getString(KEY_GEMINI_KEY, "") ?: ""
    }

    fun setGeminiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_GEMINI_KEY, key.trim()).apply()
    }

    fun getOpenRouterKey(context: Context): String {
        return getPrefs(context).getString(KEY_OPENROUTER_KEY, "") ?: ""
    }

    fun setOpenRouterKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_OPENROUTER_KEY, key.trim()).apply()
    }

    fun getGroqKey(context: Context): String {
        return getPrefs(context).getString(KEY_GROQ_KEY, "") ?: ""
    }

    fun setGroqKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_GROQ_KEY, key.trim()).apply()
    }

    fun getCustomOpenAiKey(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_OPENAI_KEY, "") ?: ""
    }

    fun setCustomOpenAiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_OPENAI_KEY, key.trim()).apply()
    }

    fun getCustomOpenAiUrl(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_OPENAI_URL, "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
    }

    fun setCustomOpenAiUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_OPENAI_URL, url.trim()).apply()
    }

    fun getSelectedModel(context: Context, providerId: String): String {
        return when (providerId) {
            "openrouter" -> getPrefs(context).getString(KEY_OPENROUTER_MODEL, "meta-llama/llama-3.1-8b-instruct:free") ?: "meta-llama/llama-3.1-8b-instruct:free"
            "groq" -> getPrefs(context).getString(KEY_GROQ_MODEL, "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
            "custom_openai" -> getPrefs(context).getString(KEY_CUSTOM_OPENAI_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
            else -> getPrefs(context).getString(KEY_GEMINI_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
        }
    }

    fun setSelectedModel(context: Context, providerId: String, model: String) {
        val key = when (providerId) {
            "openrouter" -> KEY_OPENROUTER_MODEL
            "groq" -> KEY_GROQ_MODEL
            "custom_openai" -> KEY_CUSTOM_OPENAI_MODEL
            else -> KEY_GEMINI_MODEL
        }
        getPrefs(context).edit().putString(key, model).apply()
    }

    fun isAutoFallbackLocal(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_FALLBACK_LOCAL, true)
    }

    fun setAutoFallbackLocal(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_FALLBACK_LOCAL, enabled).apply()
    }

    fun getCachedModels(context: Context, providerId: String): CachedModelCatalog? {
        val raw = getPrefs(context).getString(KEY_MODEL_CACHE_PREFIX + providerId, null) ?: return null
        return try {
            json.decodeFromString<CachedModelCatalog>(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCachedModels(context: Context, catalog: CachedModelCatalog) {
        val raw = json.encodeToString(catalog)
        getPrefs(context).edit().putString(KEY_MODEL_CACHE_PREFIX + catalog.providerId, raw).apply()
    }

    fun clearCache(context: Context, providerId: String) {
        getPrefs(context).edit().remove(KEY_MODEL_CACHE_PREFIX + providerId).apply()
    }
}
