package com.aira.assistant.autonomous

import com.aira.assistant.domain.model.AutonomousTaskSubmission
import com.aira.assistant.domain.model.AutonomousToolMetadata
import com.aira.assistant.domain.model.ToolCostTier
import java.util.Locale

data class AutonomousApprovalVerdict(
    val allowedImmediately: Boolean,
    val reasonIfBlocked: String? = null
)

/**
 * Distinguishes ordinary reversible workspace operations (which AIRA executes autonomously
 * without interrupting the user) from high-consequence actions that require explicit approval:
 * - Spending money / using paid APIs
 * - Publishing content publicly
 * - Sending messages or emails
 * - Deleting user files outside the task sandbox
 * - Uploading private data to unauthorized servers
 * - Running destructive system commands
 */
class PermissionManager {

    private val highConsequenceKeywords = listOf(
        "delete all my files",
        "rm -rf /",
        "format sdcard",
        "publish publicly",
        "send email to",
        "transfer money",
        "purchase",
        "pay with card"
    )

    fun evaluateSubmission(
        submission: AutonomousTaskSubmission,
        selectedTools: List<AutonomousToolMetadata>
    ): AutonomousApprovalVerdict {
        val lower = submission.promptOrInstruction.lowercase(Locale.ROOT)

        if (!submission.userApprovedPaidApis &&
            selectedTools.any { it.costTier == ToolCostTier.PAID_API_REQUIRES_APPROVAL }
        ) {
            return AutonomousApprovalVerdict(
                allowedImmediately = false,
                reasonIfBlocked = "This workflow selected a paid external API. Please approve paid API usage or allow free local execution."
            )
        }

        val matchedHighRisk = highConsequenceKeywords.firstOrNull { lower.contains(it) }
        if (matchedHighRisk != null && !submission.userApprovedHighConsequenceActions) {
            return AutonomousApprovalVerdict(
                allowedImmediately = false,
                reasonIfBlocked = "Explicit authorization required before executing consequential action ('$matchedHighRisk')."
            )
        }

        // Ordinary workspace operations (building code, generating video/audio, fixing code, packaging ZIPs) run autonomously
        return AutonomousApprovalVerdict(allowedImmediately = true)
    }
}
