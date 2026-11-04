package com.aira.assistant.autonomous

import com.aira.assistant.domain.model.AutonomousToolMetadata
import com.aira.assistant.domain.model.ToolCostTier

/**
 * Autonomous Tool Registry (`ToolRegistry`).
 *
 * Registers every local, open-source, and cloud build/media/code tool with full metadata:
 * purpose, supported input/output formats, capabilities, cost tier, API requirements,
 * local vs. cloud execution, hardware requirements, and limitations.
 */
class ToolRegistry {

    private val registeredTools = mutableListOf(
        // 1. Code Inspection, Repair & APK Build Tools
        AutonomousToolMetadata(
            toolId = "local-kotlin-cpp-ast-inspector",
            name = "AIRA Local Kotlin/Java/C++ AST & Gradle Inspector",
            purpose = "Inspects Android project structure, Gradle catalogs, Manifests, and repairs syntax/import/dependency errors locally.",
            supportedInputFormats = listOf("kt", "java", "kts", "xml", "cpp", "toml", "zip"),
            supportedOutputFormats = listOf("kt", "java", "kts", "xml", "cpp", "report.json"),
            capabilities = listOf("PROJECT_INSPECTION", "CODE_REPAIR", "DEPENDENCY_RESOLUTION"),
            costTier = ToolCostTier.FREE_LOCAL,
            isLocalExecution = true,
            minRamMb = 256,
            qualityScore = 0.95f,
            reliabilityScore = 0.98f,
            speedScore = 0.96f,
            privacyScore = 1.0f,
            limitations = "Static & structural repair locally; full Android DEX packaging requires SDK or GitHub Actions runner."
        ),
        AutonomousToolMetadata(
            toolId = "local-gradle-android-sdk-builder",
            name = "Local Gradle & Android SDK APK Builder",
            purpose = "Runs ./gradlew testDebugUnitTest assembleDebug locally when Android SDK and JDK 17 are installed on host.",
            supportedInputFormats = listOf("android-project", "kt", "java", "xml"),
            supportedOutputFormats = listOf("apk", "test-report.xml"),
            capabilities = listOf("APK_BUILD", "UNIT_TEST_EXECUTION"),
            costTier = ToolCostTier.FREE_LOCAL,
            isLocalExecution = true,
            minRamMb = 2048,
            qualityScore = 0.98f,
            reliabilityScore = 0.95f,
            speedScore = 0.90f,
            privacyScore = 1.0f,
            limitations = "Requires local Android SDK (ANDROID_HOME) and javac/kotlinc binary."
        ),
        AutonomousToolMetadata(
            toolId = "github-actions-cloud-apk-builder",
            name = "GitHub Actions Cloud Android CI Builder",
            purpose = "Free-first cloud Android build integration: uploads verified branch/workflow, triggers ubuntu-latest Gradle build, diagnoses compilation logs, and retrieves the signed Debug APK artifact.",
            supportedInputFormats = listOf("android-project", "git-repo", "zip"),
            supportedOutputFormats = listOf("apk", "build-log.txt"),
            capabilities = listOf("APK_BUILD", "CLOUD_CI_BUILD", "UNIT_TEST_EXECUTION"),
            costTier = ToolCostTier.FREE_CLOUD_CI,
            requiresApiKey = false,
            isLocalExecution = false,
            minRamMb = 128,
            qualityScore = 0.97f,
            reliabilityScore = 0.96f,
            speedScore = 0.82f,
            privacyScore = 0.90f,
            limitations = "Requires network connectivity and authorized GitHub repository token for remote trigger."
        ),

        // 2. Script Analysis & Storyboard Generation Tools
        AutonomousToolMetadata(
            toolId = "aira-local-storyboard-director",
            name = "AIRA Local Script-to-Storyboard Director",
            purpose = "Analyzes Hindi, Hinglish, and English scripts into timed scenes, characters, locations, emotions, visual prompts, and audio cues.",
            supportedInputFormats = listOf("txt", "md", "script"),
            supportedOutputFormats = listOf("storyboard.json", "srt"),
            capabilities = listOf("SCRIPT_ANALYSIS", "STORYBOARD_GENERATION", "SUBTITLE_TIMING"),
            costTier = ToolCostTier.FREE_LOCAL,
            isLocalExecution = true,
            minRamMb = 256,
            qualityScore = 0.94f,
            reliabilityScore = 0.99f,
            speedScore = 0.97f,
            privacyScore = 1.0f
        ),

        // 3. Visual Scene Generation Tools
        AutonomousToolMetadata(
            toolId = "aira-procedural-cinematic-canvas-renderer",
            name = "AIRA Procedural Cinematic Scene & Motion Graphics Renderer",
            purpose = "Generates watermark-free HD scene visuals, animated camera pans/zooms (Ken Burns), HUD motion graphics, and Devanagari/Latin typography frames in 16:9 and 9:16.",
            supportedInputFormats = listOf("storyboard.json", "png", "jpg"),
            supportedOutputFormats = listOf("png", "raw-frames"),
            capabilities = listOf("SCENE_VISUAL_GENERATION", "MOTION_GRAPHICS", "SUBTITLE_OVERLAY"),
            costTier = ToolCostTier.FREE_LOCAL,
            isLocalExecution = true,
            minRamMb = 512,
            qualityScore = 0.93f,
            reliabilityScore = 0.99f,
            speedScore = 0.92f,
            privacyScore = 1.0f
        ),

        // 4. Voice Narration & Audio Production Tools
        AutonomousToolMetadata(
            toolId = "aira-neural-tts-narrator",
            name = "AIRA Bilingual Voiceover Synthesizer (Android Neural / Piper / Formant DSP)",
            purpose = "Synthesizes segmented Hindi, Hinglish, and English voiceover narration with natural breathing pauses and prosody.",
            supportedInputFormats = listOf("txt", "script", "storyboard.json"),
            supportedOutputFormats = listOf("wav", "pcm"),
            capabilities = listOf("VOICE_SYNTHESIS", "BILINGUAL_TTS"),
            costTier = ToolCostTier.FREE_LOCAL,
            isLocalExecution = true,
            minRamMb = 256,
            qualityScore = 0.94f,
            reliabilityScore = 0.98f,
            speedScore = 0.95f,
            privacyScore = 1.0f
        ),
        AutonomousToolMetadata(
            toolId = "aira-procedural-soundscape-mixer",
            name = "AIRA Procedural Music, SFX & Ducking Audio Mixer",
            purpose = "Generates ambient background score, transition whooshes, and emotional audio cues; mixes with voiceover using automatic sidechain ducking (-14dB under voice) and peak normalization.",
            supportedInputFormats = listOf("wav", "pcm"),
            supportedOutputFormats = listOf("wav", "mp3", "aac"),
            capabilities = listOf("MUSIC_SOUND_DESIGN", "AUDIO_MIXING", "AUDIO_MASTERING"),
            costTier = ToolCostTier.FREE_LOCAL,
            isLocalExecution = true,
            minRamMb = 256,
            qualityScore = 0.95f,
            reliabilityScore = 0.99f,
            speedScore = 0.95f,
            privacyScore = 1.0f
        ),

        // 5. Video Editing & Encoding Tools
        AutonomousToolMetadata(
            toolId = "ffmpeg-mediacodec-video-composer",
            name = "FFmpeg / Android MediaCodec + MediaMuxer Video Editor",
            purpose = "Combines scene visuals, synchronized narration, background score, cinematic color grading, crossfade transitions, and burned-in subtitles into an H.264/AAC MP4.",
            supportedInputFormats = listOf("png", "wav", "mp3", "srt"),
            supportedOutputFormats = listOf("mp4"),
            capabilities = listOf("VIDEO_EDITING", "MP4_ENCODING"),
            costTier = ToolCostTier.FREE_LOCAL,
            isLocalExecution = true,
            minRamMb = 512,
            qualityScore = 0.97f,
            reliabilityScore = 0.98f,
            speedScore = 0.91f,
            privacyScore = 1.0f
        ),

        // 6. Secure Workspace & Archive Packaging Tool
        AutonomousToolMetadata(
            toolId = "aira-workspace-packager",
            name = "AIRA Versioned Workspace & ZIP Packager",
            purpose = "Manages isolated task sandboxes, atomic file backups, rollback on interruption, and verified ZIP archives.",
            supportedInputFormats = listOf("*"),
            supportedOutputFormats = listOf("zip"),
            capabilities = listOf("WORKSPACE_MANAGEMENT", "ZIP_PACKAGING"),
            costTier = ToolCostTier.FREE_LOCAL,
            isLocalExecution = true,
            minRamMb = 128,
            qualityScore = 0.99f,
            reliabilityScore = 0.99f,
            speedScore = 0.99f,
            privacyScore = 1.0f
        )
    )

    fun getAllTools(): List<AutonomousToolMetadata> = registeredTools.toList()

    fun findToolsByCapability(capability: String): List<AutonomousToolMetadata> =
        registeredTools.filter { capability in it.capabilities }
}
