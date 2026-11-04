package com.aira.assistant.operator

import com.aira.assistant.domain.model.ScreenSnapshot

/**
 * High-level Browser Automation Engine (`BrowserAutomationEngine`).
 *
 * Operates Chrome or any installed browser through visible UI interactions
 * (address bar navigation, search queries, result clicking, scrolling, and content extraction)
 * without depending solely on closed third-party APIs.
 */
class BrowserAutomationEngine(
    private val websiteManager: WebsiteInteractionManager,
    private val deviceController: AndroidDeviceController
) {
    fun searchAndExtractWebTopic(topic: String): Result<String> {
        val screenResult = websiteManager.performGoogleSearchInBrowser(topic)
        return screenResult.map { snapshot ->
            val extracted = snapshot.elements
                .map { it.text.trim() }
                .filter { it.length > 20 && !it.startsWith("https://") }
                .joinToString("
• ")
            "Browser Research Summary for '$topic':
• $extracted"
        }
    }

    fun openWebsiteAndAdaptToLayout(url: String, targetActionLabel: String? = null): Result<ScreenSnapshot> {
        val navResult = websiteManager.navigateToUrlAndInspect(url)
        if (navResult.isFailure) return navResult

        if (!targetActionLabel.isNullOrBlank()) {
            return deviceController.findAndTapElement(targetActionLabel, allowScrollSearch = true)
        }
        return navResult
    }
}
