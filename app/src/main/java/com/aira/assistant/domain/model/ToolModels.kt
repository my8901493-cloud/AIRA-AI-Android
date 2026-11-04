package com.aira.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ToolExecutionStatus {
    SUCCESS,
    REQUIRES_PERMISSION,
    REQUIRES_CONFIRMATION,
    INVALID_INPUT,
    EXECUTION_ERROR,
    UNAVAILABLE_OFFLINE
}

@Serializable
data class ToolParameterSpec(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameterSpec>,
    val requiredAndroidPermissions: List<String> = emptyList(),
    val requiresExplicitConfirmation: Boolean = false,
    val worksOffline: Boolean = true
)

@Serializable
data class ToolExecutionResult(
    val toolName: String,
    val status: ToolExecutionStatus,
    val outputText: String,
    val structuredData: Map<String, String> = emptyMap(),
    val missingPermissions: List<String> = emptyList(),
    val confirmationPrompt: String? = null,
    val verified: Boolean = true,
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
data class AgentStep(
    val stepIndex: Int,
    val description: String,
    val toolName: String?,
    val arguments: Map<String, String> = emptyMap(),
    val status: String = "PENDING",
    val resultSummary: String = ""
)

@Serializable
data class AgentPlan(
    val requestId: String,
    val originalQuery: String,
    val detectedIntent: String,
    val requiresUserConfirmation: Boolean,
    val confirmationMessage: String? = null,
    val steps: List<AgentStep>
)
