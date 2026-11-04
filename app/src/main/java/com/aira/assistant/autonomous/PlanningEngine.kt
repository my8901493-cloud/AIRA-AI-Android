package com.aira.assistant.autonomous

import com.aira.assistant.domain.model.AutonomousTaskCategory
import com.aira.assistant.domain.model.SelectedToolDecision
import com.aira.assistant.domain.model.WorkflowStep

data class GeneratedWorkflowPlan(
    val steps: List<WorkflowStep>,
    val toolDecisions: List<SelectedToolDecision>
)

/**
 * Autonomous Multi-Step Planning Engine (`PlanningEngine`).
 *
 * Constructs an executable dependency graph (`List<WorkflowStep>`) with parallel execution
 * hints and verified tool assignments for each `AutonomousTaskCategory`.
 */
class PlanningEngine(
    private val toolSelectionEngine: ToolSelectionEngine
) {
    fun buildPlan(
        taskId: String,
        category: AutonomousTaskCategory,
        userApprovedPaidApis: Boolean = false
    ): GeneratedWorkflowPlan {
        val requiredCapabilities = when (category) {
            AutonomousTaskCategory.CODE_TO_APK_BUILD -> listOf(
                "WORKSPACE_MANAGEMENT" to ("Backup & Isolate Project Sources" to "Create versioned snapshot of original Android project before modifications"),
                "PROJECT_INSPECTION" to ("Inspect Gradle, Manifest & Sources" to "Verify Kotlin, Java, Compose, CMake, and Gradle dependency compatibility"),
                "CODE_REPAIR" to ("Auto-Fix Syntax & Dependency Issues" to "Apply minimal verified fixes to any broken imports, braces, or catalog versions"),
                "APK_BUILD" to ("Execute Build & Package APK / CI Bundle" to "Run local Gradle APK build if SDK present, or prepare & verify GitHub Actions cloud APK build"),
                "ZIP_PACKAGING" to ("Verify & Deliver Build Artifacts" to "Validate APK/source bundle integrity and output final deliverables")
            )

            AutonomousTaskCategory.SCRIPT_TO_VIDEO_PRODUCTION -> listOf(
                "SCRIPT_ANALYSIS" to ("Step 1: Script Analysis & Storyboard" to "Parse scenes, characters, locations, emotions, durations, and bilingual subtitles"),
                "SCENE_VISUAL_GENERATION" to ("Step 2: Cinematic Visual Scene Generation" to "Render HD watermark-free scene visuals, motion graphics HUDs, and typography overlays"),
                "VOICE_SYNTHESIS" to ("Step 3: Bilingual Voiceover Narration" to "Synthesize complete Hindi/Hinglish/English voiceover synchronized to scene timings"),
                "MUSIC_SOUND_DESIGN" to ("Step 4: Background Music, SFX & Ducking Mix" to "Generate ambient score & transition SFX, ducking music -14dB under narration"),
                "VIDEO_EDITING" to ("Step 5 & 6: Video Composition, Subtitles & Quality Check" to "Encode H.264/AAC MP4 with transitions & subtitles, then verify zero black frames & audio sync")
            )

            AutonomousTaskCategory.TEXT_TO_AUDIO_PRODUCTION -> listOf(
                "SCRIPT_ANALYSIS" to ("Analyze Text Cadence & Emotional Cues" to "Segment script into natural spoken clauses and breathing pauses"),
                "VOICE_SYNTHESIS" to ("Synthesize Expressive Female Voiceover" to "Generate clear narration across all script segments"),
                "AUDIO_MIXING" to ("Mix Ambient Score & Peak Normalize" to "Blend subtle background bed, apply peak normalization, and export WAV/MP3")
            )

            AutonomousTaskCategory.CODE_DIAGNOSIS_AND_REPAIR -> listOf(
                "WORKSPACE_MANAGEMENT" to ("Snapshot Original Source File" to "Save immutable backup copy of input source code"),
                "PROJECT_INSPECTION" to ("Diagnose Syntax & Logic Defects" to "Parse AST/compiler diagnostics to pinpoint root cause"),
                "CODE_REPAIR" to ("Apply Minimal Automated Repair & Run Tests" to "Fix defects, compile/verify, and validate all unit tests pass")
            )

            AutonomousTaskCategory.PROJECT_ORGANIZATION_AND_ARCHIVE,
            AutonomousTaskCategory.GENERAL_AUTONOMOUS_WORKFLOW -> listOf(
                "WORKSPACE_MANAGEMENT" to ("Organize Workspace & Assets" to "Structure files into clean directories with backups"),
                "PROJECT_INSPECTION" to ("Execute Task Operations" to "Process input files and synthesize requested output"),
                "ZIP_PACKAGING" to ("Package & Validate Final Deliverable" to "Create verified output archive and run integrity checks")
            )
        }

        val decisions = mutableListOf<SelectedToolDecision>()
        val steps = mutableListOf<WorkflowStep>()

        requiredCapabilities.forEachIndexed { idx, (capability, titleDesc) ->
            val decision = toolSelectionEngine.selectBestTool(capability, userApprovedPaidApis)
            decisions.add(decision)

            val stepNum = idx + 1
            val stepId = "${taskId}_step_$stepNum"
            // In Script-to-Video, Step 2 (Visuals) and Step 3 (Voice Synthesis) only depend on Step 1 (Storyboard) and can run in parallel!
            val dependsOn = when {
                idx == 0 -> emptyList()
                category == AutonomousTaskCategory.SCRIPT_TO_VIDEO_PRODUCTION && (stepNum == 2 || stepNum == 3) ->
                    listOf("${taskId}_step_1")
                category == AutonomousTaskCategory.SCRIPT_TO_VIDEO_PRODUCTION && stepNum == 4 ->
                    listOf("${taskId}_step_3")
                category == AutonomousTaskCategory.SCRIPT_TO_VIDEO_PRODUCTION && stepNum == 5 ->
                    listOf("${taskId}_step_2", "${taskId}_step_4")
                else -> listOf("${taskId}_step_${stepNum - 1}")
            }
            val parallelizable = category == AutonomousTaskCategory.SCRIPT_TO_VIDEO_PRODUCTION && (stepNum == 2 || stepNum == 3)

            steps.add(
                WorkflowStep(
                    stepId = stepId,
                    stepNumber = stepNum,
                    title = titleDesc.first,
                    description = titleDesc.second,
                    requiredCapability = capability,
                    assignedToolId = decision.selectedTool.toolId,
                    dependsOnStepIds = dependsOn,
                    canRunInParallel = parallelizable
                )
            )
        }

        return GeneratedWorkflowPlan(steps = steps, toolDecisions = decisions)
    }
}
