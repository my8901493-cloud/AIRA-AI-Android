package com.aira.assistant.operator

import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.FreeToolCandidate
import com.aira.assistant.domain.model.OperatorActivityLogEntry
import com.aira.assistant.domain.model.OperatorExecutionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SafetyGateDecision(
    val isAllowedImmediately: Boolean,
    val requiresUserApprovalReason: String? = null
)

/**
 * Permission, Paid-Service Protection & Transparent Activity Log Manager (`CostAndPermissionManager`).
 *
 * Strictly enforces Section 11:
 * Explicit user approval is required before:
 * - Installing applications or APK files
 * - Granting sensitive permissions
 * - Making purchases, starting subscriptions, or using paid APIs/services
 * - Uploading private files to external websites
 * - Publishing or sending content publicly
 * - Deleting important files outside the task workspace
 * - Sending messages or emails
 * - Changing security or account settings
 */
class CostAndPermissionManager {

    private val _activityLog = MutableStateFlow<List<OperatorActivityLogEntry>>(emptyList())
    val activityLog: StateFlow<List<OperatorActivityLogEntry>> = _activityLog.asStateFlow()

    private val highRiskActionPatterns = listOf(
        "install apk",
        "purchase",
        "subscribe",
        "paid api",
        "upload private",
        "publish publicly",
        "delete important",
        "send email",
        "send whatsapp message",
        "change password",
        "security settings"
    )

    fun evaluateCandidateAndGoal(
        userGoal: String,
        candidate: FreeToolCandidate?,
        userExplicitlyApprovedPaidOrSensitive: Boolean = false
    ): SafetyGateDecision {
        if (candidate != null && (candidate.requiresPaymentOrCredits || !candidate.isCompletelyFree)) {
            if (!userExplicitlyApprovedPaidOrSensitive) {
                return SafetyGateDecision(
                    isAllowedImmediately = false,
                    requiresUserApprovalReason = "Tool '${candidate.name}' requires payment or paid credits (${candidate.freeLimitDescription}). Explicit user approval is required before proceeding."
                )
            }
        }

        val lower = userGoal.lowercase(Locale.ROOT)
        val matchedPattern = highRiskActionPatterns.firstOrNull { lower.contains(it) }
        if (matchedPattern != null && !userExplicitlyApprovedPaidOrSensitive) {
            return SafetyGateDecision(
                isAllowedImmediately = false,
                requiresUserApprovalReason = "Goal involves a protected action ('$matchedPattern'). Please confirm before AIRA proceeds."
            )
        }

        return SafetyGateDecision(isAllowedImmediately = true)
    }

    fun recordActivity(
        taskId: String,
        state: OperatorExecutionState,
        targetAppOrWebsite: String,
        actionPerformed: String,
        verificationNote: String
    ) {
        val entry = OperatorActivityLogEntry(
            timestampMs = System.currentTimeMillis(),
            taskId = taskId,
            state = state,
            targetAppOrWebsite = targetAppOrWebsite,
            actionPerformed = actionPerformed,
            verificationNote = verificationNote
        )
        _activityLog.value = (_activityLog.value + entry).takeLast(250)
        AiraLogger.i(
            "OperatorActivityLog",
            "[${state.name}] App/Site='$targetAppOrWebsite' | Action='$actionPerformed' | Verified='$verificationNote'"
        )
    }

    fun getLogsForTask(taskId: String): List<OperatorActivityLogEntry> =
        _activityLog.value.filter { it.taskId == taskId }
}
