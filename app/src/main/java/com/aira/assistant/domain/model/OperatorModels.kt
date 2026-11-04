package com.aira.assistant.domain.model

import kotlinx.serialization.Serializable

/**
 * The 13 persistent task execution states required by Section 10 of the Real-World Operator spec.
 */
@Serializable
enum class OperatorExecutionState(val displayLabel: String) {
    ANALYZING("Analyzing Goal"),
    PLANNING("Planning Workflow"),
    DISCOVERING_TOOLS("Discovering Tools & Apps"),
    CHECKING_COST("Checking Cost & Free Limits"),
    WAITING_FOR_APPROVAL("Waiting for User Approval"),
    OPENING_APP("Opening Target App / Browser"),
    OPERATING_SCREEN("Operating Screen & UI"),
    EXECUTING("Executing Workflow"),
    VERIFYING("Verifying Output Deliverable"),
    RECOVERING("Recovering & Adapting"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

@Serializable
data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

@Serializable
data class UiElementNode(
    val nodeId: String,
    val viewIdResourceName: String? = null,
    val className: String = "",
    val text: String = "",
    val contentDescription: String = "",
    val bounds: UiBounds = UiBounds(0, 0, 0, 0),
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEnabled: Boolean = true,
    val sourceMethod: String = "ACCESSIBILITY_NODE" // ACCESSIBILITY_NODE or OCR_VISION
)

@Serializable
data class ScreenSnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val foregroundPackage: String = "",
    val activityOrWindowTitle: String = "",
    val elements: List<UiElementNode> = emptyList(),
    val ocrFullText: String = "",
    val isLoadingStateDetected: Boolean = false,
    val isErrorDialogDetected: Boolean = false,
    val isPermissionDialogDetected: Boolean = false,
    val isProtectedSecurityScreen: Boolean = false, // CAPTCHA, Payment, Banking/Biometric, Password prompt
    val protectedScreenReason: String? = null
)

@Serializable
enum class AppCapabilityCategory {
    BROWSER,
    YOUTUBE_MEDIA,
    VIDEO_EDITOR,
    AUDIO_EDITOR,
    IMAGE_EDITOR,
    FILE_MANAGER,
    DOCUMENT_EDITOR,
    VOICE_RECORDER,
    MEDIA_PLAYER,
    CODE_IDE_OR_TERM
}

@Serializable
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val category: AppCapabilityCategory,
    val isInstalled: Boolean = true,
    val supportsAccessibilityAutomation: Boolean = true,
    val supportedImportFormats: List<String> = emptyList(),
    val supportedExportFormats: List<String> = emptyList(),
    val verifiedFeatures: List<String> = emptyList()
)

@Serializable
data class FreeToolCandidate(
    val id: String,
    val name: String,
    val sourceType: String, // INSTALLED_APP, PLAY_STORE_FREE, OPEN_SOURCE_GITHUB, FREE_WEB_TOOL, LOCAL_ENGINE
    val packageNameOrUrl: String,
    val capability: String,
    val isCompletelyFree: Boolean,
    val hasFreeUsageLimit: Boolean = false,
    val freeLimitDescription: String = "Unlimited free usage",
    val requiresAccountLogin: Boolean = false,
    val requiresPaymentOrCredits: Boolean = false,
    val worksOnAndroid: Boolean = true,
    val operableViaBrowserOrApp: Boolean = true,
    val supportedInputs: List<String> = emptyList(),
    val supportedOutputs: List<String> = emptyList(),
    val isTrustworthyAndLegal: Boolean = true,
    val priorityRank: Int = 1
)

@Serializable
data class AppInstallationProposal(
    val missingCapability: String,
    val recommendedAppName: String,
    val packageName: String,
    val officialStoreUri: String, // e.g., market://details?id=... or https://play.google.com/store/apps/details?id=...
    val developerName: String,
    val pricingSummary: String,
    val requiredPermissionsSummary: String,
    val rationaleExplanation: String,
    val alternativesCompared: List<String> = emptyList(),
    val userApproved: Boolean = false
)

@Serializable
data class OperatorActivityLogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val taskId: String,
    val state: OperatorExecutionState,
    val targetAppOrWebsite: String,
    val actionPerformed: String,
    val verificationNote: String
)

@Serializable
data class OperatorTaskExecutionRecord(
    val taskId: String,
    val userGoal: String,
    val state: OperatorExecutionState,
    val currentStepDescription: String = "",
    val selectedToolOrApp: String = "",
    val selectionReason: String = "",
    val pendingInstallProposal: AppInstallationProposal? = null,
    val pendingApprovalReason: String? = null,
    val activityLog: List<OperatorActivityLogEntry> = emptyList(),
    val outputDeliverables: List<String> = emptyList(),
    val verificationPassed: Boolean = false,
    val verificationSummary: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)
