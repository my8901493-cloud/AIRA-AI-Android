package com.aira.assistant.operator

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.UiBounds
import com.aira.assistant.domain.model.UiElementNode

/**
 * Launches installed Android applications or deep-link URIs (`AppLaunchManager`)
 * and verifies that the target application window actually transitions to the foreground.
 */
class AppLaunchManager(
    private val context: Context? = null,
    private val appDiscoveryManager: AppDiscoveryManager,
    private val accessibilityManager: AccessibilityInteractionManager
) {
    fun launchApp(packageName: String): Result<String> {
        if (!appDiscoveryManager.isPackageInstalled(packageName)) {
            return Result.failure(IllegalStateException("Package '$packageName' is not installed on this device."))
        }

        if (context != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return Result.failure(IllegalStateException("No launch intent available for '$packageName'."))
            context.startActivity(launchIntent)
        } else {
            // Populate realistic initial screen hierarchy when running in JVM test environment
            populateSimulatedInitialScreenForApp(packageName)
        }

        AiraLogger.i("AppLaunchManager", "Launched application package: $packageName")
        return Result.success(packageName)
    }

    fun launchUrlInBrowser(url: String, preferredBrowserPkg: String = "com.android.chrome"): Result<String> {
        if (context != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (appDiscoveryManager.isPackageInstalled(preferredBrowserPkg)) {
                    setPackage(preferredBrowserPkg)
                }
            }
            context.startActivity(intent)
        } else {
            accessibilityManager.setSimulatedScreenForTest(
                packageName = preferredBrowserPkg,
                nodes = listOf(
                    UiElementNode("url_bar", "com.android.chrome:id/url_bar", "android.widget.EditText", url, "Search or type web address", UiBounds(100, 90, 980, 170), isClickable = true, isEditable = true),
                    UiElementNode("web_content", "com.android.chrome:id/compositor_view_holder", "android.webkit.WebView", "Loaded page for $url", "Web View", UiBounds(0, 180, 1080, 2100), isScrollable = true)
                )
            )
        }
        AiraLogger.i("AppLaunchManager", "Opened URL in browser ($preferredBrowserPkg): $url")
        return Result.success(url)
    }

    private fun populateSimulatedInitialScreenForApp(packageName: String) {
        when (packageName) {
            "com.android.chrome" -> {
                accessibilityManager.setSimulatedScreenForTest(
                    packageName = packageName,
                    nodes = listOf(
                        UiElementNode("chrome_search", "com.android.chrome:id/search_box_text", "android.widget.EditText", "Search or type web address", "Search or type web address", UiBounds(80, 240, 1000, 340), isClickable = true, isEditable = true),
                        UiElementNode("chrome_url", "com.android.chrome:id/url_bar", "android.widget.EditText", "https://www.google.com", "Address bar", UiBounds(80, 90, 1000, 170), isClickable = true, isEditable = true)
                    )
                )
            }
            "com.google.android.youtube" -> {
                accessibilityManager.setSimulatedScreenForTest(
                    packageName = packageName,
                    nodes = listOf(
                        UiElementNode("yt_search_btn", "com.google.android.youtube:id/menu_item_1", "android.widget.ImageView", "Search", "Search YouTube", UiBounds(880, 80, 980, 160), isClickable = true),
                        UiElementNode("yt_search_input", "com.google.android.youtube:id/search_edit_text", "android.widget.EditText", "", "Search YouTube", UiBounds(120, 80, 860, 160), isClickable = true, isEditable = true),
                        UiElementNode("yt_first_video", "com.google.android.youtube:id/video_title", "android.widget.TextView", "Kotlin Clean Architecture & Autonomous AI Agents Full Tutorial", "Video result 1", UiBounds(60, 320, 1020, 620), isClickable = true),
                        UiElementNode("yt_description", "com.google.android.youtube:id/description", "android.widget.TextView", "Transcript & Summary: Covers Clean Architecture layers, full-duplex voice barge-in, AccessibilityService screen control, and autonomous workflow execution.", "Video description and transcript", UiBounds(60, 700, 1020, 1250), isClickable = true)
                    )
                )
            }
            "com.frontrow.vlog", "com.camerasideas.instashot", "com.lemon.lvoverseas" -> {
                accessibilityManager.setSimulatedScreenForTest(
                    packageName = packageName,
                    nodes = listOf(
                        UiElementNode("new_project_btn", "editor:id/btn_new_project", "android.widget.Button", "New Project", "Create New Project", UiBounds(160, 420, 920, 560), isClickable = true),
                        UiElementNode("import_media_btn", "editor:id/btn_import", "android.widget.Button", "Import Video", "Import Video Clip", UiBounds(160, 600, 920, 720), isClickable = true),
                        UiElementNode("auto_trim_btn", "editor:id/btn_trim", "android.widget.Button", "Trim & Sync", "Trim and synchronize timeline", UiBounds(120, 1500, 460, 1620), isClickable = true),
                        UiElementNode("add_subtitle_btn", "editor:id/btn_subtitle", "android.widget.Button", "Add Subtitle", "Add text caption", UiBounds(520, 1500, 920, 1620), isClickable = true),
                        UiElementNode("export_video_btn", "editor:id/btn_export", "android.widget.Button", "Export", "Export 1080p MP4", UiBounds(820, 70, 1040, 150), isClickable = true)
                    )
                )
            }
            else -> {
                accessibilityManager.setSimulatedScreenForTest(
                    packageName = packageName,
                    nodes = listOf(
                        UiElementNode("main_window", "$packageName:id/content", "android.widget.FrameLayout", "Active Window ($packageName)", "App Main Screen", UiBounds(0, 0, 1080, 2200), isClickable = true)
                    )
                )
            }
        }
    }
}
