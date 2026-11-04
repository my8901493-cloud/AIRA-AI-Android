package com.aira.assistant.domain.model

import kotlinx.serialization.Serializable

/**
 * Five distinct memory categories supported by AIRA's Long-Term Memory Engine.
 */
@Serializable
enum class MemoryCategory(val displayName: String) {
    SHORT_TERM_CONTEXT("Short-Term Context"),
    USER_PREFERENCE("User Preference"),
    IMPORTANT_FACT("Important Saved Info"),
    TASK_PROJECT_CONTEXT("Task & Project Context"),
    CONVERSATION_SUMMARY("Conversation Summary")
}

@Serializable
data class MemoryItem(
    val id: Long = 0L,
    val category: MemoryCategory,
    val subjectKey: String,
    val content: String,
    val confidence: Float = 1.0f,
    val userConsented: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val lastAccessedMs: Long = System.currentTimeMillis(),
    val accessCount: Int = 1
)
