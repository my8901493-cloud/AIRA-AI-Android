package com.aira.assistant.operator

import java.io.File

data class BrowserResearchDeliverable(
    val topicOrVideoQuery: String,
    val sourceAppOrUrl: String,
    val summaryMarkdown: String,
    val savedReportFile: File
)

/**
 * Autonomous Browser & YouTube Research Agent (`BrowserResearchAgent`).
 *
 * Handles:
 * - Opening Chrome, searching a topic, opening top results, reading visible content, and saving a structured summary.
 * - Opening YouTube, searching for a video, opening the video, inspecting its visible title/description/transcript,
 *   and generating a concise summary report.
 */
class BrowserResearchAgent(
    private val appLaunchManager: AppLaunchManager,
    private val deviceController: AndroidDeviceController,
    private val browserEngine: BrowserAutomationEngine
) {
    fun researchTopicInChrome(
        topic: String,
        outputDir: File
    ): Result<BrowserResearchDeliverable> {
        outputDir.mkdirs()
        appLaunchManager.launchApp("com.android.chrome")
        val extractedResult = browserEngine.searchAndExtractWebTopic(topic)
        return extractedResult.map { summaryText ->
            val reportFile = File(outputDir, "chrome_research_summary.md")
            val markdown = buildString {
                appendLine("# Chrome Web Research Report: $topic")
                appendLine()
                appendLine("- **Operated Application**: Google Chrome (`com.android.chrome`)")
                appendLine("- **Interaction Method**: Accessibility Node + Visual OCR Verification")
                appendLine()
                appendLine("## Extracted Findings")
                appendLine(summaryText)
            }
            reportFile.writeText(markdown, Charsets.UTF_8)
            BrowserResearchDeliverable(
                topicOrVideoQuery = topic,
                sourceAppOrUrl = "com.android.chrome (Google Search)",
                summaryMarkdown = markdown,
                savedReportFile = reportFile
            )
        }
    }

    fun inspectAndSummarizeYouTubeVideo(
        videoQuery: String,
        outputDir: File
    ): Result<BrowserResearchDeliverable> {
        outputDir.mkdirs()
        val launchRes = appLaunchManager.launchApp("com.google.android.youtube")
        if (launchRes.isFailure) {
            // Fallback to mobile YouTube in Chrome if YouTube app isn't installed
            appLaunchManager.launchUrlInBrowser("https://m.youtube.com/results?search_query=${videoQuery.replace(" ", "+")}")
        }

        // Tap Search, type video query, tap first matching video, and inspect description/transcript
        deviceController.findAndTapElement("Search", allowScrollSearch = false)
        deviceController.typeTextInField(videoQuery, "Search YouTube")
        deviceController.findAndTapElement("Video result", allowScrollSearch = true)

        val snapshot = deviceController.captureAndUnderstandScreen()
        val visibleText = snapshot.elements
            .map { it.text.trim() }
            .filter { it.length > 15 }
            .joinToString("
- ")

        val reportFile = File(outputDir, "youtube_video_summary.md")
        val markdown = buildString {
            appendLine("# YouTube Video Inspection & Summary: $videoQuery")
            appendLine()
            appendLine("- **Operated Application**: YouTube (`com.google.android.youtube`)")
            appendLine("- **Query**: $videoQuery")
            appendLine()
            appendLine("## Video Content & Transcript Summary")
            appendLine("- $visibleText")
        }
        reportFile.writeText(markdown, Charsets.UTF_8)

        return Result.success(
            BrowserResearchDeliverable(
                topicOrVideoQuery = videoQuery,
                sourceAppOrUrl = "com.google.android.youtube",
                summaryMarkdown = markdown,
                savedReportFile = reportFile
            )
        )
    }
}
