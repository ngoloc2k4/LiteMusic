package com.litemusic.app.core.ai.provider

import com.litemusic.app.core.ai.LlmProvider
import com.litemusic.app.core.ai.LlmRequest
import com.litemusic.app.core.ai.LlmResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenRouterProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : LlmProvider {

    override val id: String = "openrouter"
    override val displayName: String = "OpenRouter (Free :free models)"

    override suspend fun getAvailableModels(apiKey: String, forceRefresh: Boolean): List<String> = withContext(Dispatchers.IO) {
        val defaultFreeModels = listOf(
            "meta-llama/llama-3.1-8b-instruct:free",
            "mistralai/mistral-7b-instruct:free",
            "google/gemini-2.0-flash-lite-preview-02-05:free",
            "qwen/qwen-2.5-72b-instruct:free"
        )
        if (apiKey.isBlank()) return@withContext defaultFreeModels

        try {
            val req = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://github.com/ngoloc2k4/LiteMusic")
                .header("X-Title", "LiteMusic Android")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext defaultFreeModels

            val body = resp.body?.string() ?: return@withContext defaultFreeModels
            val json = JSONObject(body)
            val dataArr = json.optJSONArray("data") ?: return@withContext defaultFreeModels

            val freeModels = mutableListOf<String>()
            for (i in 0 until dataArr.length()) {
                val m = dataArr.getJSONObject(i)
                val modelId = m.optString("id")
                if (modelId.endsWith(":free")) {
                    freeModels.add(modelId)
                }
            }

            if (freeModels.isNotEmpty()) freeModels else defaultFreeModels
        } catch (e: Exception) {
            defaultFreeModels
        }
    }

    override suspend fun generate(apiKey: String, request: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext LlmResult.Error("OpenRouter API Key is missing. Please set it in Settings.", isRetryable = false)
        }

        val targetModel = request.model ?: "meta-llama/llama-3.1-8b-instruct:free"
        val url = "https://openrouter.ai/api/v1/chat/completions"

        val messagesArr = JSONArray().apply {
            if (request.systemPrompt != null) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", request.systemPrompt)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", request.prompt)
            })
        }

        val jsonPayload = JSONObject().apply {
            put("model", targetModel)
            put("messages", messagesArr)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toString().toRequestBody(mediaType)
        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/ngoloc2k4/LiteMusic")
            .header("X-Title", "LiteMusic Android")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(httpRequest).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val isRetryable = code == 429 || code == 503
                return@withContext LlmResult.Error("OpenRouter error (HTTP $code): $bodyString", isRetryable = isRetryable, statusCode = code)
            }

            val respJson = JSONObject(bodyString)
            val choices = respJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext LlmResult.Error("No choices returned from OpenRouter.", isRetryable = false)
            }

            val msg = choices.getJSONObject(0).optJSONObject("message")
            val rawText = msg?.optString("content") ?: ""

            var cleanedText = rawText.trim()
            if (cleanedText.startsWith("```json")) cleanedText = cleanedText.removePrefix("```json").trim()
            else if (cleanedText.startsWith("```")) cleanedText = cleanedText.removePrefix("```").trim()
            if (cleanedText.endsWith("```")) cleanedText = cleanedText.removeSuffix("```").trim()

            LlmResult.Success(text = cleanedText, modelUsed = targetModel)
        } catch (e: Exception) {
            LlmResult.Error("OpenRouter connection error: ${e.localizedMessage ?: e.message}", isRetryable = true)
        }
    }
}
