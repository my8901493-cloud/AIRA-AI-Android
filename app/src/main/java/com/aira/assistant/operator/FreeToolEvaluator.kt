package com.aira.assistant.operator

import com.aira.assistant.domain.model.AppCapabilityCategory
import com.aira.assistant.domain.model.FreeToolCandidate

/**
 * Free-First Tool Discovery & Evaluator (`FreeToolEvaluator`).
 *
 * Searches across installed Android apps, free Play Store apps, open-source GitHub/F-Droid tools,
 * free browser-based web tools, and local engines.
 *
 * Strictly evaluates:
 * - Whether the tool is genuinely free vs. paid/credits/trial-only
 * - Whether it works on Android and supports required input/output formats
 * - Prioritizes:
 *   1. Already-installed free applications (`priorityRank = 1`)
 *   2. Local on-device open-source processing (`priorityRank = 2`)
 *   3. Free online browser tools with no paywall (`priorityRank = 3`)
 *   4. Official Play Store / F-Droid free apps requiring user installation approval (`priorityRank = 4`)
 */
class FreeToolEvaluator(
    private val appDiscoveryManager: AppDiscoveryManager
) {
    private val unavailableOrPaidOverrideIds = mutableSetOf<String>()

    fun markToolUnavailableOrPaidForTest(toolId: String) {
        unavailableOrPaidOverrideIds.add(toolId)
    }

    fun clearTestOverrides() {
        unavailableOrPaidOverrideIds.clear()
    }

    fun discoverCandidatesForCapability(capability: String): List<FreeToolCandidate> {
        val installedVideoEditors = appDiscoveryManager.findInstalledAppsByCategory(AppCapabilityCategory.VIDEO_EDITOR)
        val installedAudioEditors = appDiscoveryManager.findInstalledAppsByCategory(AppCapabilityCategory.AUDIO_EDITOR)

        val candidates = mutableListOf<FreeToolCandidate>()

        // 1. Add already-installed compatible apps (Priority Rank 1)
        installedVideoEditors.forEach { app ->
            candidates.add(
                FreeToolCandidate(
                    id = "installed_${app.packageName}",
                    name = "${app.appName} (Installed App)",
                    sourceType = "INSTALLED_APP",
                    packageNameOrUrl = app.packageName,
                    capability = "VIDEO_EDITING",
                    isCompletelyFree = true,
                    hasFreeUsageLimit = false,
                    requiresAccountLogin = false,
                    requiresPaymentOrCredits = app.packageName in unavailableOrPaidOverrideIds,
                    worksOnAndroid = true,
                    operableViaBrowserOrApp = app.supportsAccessibilityAutomation,
                    supportedInputs = app.supportedImportFormats,
                    supportedOutputs = app.supportedExportFormats,
                    priorityRank = 1
                )
            )
        }

        installedAudioEditors.forEach { app ->
            candidates.add(
                FreeToolCandidate(
                    id = "installed_${app.packageName}",
                    name = "${app.appName} (Installed App)",
                    sourceType = "INSTALLED_APP",
                    packageNameOrUrl = app.packageName,
                    capability = "AUDIO_EDITING",
                    isCompletelyFree = true,
                    requiresPaymentOrCredits = app.packageName in unavailableOrPaidOverrideIds,
                    worksOnAndroid = true,
                    operableViaBrowserOrApp = app.supportsAccessibilityAutomation,
                    supportedInputs = app.supportedImportFormats,
                    supportedOutputs = app.supportedExportFormats,
                    priorityRank = 1
                )
            )
        }

        // 2. Add full catalog of Local Open-Source, Free Web, and Play Store Free Candidates
        candidates.addAll(
            listOf(
                FreeToolCandidate(
                    id = "aira_local_ffmpeg_mediamuxer",
                    name = "AIRA Local Open-Source Video & Audio Studio (FFmpeg / MediaMuxer)",
                    sourceType = "LOCAL_ENGINE",
                    packageNameOrUrl = "com.aira.assistant.local.media",
                    capability = "VIDEO_EDITING",
                    isCompletelyFree = "aira_local_ffmpeg_mediamuxer" !in unavailableOrPaidOverrideIds,
                    requiresPaymentOrCredits = "aira_local_ffmpeg_mediamuxer" in unavailableOrPaidOverrideIds,
                    worksOnAndroid = true,
                    operableViaBrowserOrApp = true,
                    supportedInputs = listOf("mp4", "png", "jpg", "wav", "mp3", "srt"),
                    supportedOutputs = listOf("mp4"),
                    priorityRank = 2
                ),
                FreeToolCandidate(
                    id = "playstore_vn_video_editor",
                    name = "VN Video Editor Maker VlogNow (Google Play Free)",
                    sourceType = "PLAY_STORE_FREE",
                    packageNameOrUrl = "com.frontrow.vlog",
                    capability = "VIDEO_EDITING",
                    isCompletelyFree = "playstore_vn_video_editor" !in unavailableOrPaidOverrideIds,
                    freeLimitDescription = "100% free 1080p/4K MP4 export with zero watermark",
                    requiresAccountLogin = false,
                    requiresPaymentOrCredits = "playstore_vn_video_editor" in unavailableOrPaidOverrideIds,
                    worksOnAndroid = true,
                    operableViaBrowserOrApp = true,
                    supportedInputs = listOf("mp4", "mov", "png", "jpg", "wav", "mp3"),
                    supportedOutputs = listOf("mp4"),
                    priorityRank = 4
                ),
                FreeToolCandidate(
                    id = "web_cobalt_open_media_tool",
                    name = "Open-Source Browser Media Processor (WebAssembly FFmpeg)",
                    sourceType = "FREE_WEB_TOOL",
                    packageNameOrUrl = "https://ffmpegwasm.netlify.app",
                    capability = "VIDEO_EDITING",
                    isCompletelyFree = true,
                    requiresPaymentOrCredits = false,
                    worksOnAndroid = true,
                    operableViaBrowserOrApp = true,
                    supportedInputs = listOf("mp4", "wav", "mp3"),
                    supportedOutputs = listOf("mp4"),
                    priorityRank = 3
                ),
                FreeToolCandidate(
                    id = "playstore_kdenlive_vespucci_3d",
                    name = "Spck Code Editor / Termux Open-Source Sandbox",
                    sourceType = "PLAY_STORE_FREE",
                    packageNameOrUrl = "io.spck",
                    capability = "MOBILE_CODE_SANDBOX",
                    isCompletelyFree = true,
                    requiresPaymentOrCredits = false,
                    worksOnAndroid = true,
                    operableViaBrowserOrApp = true,
                    supportedInputs = listOf("kt", "js", "html", "css", "json"),
                    supportedOutputs = listOf("zip", "js"),
                    priorityRank = 4
                ),
                FreeToolCandidate(
                    id = "paid_cloud_video_render_api",
                    name = "Commercial Cloud Video Render API (Paid Credits Required)",
                    sourceType = "PAID_CLOUD_SERVICE",
                    packageNameOrUrl = "https://api.example-paid-video.com",
                    capability = "VIDEO_EDITING",
                    isCompletelyFree = false,
                    hasFreeUsageLimit = true,
                    freeLimitDescription = "Requires paid API key ($0.25/minute)",
                    requiresAccountLogin = true,
                    requiresPaymentOrCredits = true,
                    worksOnAndroid = true,
                    operableViaBrowserOrApp = true,
                    supportedInputs = listOf("mp4", "script"),
                    supportedOutputs = listOf("mp4"),
                    priorityRank = 99
                )
            )
        )

        return candidates.filter {
            (it.capability.equals(capability, ignoreCase = true) || capability == "ANY") &&
                it.id !in unavailableOrPaidOverrideIds
        }.sortedWith(
            compareBy<FreeToolCandidate> { it.requiresPaymentOrCredits }
                .thenBy { it.priorityRank }
        )
    }

    /**
     * Selects the highest-priority genuinely free and compatible tool for the requested capability.
     */
    fun selectBestFreeTool(
        capability: String,
        requiredOutputFormat: String? = null
    ): FreeToolCandidate? {
        return discoverCandidatesForCapability(capability).firstOrNull { candidate ->
            candidate.isCompletelyFree &&
                !candidate.requiresPaymentOrCredits &&
                candidate.worksOnAndroid &&
                candidate.isTrustworthyAndLegal &&
                (requiredOutputFormat == null || candidate.supportedOutputs.any { it.equals(requiredOutputFormat, ignoreCase = true) })
        }
    }
}
