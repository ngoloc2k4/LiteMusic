package com.litemusic.app.core.ai

import kotlinx.serialization.Serializable

@Serializable
data class LlmMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

@Serializable
data class LlmRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val model: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val responseJsonSchema: Boolean = false
)

sealed interface LlmResult {
    data class Success(
        val text: String,
        val modelUsed: String,
        val totalTokens: Int? = null
    ) : LlmResult

    data class Error(
        val message: String,
        val isRetryable: Boolean = false,
        val statusCode: Int? = null
    ) : LlmResult
}

interface LlmProvider {
    val id: String
    val displayName: String

    suspend fun getAvailableModels(apiKey: String, forceRefresh: Boolean = false): List<String>

    suspend fun generate(
        apiKey: String,
        request: LlmRequest
    ): LlmResult
}
