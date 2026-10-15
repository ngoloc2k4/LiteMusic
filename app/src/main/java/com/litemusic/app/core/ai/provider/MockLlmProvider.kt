package com.litemusic.app.core.ai.provider

import com.litemusic.app.core.ai.LlmProvider
import com.litemusic.app.core.ai.LlmRequest
import com.litemusic.app.core.ai.LlmResult

class MockLlmProvider : LlmProvider {
    override val id: String = "mock"
    override val displayName: String = "Thuật toán cục bộ (Không cần API/AI)"

    override suspend fun getAvailableModels(apiKey: String, forceRefresh: Boolean): List<String> {
        return listOf("local-heuristic-v1")
    }

    override suspend fun generate(apiKey: String, request: LlmRequest): LlmResult {
        // Returns basic mock JSON that passes validation
        return LlmResult.Success(
            text = """{"selectedIds": [], "reason": "Chạy bằng thuật toán cục bộ"}""",
            modelUsed = "local-heuristic-v1"
        )
    }
}
