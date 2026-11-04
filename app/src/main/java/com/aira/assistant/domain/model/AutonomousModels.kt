package com.aira.assistant.domain.model

import kotlinx.serialization.Serializable

/**
 * Explicit progress stages shown in the Autonomous Task Submission UI.
 */
@Serializable
enum class AutonomousTaskStage(val displayLabel: String) {
    QUEUED("Queued"),
    ANALYZING("Analyzing"),
    PLANNING("Planning"),
    SELECTING_TOOLS("Selecting Tools"),
    WAITING_APPROVAL("Waiting Approval"),
    EXECUTING("Executing"),
    RECOVERING("Recovering Error"),
    VALIDATING("Validating"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    FAILED("Failed");

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED || this == FAILED
}

@Serializable
enum class AutonomousInputType {
    ANDROID_PROJECT_SOURCE,
    SOURCE_CODE_SNIPPET,
    VIDEO_SCRIPT,
    PLAIN_TEXT_NARRATION,
    AUDIO_FILE,
    VIDEO_FILE,
    DOCUMENT_PDF,
    IMAGE_FILE,
    MULTI_FILE_PROJECT,
    GENERAL_INSTRUCTION
}

@Serializable
enum class AutonomousTaskCategory(val title: String) {
    CODE_TO_APK_BUILD("Autonomous Code-to-APK Build"),
    SCRIPT_TO_VIDEO_PRODUCTION("Autonomous Script-to-Video Production"),
    TEXT_TO_AUDIO_PRODUCTION("Autonomous Audio & Voiceover Production"),
    CODE_DIAGNOSIS_AND_REPAIR("Autonomous Code Repair & Testing"),
    PROJECT_ORGANIZATION_AND_ARCHIVE("Autonomous File & Project Packaging"),
    GENERAL_AUTONOMOUS_WORKFLOW("Autonomous Multi-Step Workflow")
}

@Serializable
enum class ToolCostTier(val rank: Int, val label: String) {
    FREE_LOCAL(1, "Free Local On-Device"),
    FREE_OPEN_SOURCE_MODEL(2, "Free Open-Source Model"),
    FREE_CLOUD_CI(3, "Free Cloud Build (GitHub Actions)"),
    AUTHORIZED_INTEGRATION(4, "Authorized User Integration"),
    PAID_API_REQUIRES_APPROVAL(5, "Paid API (Requires Explicit Approval)")
}

@Serializable
data class AutonomousToolMetadata(
    val toolId: String,
    val name: String,
    val purpose: String,
    val supportedInputFormats: List<String>,
    val supportedOutputFormats: List<String>,
    val capabilities: List<String>,
    val costTier: ToolCostTier,
    val requiresApiKey: Boolean = false,
    val isLocalExecution: Boolean = true,
    val minRamMb: Int = 256,
    val qualityScore: Float = 0.90f,
    val reliabilityScore: Float = 0.92f,
    val speedScore: Float = 0.88f,
    val privacyScore: Float = 0.98f,
    val limitations: String = ""
)

@Serializable
data class SelectedToolDecision(
    val capabilityRequired: String,
    val selectedTool: AutonomousToolMetadata,
    val compositeScore: Float,
    val selectionRationale: String,
    val fallbackToolId: String? = null
)

@Serializable
data class WorkflowStep(
    val stepId: String,
    val stepNumber: Int,
    val title: String,
    val description: String,
    val requiredCapability: String,
    val assignedToolId: String,
    val dependsOnStepIds: List<String> = emptyList(),
    val canRunInParallel: Boolean = false,
    val status: String = "PENDING",
    val attemptCount: Int = 0,
    val outputArtifactPath: String? = null,
    val logSummary: String = ""
)

@Serializable
data class StoryboardScene(
    val sceneNumber: Int,
    val title: String,
    val narrationText: String,
    val visualPrompt: String,
    val visualMode: String, // AI_GENERATED_CANVAS, MOTION_GRAPHICS_HUD, ANIMATED_STILL, EXISTING_ASSET
    val characterFocus: String,
    val locationSetting: String,
    val emotionTone: String,
    val durationSeconds: Float,
    val subtitleText: String,
    val musicMood: String,
    val sfxCue: String
)

@Serializable
data class VideoAspectRatio(
    val label: String,
    val width: Int,
    val height: Int
) {
    companion object {
        val WIDESCREEN_16_9 = VideoAspectRatio("16:9 Widescreen", 1280, 720)
        val SHORTS_9_16 = VideoAspectRatio("9:16 Vertical Shorts", 720, 1280)
    }
}

@Serializable
data class QualityValidationReport(
    val passed: Boolean,
    val artifactPath: String,
    val fileSizeBytes: Long,
    val checksPerformed: Map<String, Boolean>,
    val metrics: Map<String, String> = emptyMap(),
    val failureReasons: List<String> = emptyList()
)

@Serializable
data class AutonomousTaskSubmission(
    val promptOrInstruction: String,
    val attachedFiles: List<String> = emptyList(),
    val pastedCodeOrScript: String = "",
    val preferredVideoAspect: String = "16:9", // "16:9" or "9:16"
    val userApprovedHighConsequenceActions: Boolean = false,
    val userApprovedPaidApis: Boolean = false
)

@Serializable
data class AutonomousTaskRecord(
    val taskId: String,
    val title: String,
    val inputType: AutonomousInputType,
    val category: AutonomousTaskCategory,
    val stage: AutonomousTaskStage,
    val progressFraction: Float = 0f,
    val steps: List<WorkflowStep> = emptyList(),
    val toolDecisions: List<SelectedToolDecision> = emptyList(),
    val outputFiles: List<String> = emptyList(),
    val validationReport: QualityValidationReport? = null,
    val errorRecoveryLogs: List<String> = emptyList(),
    val requiresApprovalReason: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)
