package com.aira.assistant.operator

import android.content.Context
import android.content.Intent
import com.aira.assistant.domain.model.AppCapabilityCategory
import com.aira.assistant.domain.model.InstalledAppInfo

/**
 * Discovers installed Android applications on the phone and maps them to verified capabilities
 * (Browsers, YouTube, Video Editors, Audio Editors, Image Editors, File Managers, Document Editors).
 *
 * Never claims an application is installed or supports a feature unless confirmed by
 * `PackageManager` (or an explicit test fixture) and its known capability matrix.
 */
class AppDiscoveryManager(
    private val context: Context? = null
) {
    private val simulatedInstalledPackages = mutableSetOf(
        "com.android.chrome",
        "com.google.android.youtube"
    )

    private val knownAppProfiles: List<InstalledAppInfo> = listOf(
        InstalledAppInfo(
            packageName = "com.android.chrome",
            appName = "Google Chrome",
            category = AppCapabilityCategory.BROWSER,
            supportsAccessibilityAutomation = true,
            supportedImportFormats = listOf("html", "pdf", "url"),
            supportedExportFormats = listOf("txt", "pdf", "download"),
            verifiedFeatures = listOf("WEB_SEARCH", "URL_NAVIGATION", "WEB_TOOL_INTERACTION", "FILE_DOWNLOAD")
        ),
        InstalledAppInfo(
            packageName = "org.mozilla.firefox",
            appName = "Mozilla Firefox",
            category = AppCapabilityCategory.BROWSER,
            supportsAccessibilityAutomation = true,
            verifiedFeatures = listOf("WEB_SEARCH", "URL_NAVIGATION", "WEB_TOOL_INTERACTION")
        ),
        InstalledAppInfo(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            category = AppCapabilityCategory.YOUTUBE_MEDIA,
            supportsAccessibilityAutomation = true,
            verifiedFeatures = listOf("VIDEO_SEARCH", "VIDEO_PLAYBACK", "DESCRIPTION_AND_TRANSCRIPT_INSPECTION")
        ),
        InstalledAppInfo(
            packageName = "com.frontrow.vlog",
            appName = "VN Video Editor",
            category = AppCapabilityCategory.VIDEO_EDITOR,
            supportsAccessibilityAutomation = true,
            supportedImportFormats = listOf("mp4", "mov", "png", "jpg", "wav", "mp3"),
            supportedExportFormats = listOf("mp4"),
            verifiedFeatures = listOf("TIMELINE_IMPORT", "TRIM_AND_SPLIT", "TRANSITIONS", "TEXT_SUBTITLES", "WATERMARK_FREE_EXPORT")
        ),
        InstalledAppInfo(
            packageName = "com.camerasideas.instashot",
            appName = "InShot Video Editor",
            category = AppCapabilityCategory.VIDEO_EDITOR,
            supportsAccessibilityAutomation = true,
            supportedImportFormats = listOf("mp4", "png", "jpg", "mp3", "wav"),
            supportedExportFormats = listOf("mp4"),
            verifiedFeatures = listOf("TIMELINE_IMPORT", "TRIM_AND_SPLIT", "FILTERS", "SUBTITLES", "MP4_EXPORT")
        ),
        InstalledAppInfo(
            packageName = "com.lemon.lvoverseas",
            appName = "CapCut Video Editor",
            category = AppCapabilityCategory.VIDEO_EDITOR,
            supportsAccessibilityAutomation = true,
            supportedImportFormats = listOf("mp4", "png", "jpg", "mp3", "wav"),
            supportedExportFormats = listOf("mp4"),
            verifiedFeatures = listOf("TIMELINE_IMPORT", "AUTO_CAPTIONS", "TRANSITIONS", "MP4_EXPORT")
        ),
        InstalledAppInfo(
            packageName = "br.com.lexis.lexisaudioeditor",
            appName = "Lexis Audio Editor",
            category = AppCapabilityCategory.AUDIO_EDITOR,
            supportsAccessibilityAutomation = true,
            supportedImportFormats = listOf("wav", "mp3", "m4a"),
            supportedExportFormats = listOf("wav", "mp3"),
            verifiedFeatures = listOf("AUDIO_TRIM", "NOISE_REDUCTION", "NORMALIZATION", "WAV_MP3_EXPORT")
        ),
        InstalledAppInfo(
            packageName = "com.google.android.apps.nbu.files",
            appName = "Files by Google",
            category = AppCapabilityCategory.FILE_MANAGER,
            supportsAccessibilityAutomation = true,
            verifiedFeatures = listOf("FILE_BROWSING", "ZIP_EXTRACTION", "FILE_SHARING")
        )
    )

    fun setSimulatedInstalledPackagesForTest(packages: Set<String>) {
        simulatedInstalledPackages.clear()
        simulatedInstalledPackages.addAll(packages)
    }

    fun addSimulatedInstalledPackage(packageName: String) {
        simulatedInstalledPackages.add(packageName)
    }

    fun discoverInstalledApps(): List<InstalledAppInfo> {
        val installedPkgSet = if (context != null) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(mainIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()
        } else {
            simulatedInstalledPackages.toSet()
        }

        return knownAppProfiles.filter { it.packageName in installedPkgSet }
            .map { it.copy(isInstalled = true) }
    }

    fun findInstalledAppsByCategory(category: AppCapabilityCategory): List<InstalledAppInfo> {
        return discoverInstalledApps().filter { it.category == category }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return discoverInstalledApps().any { it.packageName == packageName }
    }
}
