package com.aira.assistant.autonomous

import com.aira.assistant.domain.model.AutonomousInputType
import com.aira.assistant.domain.model.AutonomousTaskSubmission
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

data class AnalyzedInputBundle(
    val primaryInputType: AutonomousInputType,
    val detectedLanguages: List<String>,
    val containsAndroidProject: Boolean,
    val containsBrokenSyntax: Boolean,
    val detectedFileExtensions: Set<String>,
    val estimatedSceneCount: Int,
    val totalInputSizeBytes: Long,
    val summaryDescription: String
)

/**
 * Inspects any user submission (files, project ZIPs, source code, scripts, audio/video/PDFs,
 * or natural-language objectives) to extract concrete structural metadata before classification.
 */
class InputAnalyzer {

    fun analyze(submission: AutonomousTaskSubmission): AnalyzedInputBundle {
        val promptLower = submission.promptOrInstruction.lowercase(Locale.ROOT)
        val codeOrScript = submission.pastedCodeOrScript.trim()
        val extensions = mutableSetOf<String>()
        var totalSize = codeOrScript.length.toLong()
        var hasAndroidStructure = false

        for (path in submission.attachedFiles) {
            val file = File(path)
            if (file.exists()) {
                totalSize += file.length()
                val ext = file.extension.lowercase(Locale.ROOT)
                if (ext.isNotEmpty()) extensions.add(ext)

                if (ext == "zip") {
                    runCatching {
                        ZipFile(file).use { zf ->
                            val entryNames = zf.entries().asSequence().map { it.name }.toList()
                            if (entryNames.any { it.endsWith("AndroidManifest.xml") || it.endsWith("build.gradle.kts") || it.endsWith("build.gradle") }) {
                                hasAndroidStructure = true
                            }
                        }
                    }
                } else if (file.isDirectory) {
                    if (File(file, "settings.gradle.kts").exists() || File(file, "app/src/main/AndroidManifest.xml").exists()) {
                        hasAndroidStructure = true
                    }
                }
            }
        }

        if (codeOrScript.contains("AndroidManifest") ||
            codeOrScript.contains("androidx.compose") ||
            promptLower.contains("build apk") ||
            promptLower.contains("android project")
        ) {
            hasAndroidStructure = true
        }

        val containsCodeMarkers = codeOrScript.contains("fun ") ||
            codeOrScript.contains("class ") ||
            codeOrScript.contains("def ") ||
            codeOrScript.contains("#include") ||
            codeOrScript.contains("package ") ||
            extensions.any { it in setOf("kt", "java", "py", "cpp", "c", "js", "ts") }

        val hasBrokenBraces = if (containsCodeMarkers && codeOrScript.isNotEmpty()) {
            codeOrScript.count { it == '{' } != codeOrScript.count { it == '}' } ||
                codeOrScript.count { it == '(' } != codeOrScript.count { it == ')' } ||
                promptLower.contains("fix") ||
                promptLower.contains("broken") ||
                promptLower.contains("error")
        } else false

        val isVideoRequest = promptLower.contains("video") ||
            promptLower.contains("mp4") ||
            promptLower.contains("short") ||
            promptLower.contains("reel") ||
            promptLower.contains("storyboard") ||
            promptLower.contains("scene")

        val isVoiceoverRequest = !isVideoRequest && (
            promptLower.contains("voiceover") ||
                promptLower.contains("narration") ||
                promptLower.contains("audio file") ||
                promptLower.contains("mp3") ||
                promptLower.contains("wav") ||
                promptLower.contains("tts")
            )

        val primaryType = when {
            hasAndroidStructure -> AutonomousInputType.ANDROID_PROJECT_SOURCE
            isVideoRequest -> AutonomousInputType.VIDEO_SCRIPT
            isVoiceoverRequest -> AutonomousInputType.PLAIN_TEXT_NARRATION
            containsCodeMarkers -> AutonomousInputType.SOURCE_CODE_SNIPPET
            extensions.contains("pdf") || extensions.contains("docx") -> AutonomousInputType.DOCUMENT_PDF
            extensions.any { it in setOf("mp3", "wav", "m4a") } -> AutonomousInputType.AUDIO_FILE
            extensions.any { it in setOf("mp4", "mkv", "mov") } -> AutonomousInputType.VIDEO_FILE
            extensions.any { it in setOf("png", "jpg", "jpeg", "webp") } -> AutonomousInputType.IMAGE_FILE
            submission.attachedFiles.size > 1 -> AutonomousInputType.MULTI_FILE_PROJECT
            else -> AutonomousInputType.GENERAL_INSTRUCTION
        }

        val combinedText = "${submission.promptOrInstruction} $codeOrScript"
        val langs = mutableListOf<String>()
        if (combinedText.any { it in 'ऀ'..'ॿ' }) langs.add("hi-IN")
        if (combinedText.any { it in 'a'..'z' || it in 'A'..'Z' }) langs.add("en-IN")
        if (langs.isEmpty()) langs.add("en-IN")

        val sentences = combinedText.split(Regex("(?<=[.!?।\n])+")).filter { it.trim().length > 10 }
        val estimatedScenes = sentences.size.coerceIn(3, 12)

        return AnalyzedInputBundle(
            primaryInputType = primaryType,
            detectedLanguages = langs,
            containsAndroidProject = hasAndroidStructure,
            containsBrokenSyntax = hasBrokenBraces,
            detectedFileExtensions = extensions,
            estimatedSceneCount = estimatedScenes,
            totalInputSizeBytes = totalSize,
            summaryDescription = "InputType=$primaryType, Languages=${langs.joinToString("/")}, AndroidProject=$hasAndroidStructure, Scenes=$estimatedScenes"
        )
    }
}
