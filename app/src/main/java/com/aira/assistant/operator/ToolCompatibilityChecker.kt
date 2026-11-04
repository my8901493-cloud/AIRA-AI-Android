package com.aira.assistant.operator

import com.aira.assistant.domain.model.FreeToolCandidate

data class CompatibilityVerdict(
    val isCompatible: Boolean,
    val requiresUserAppInstall: Boolean,
    val requiresPaidApproval: Boolean,
    val summaryReason: String
)

/**
 * Verifies whether a candidate tool or app is compatible with the current Android device,
 * required file formats, and user cost/permission constraints (`ToolCompatibilityChecker`).
 */
class ToolCompatibilityChecker(
    private val appDiscoveryManager: AppDiscoveryManager
) {
    fun checkCompatibility(
        candidate: FreeToolCandidate,
        requiredInputFormat: String? = null,
        requiredOutputFormat: String? = null
    ): CompatibilityVerdict {
        if (!candidate.worksOnAndroid || !candidate.isTrustworthyAndLegal) {
            return CompatibilityVerdict(
                isCompatible = false,
                requiresUserAppInstall = false,
                requiresPaidApproval = false,
                summaryReason = "${candidate.name} is not compatible with Android or failed safety verification."
            )
        }

        if (requiredInputFormat != null && candidate.supportedInputs.isNotEmpty() &&
            candidate.supportedInputs.none { it.equals(requiredInputFormat, ignoreCase = true) }
        ) {
            return CompatibilityVerdict(
                isCompatible = false,
                requiresUserAppInstall = false,
                requiresPaidApproval = false,
                summaryReason = "${candidate.name} does not support input format '.$requiredInputFormat'."
            )
        }

        if (requiredOutputFormat != null && candidate.supportedOutputs.isNotEmpty() &&
            candidate.supportedOutputs.none { it.equals(requiredOutputFormat, ignoreCase = true) }
        ) {
            return CompatibilityVerdict(
                isCompatible = false,
                requiresUserAppInstall = false,
                requiresPaidApproval = false,
                summaryReason = "${candidate.name} cannot export '.$requiredOutputFormat'."
            )
        }

        val needsInstall = candidate.sourceType == "PLAY_STORE_FREE" &&
            !appDiscoveryManager.isPackageInstalled(candidate.packageNameOrUrl)

        return CompatibilityVerdict(
            isCompatible = true,
            requiresUserAppInstall = needsInstall,
            requiresPaidApproval = candidate.requiresPaymentOrCredits || !candidate.isCompletelyFree,
            summaryReason = if (needsInstall) {
                "${candidate.name} is compatible and free, but is not currently installed on your phone."
            } else {
                "${candidate.name} is verified, installed/available, and ready for autonomous operation."
            }
        )
    }
}
