package com.aira.assistant.ai

import com.aira.assistant.domain.model.AiraLanguage
import com.aira.assistant.domain.model.ChatMessage
import com.aira.assistant.domain.model.MemoryItem
import kotlinx.coroutines.flow.Flow

data class LlmGenerationRequest(
    val userPrompt: String,
    val language: AiraLanguage,
    val conversationHistory: List<ChatMessage> = emptyList(),
    val relevantMemories: List<MemoryItem> = emptyList(),
    val screenContextText: String? = null,
    val toolContextOutput: String? = null,
    val maxTokens: Int = 512,
    val temperature: Float = 0.65f
)

/**
 * Provider-independent AI Reasoning Engine interface.
 * Supports streaming token emission and immediate cooperative cancellation on user barge-in.
 */
interface LlmEngine {
    val engineId: String
    val displayName: String
    val isReady: Boolean

    suspend fun initialize(): Boolean
    fun streamResponse(request: LlmGenerationRequest): Flow<String>
    fun cancelActiveGeneration()
    suspend fun release()
}
