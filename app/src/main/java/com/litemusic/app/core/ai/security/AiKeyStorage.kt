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
    private const val KEY_GEMINI_MODEL = "ai_gemini_model"
    private const val KEY_OPENROUTER_MODEL = "ai_openrouter_model"
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

    fun getSelectedModel(context: Context, providerId: String): String {
        val key = if (providerId == "openrouter") KEY_OPENROUTER_MODEL else KEY_GEMINI_MODEL
        val defaultModel = if (providerId == "openrouter") "meta-llama/llama-3.1-8b-instruct:free" else "gemini-2.5-flash"
        return getPrefs(context).getString(key, defaultModel) ?: defaultModel
    }

    fun setSelectedModel(context: Context, providerId: String, model: String) {
        val key = if (providerId == "openrouter") KEY_OPENROUTER_MODEL else KEY_GEMINI_MODEL
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
