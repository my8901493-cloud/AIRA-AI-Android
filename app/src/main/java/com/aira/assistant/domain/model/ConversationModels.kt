package com.aira.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

@Serializable
data class ChatMessage(
    val id: Long = 0L,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val languageDetected: String = "en-IN",
    val toolInvoked: String? = null,
    val latencyMs: Long? = null,
    val wasInterrupted: Boolean = false
)

@Serializable
data class ConversationSession(
    val id: Long = 0L,
    val title: String,
    val summary: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)
