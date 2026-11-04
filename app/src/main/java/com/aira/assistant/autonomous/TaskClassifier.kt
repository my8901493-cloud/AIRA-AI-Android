package com.aira.assistant.autonomous

import com.aira.assistant.domain.model.AutonomousInputType
import com.aira.assistant.domain.model.AutonomousTaskCategory
import com.aira.assistant.domain.model.AutonomousTaskSubmission
import java.util.Locale

/**
 * Classifies an analyzed user input into an executable `AutonomousTaskCategory`
 * without asking the user which tool or pipeline to pick.
 */
class TaskClassifier {

    fun classify(submission: AutonomousTaskSubmission, analysis: AnalyzedInputBundle): AutonomousTaskCategory {
        val lower = submission.promptOrInstruction.lowercase(Locale.ROOT)

        return when {
            analysis.primaryInputType == AutonomousInputType.ANDROID_PROJECT_SOURCE ||
                lower.contains("build apk") ||
                lower.contains("compile apk") ||
                lower.contains("generate apk") -> {
                AutonomousTaskCategory.CODE_TO_APK_BUILD
            }

            analysis.primaryInputType == AutonomousInputType.VIDEO_SCRIPT ||
                lower.contains("make a video") ||
                lower.contains("create a video") ||
                lower.contains("30-second video") ||
                lower.contains("30 second video") ||
                lower.contains("produce video") ||
                lower.contains("mp4") -> {
                AutonomousTaskCategory.SCRIPT_TO_VIDEO_PRODUCTION
            }

            analysis.primaryInputType == AutonomousInputType.PLAIN_TEXT_NARRATION ||
                lower.contains("voiceover") ||
                lower.contains("narration audio") ||
                lower.contains("generate audio") ||
                lower.contains("export mp3") ||
                lower.contains("export wav") -> {
                AutonomousTaskCategory.TEXT_TO_AUDIO_PRODUCTION
            }

            analysis.primaryInputType == AutonomousInputType.SOURCE_CODE_SNIPPET ||
                analysis.containsBrokenSyntax ||
                lower.contains("fix code") ||
                lower.contains("broken code") ||
                lower.contains("debug") -> {
                AutonomousTaskCategory.CODE_DIAGNOSIS_AND_REPAIR
            }

            analysis.primaryInputType == AutonomousInputType.MULTI_FILE_PROJECT ||
                lower.contains("package project") ||
                lower.contains("organize files") ||
                lower.contains("create zip") -> {
                AutonomousTaskCategory.PROJECT_ORGANIZATION_AND_ARCHIVE
            }

            else -> AutonomousTaskCategory.GENERAL_AUTONOMOUS_WORKFLOW
        }
    }
}
