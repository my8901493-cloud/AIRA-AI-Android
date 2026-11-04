package com.aira.assistant.autonomous

import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.WorkflowStep

data class ErrorRecoveryAction(
    val canRecoverAutomatically: Boolean,
    val rootCauseDiagnosis: String,
    val appliedFixDescription: String,
    val replacementToolId: String? = null
)

/**
 * Autonomous Error Diagnosis & Self-Healing Engine (`ErrorRecoveryEngine`).
 *
 * When a workflow step throws an exception or fails validation:
 * 1. Captures the error message and logs.
 * 2. Diagnoses the root cause (e.g., missing local SDK/binary, syntax error in input code,
 *    transient I/O failure, or unreachable network endpoint).
 * 3. Determines whether AIRA can self-repair or switch to a verified fallback tool
 *    within bounded retry limits (`MAX_RETRIES_PER_STEP = 2`) to prevent infinite loops.
 */
class ErrorRecoveryEngine(
    private val maxRetriesPerStep: Int = 2
) {
    fun diagnoseAndRecover(
        failedStep: WorkflowStep,
        errorMessage: String,
        fallbackToolId: String? = null
    ): ErrorRecoveryAction {
        if (failedStep.attemptCount >= maxRetriesPerStep) {
            return ErrorRecoveryAction(
                canRecoverAutomatically = false,
                rootCauseDiagnosis = "Step '${failedStep.title}' exceeded max retry limit ($maxRetriesPerStep): $errorMessage",
                appliedFixDescription = "Halted retries to prevent infinite loop."
            )
        }

        val lower = errorMessage.lowercase()

        if (lower.contains("sdk") || lower.contains("javac") || lower.contains("gradle") || lower.contains("command not found")) {
            val replacement = fallbackToolId ?: "github-actions-cloud-apk-builder"
            AiraLogger.i("ErrorRecoveryEngine", "Recovering '${failedStep.title}' by switching to fallback tool: $replacement")
            return ErrorRecoveryAction(
                canRecoverAutomatically = true,
                rootCauseDiagnosis = "Local build binary or SDK unavailable on host ($errorMessage)",
                appliedFixDescription = "Switched execution to verified cloud CI / local fallback tool '$replacement'.",
                replacementToolId = replacement
            )
        }

        if (lower.contains("unbalanced") || lower.contains("syntax") || lower.contains("parse")) {
            return ErrorRecoveryAction(
                canRecoverAutomatically = true,
                rootCauseDiagnosis = "Source code syntax imbalance detected ($errorMessage)",
                appliedFixDescription = "Invoking AST delimiter & import auto-repair pass before retrying."
            )
        }

        return ErrorRecoveryAction(
            canRecoverAutomatically = true,
            rootCauseDiagnosis = "Transient execution error in '${failedStep.title}': $errorMessage",
            appliedFixDescription = "Cleaned temporary staging buffers and retrying step (attempt ${failedStep.attemptCount + 1}/$maxRetriesPerStep).",
            replacementToolId = fallbackToolId
        )
    }
}
