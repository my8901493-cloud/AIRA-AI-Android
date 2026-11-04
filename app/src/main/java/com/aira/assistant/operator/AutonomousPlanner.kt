package com.aira.assistant.operator

import java.util.Locale

enum class OperatorIntentType {
    CHROME_WEB_RESEARCH,
    YOUTUBE_VIDEO_SUMMARY,
    INSTALLED_APP_VIDEO_EDIT,
    SCRIPT_TO_CINEMATIC_VIDEO,
    TEXT_TO_VOICEOVER_AUDIO,
    MISSING_APP_DISCOVERY_AND_INSTALL,
    CODE_TO_APK_BUILD,
    FREE_TOOL_SEARCH_AND_EXECUTION
}

data class OperatorExecutionPlan(
    val intentType: OperatorIntentType,
    val targetCapability: String,
    val extractedQueryOrTopic: String,
    val explicitRequiredPackage: String? = null,
    val plannedSteps: List<String>
)

/**
 * Converts natural Hindi, Hinglish, or English user goals into an executable `OperatorExecutionPlan`.
 *
 * Examples handled automatically:
 * - "Chrome kholo aur is topic par research karo" -> `CHROME_WEB_RESEARCH`
 * - "YouTube par ye video dekho aur iska summary banao" -> `YOUTUBE_VIDEO_SUMMARY`
 * - "Editing app kholo aur video edit karo" -> `INSTALLED_APP_VIDEO_EDIT`
 * - "Meri script ko cinematic video mein convert karo" -> `SCRIPT_TO_CINEMATIC_VIDEO`
 * - "Is Android project ka APK build karo" -> `CODE_TO_APK_BUILD`
 * - "Aisa free tool dhundo jo ye kaam kar sake" -> `FREE_TOOL_SEARCH_AND_EXECUTION`
 */
class AutonomousPlanner {

    fun createPlan(userGoal: String, requiredUninstalledPackageHint: String? = null): OperatorExecutionPlan {
        val trimmed = userGoal.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        if (!requiredUninstalledPackageHint.isNullOrBlank()) {
            return OperatorExecutionPlan(
                intentType = OperatorIntentType.MISSING_APP_DISCOVERY_AND_INSTALL,
                targetCapability = "MOBILE_CODE_SANDBOX",
                extractedQueryOrTopic = trimmed,
                explicitRequiredPackage = requiredUninstalledPackageHint,
                plannedSteps = listOf(
                    "Identify missing application capability ($requiredUninstalledPackageHint)",
                    "Search and compare free Play Store / open-source alternatives",
                    "Present app purpose, free pricing, and permissions to user for approval",
                    "Open official Google Play Store listing after user approval"
                )
            )
        }

        return when {
            lower.contains("youtube") && (lower.contains("summary") || lower.contains("dekho") || lower.contains("watch") || lower.contains("summarize")) -> {
                OperatorExecutionPlan(
                    intentType = OperatorIntentType.YOUTUBE_VIDEO_SUMMARY,
                    targetCapability = "YOUTUBE_MEDIA",
                    extractedQueryOrTopic = trimmed,
                    plannedSteps = listOf(
                        "Launch YouTube application or mobile site",
                        "Tap search bar and enter video topic",
                        "Open top matching video and inspect accessible description & transcript",
                        "Synthesize and verify structured summary markdown"
                    )
                )
            }

            (lower.contains("chrome") || lower.contains("browser") || lower.contains("google") || lower.contains("research karo")) &&
                !lower.contains("apk") -> {
                OperatorExecutionPlan(
                    intentType = OperatorIntentType.CHROME_WEB_RESEARCH,
                    targetCapability = "BROWSER",
                    extractedQueryOrTopic = trimmed,
                    plannedSteps = listOf(
                        "Launch Google Chrome browser",
                        "Execute Google search for requested topic",
                        "Open top organic result and extract visible content via Accessibility + OCR",
                        "Compile verified research report into workspace"
                    )
                )
            }

            lower.contains("editing app") || lower.contains("editor kholo") || lower.contains("open editor") || lower.contains("import the video") -> {
                OperatorExecutionPlan(
                    intentType = OperatorIntentType.INSTALLED_APP_VIDEO_EDIT,
                    targetCapability = "VIDEO_EDITING",
                    extractedQueryOrTopic = trimmed,
                    plannedSteps = listOf(
                        "Discover installed Android video editing applications",
                        "Launch selected video editor and tap 'New Project'",
                        "Import video & audio assets, trim timeline, and add subtitles",
                        "Export MP4 and verify file integrity and playback"
                    )
                )
            }

            lower.contains("apk") || lower.contains("android project") -> {
                OperatorExecutionPlan(
                    intentType = OperatorIntentType.CODE_TO_APK_BUILD,
                    targetCapability = "APK_BUILD",
                    extractedQueryOrTopic = trimmed,
                    plannedSteps = listOf(
                        "Inspect Android project structure, Gradle catalog, and Manifest",
                        "Snapshot backup and auto-fix syntax/dependency issues",
                        "Execute local Gradle build or prepare verified GitHub Actions Cloud APK build",
                        "Verify output archive / APK integrity"
                    )
                )
            }

            lower.contains("voiceover") || (lower.contains("audio") && !lower.contains("video")) -> {
                OperatorExecutionPlan(
                    intentType = OperatorIntentType.TEXT_TO_VOICEOVER_AUDIO,
                    targetCapability = "AUDIO_EDITING",
                    extractedQueryOrTopic = trimmed,
                    plannedSteps = listOf(
                        "Analyze narration cadence and pauses",
                        "Synthesize expressive female voiceover",
                        "Mix ambient score with -14dB voice ducking and peak normalize to -1.0 dBFS",
                        "Export and verify playable MP3/WAV audio"
                    )
                )
            }

            lower.contains("video") || lower.contains("script") -> {
                OperatorExecutionPlan(
                    intentType = OperatorIntentType.SCRIPT_TO_CINEMATIC_VIDEO,
                    targetCapability = "VIDEO_EDITING",
                    extractedQueryOrTopic = trimmed,
                    plannedSteps = listOf(
                        "Analyze script into timed storyboard scenes and subtitles",
                        "Select best available free video tool (Installed Editor or AIRA Local Studio)",
                        "Generate HD visuals, voiceover narration, and ducked ambient score",
                        "Export H.264/AAC MP4 and verify zero black frames & audio sync"
                    )
                )
            }

            else -> {
                OperatorExecutionPlan(
                    intentType = OperatorIntentType.FREE_TOOL_SEARCH_AND_EXECUTION,
                    targetCapability = "VIDEO_EDITING",
                    extractedQueryOrTopic = trimmed,
                    plannedSteps = listOf(
                        "Search installed apps, free web tools, and open-source engines",
                        "Filter out paid/subscription-locked options and select top free tool",
                        "Execute workflow and verify output artifact"
                    )
                )
            }
        }
    }
}
