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

class OpenAiCompatibleProvider(
    override val id: String = "groq",
    override val displayName: String = "Groq (Ultra-fast / Llama 3.3)",
    private val defaultBaseUrl: String = "https://api.groq.com/openai/v1",
    private val getCustomBaseUrl: (() -> String)? = null
) : LlmProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val fallbackModels = if (id == "groq") {
        listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
        )
    } else {
        listOf(
            "gpt-4o-mini",
            "gpt-3.5-turbo",
            "claude-3-haiku",
            "deepseek-chat"
        )
    }

    private fun getEffectiveBaseUrl(): String {
        val custom = getCustomBaseUrl?.invoke()?.trim()
        if (!custom.isNullOrEmpty()) {
            return custom.removeSuffix("/")
        }
        return defaultBaseUrl.removeSuffix("/")
    }

    override suspend fun getAvailableModels(apiKey: String, forceRefresh: Boolean): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext fallbackModels

        try {
            val url = "${getEffectiveBaseUrl()}/models"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext fallbackModels
            }

            val body = response.body?.string() ?: return@withContext fallbackModels
            val json = JSONObject(body)
            val dataArray = json.optJSONArray("data") ?: return@withContext fallbackModels

            val models = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val mId = item.optString("id")
                if (mId.isNotEmpty() && !mId.contains("whisper") && !mId.contains("embedding")) {
                    models.add(mId)
                }
            }

            if (models.isNotEmpty()) models else fallbackModels
        } catch (_: Exception) {
            fallbackModels
        }
    }

    override suspend fun generate(apiKey: String, request: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext LlmResult.Error("API Key is missing. Please configure in Settings.", isRetryable = false)
        }

        val chosenModel = request.model?.ifBlank { null } ?: fallbackModels.first()
        val url = "${getEffectiveBaseUrl()}/chat/completions"

        val messagesArr = JSONArray()
        if (!request.systemPrompt.isNullOrBlank()) {
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", request.systemPrompt)
            })
        }
        messagesArr.put(JSONObject().apply {
            put("role", "user")
            put("content", request.prompt)
        })

        val payload = JSONObject().apply {
            put("model", chosenModel)
            put("messages", messagesArr)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            if (request.responseJsonSchema) {
                put("response_format", JSONObject().apply {
                    put("type", "json_object")
                })
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)

        val httpRequest = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(httpRequest).execute()
            val code = response.code
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val isRetryable = (code == 429 || code >= 500)
                val errMsg = try {
                    val errJson = JSONObject(responseBody).optJSONObject("error")
                    errJson?.optString("message", "API Error $code: $responseBody") ?: "API Error $code: $responseBody"
                } catch (_: Exception) {
                    "API HTTP Error $code"
                }
                return@withContext LlmResult.Error(errMsg, isRetryable = isRetryable, statusCode = code)
            }

            val respJson = JSONObject(responseBody)
            val choices = respJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext LlmResult.Error("No content returned from $displayName", isRetryable = false)
            }

            val firstChoice = choices.getJSONObject(0)
            val messageObj = firstChoice.optJSONObject("message")
            val content = messageObj?.optString("content", "") ?: ""
            val usageObj = respJson.optJSONObject("usage")
            val totalTokens = usageObj?.optInt("total_tokens")

            LlmResult.Success(
                text = content,
                modelUsed = chosenModel,
                totalTokens = totalTokens
            )
        } catch (e: Exception) {
            LlmResult.Error(e.message ?: "Connection error to $displayName", isRetryable = true)
        }
    }
}
