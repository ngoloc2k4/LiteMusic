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

class GeminiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : LlmProvider {

    override val id: String = "gemini"
    override val displayName: String = "Google Gemini (Free Tier)"

    override suspend fun getAvailableModels(apiKey: String, forceRefresh: Boolean): List<String> = withContext(Dispatchers.IO) {
        val defaultModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        )
        if (apiKey.isBlank()) return@withContext defaultModels

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext defaultModels

            val body = resp.body?.string() ?: return@withContext defaultModels
            val json = JSONObject(body)
            val modelsArr = json.optJSONArray("models") ?: return@withContext defaultModels
            val result = mutableListOf<String>()

            for (i in 0 until modelsArr.length()) {
                val m = modelsArr.getJSONObject(i)
                val name = m.optString("name").replace("models/", "")
                val methods = m.optJSONArray("supportedGenerationMethods")
                val supportsGenerate = methods != null && (0 until methods.length()).any { methods.getString(it) == "generateContent" }
                if (supportsGenerate && name.contains("flash") || name.contains("pro")) {
                    result.add(name)
                }
            }

            if (result.isNotEmpty()) result else defaultModels
        } catch (e: Exception) {
            defaultModels
        }
    }

    override suspend fun generate(apiKey: String, request: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext LlmResult.Error("Chưa thiết lập Gemini API Key. Vui lòng nhập key trong Cài đặt.", isRetryable = false)
        }

        val targetModel = request.model ?: "gemini-2.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey"

        val jsonPayload = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            val combinedPrompt = if (request.systemPrompt != null) {
                                "${request.systemPrompt}\n\nUser request: ${request.prompt}"
                            } else {
                                request.prompt
                            }
                            put("text", combinedPrompt)
                        })
                    })
                })
            }
            put("contents", contentsArr)
            put("generationConfig", JSONObject().apply {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
                if (request.responseJsonSchema) {
                    put("responseMimeType", "application/json")
                }
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toString().toRequestBody(mediaType)
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(httpRequest).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val isRetryable = code == 429 || code == 503 || code == 500
                val errMsg = if (code == 429) {
                    "Gemini API vượt quá hạn ngạch (429 Too Many Requests)."
                } else {
                    "Lỗi Gemini API (HTTP $code): $bodyString"
                }
                return@withContext LlmResult.Error(errMsg, isRetryable = isRetryable, statusCode = code)
            }

            val respJson = JSONObject(bodyString)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext LlmResult.Error("Gemini không trả về kết quả nào.", isRetryable = false)
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

            // Sanitize markdown ```json codeblocks if present
            val cleanedText = sanitizeJsonBlock(rawText)
            LlmResult.Success(text = cleanedText, modelUsed = targetModel)
        } catch (e: Exception) {
            LlmResult.Error("Lỗi kết nối Gemini: ${e.localizedMessage ?: e.message}", isRetryable = true)
        }
    }

    private fun sanitizeJsonBlock(input: String): String {
        var text = input.trim()
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json").trim()
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```").trim()
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }
        return text
    }
}
