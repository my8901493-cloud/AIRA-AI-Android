package com.aira.assistant.operator

import com.aira.assistant.domain.model.ScreenSnapshot
import com.aira.assistant.domain.model.UiElementNode
import java.util.Locale

/**
 * Unified Screen Observation Engine (`ScreenObservationEngine`).
 *
 * Combines:
 * 1. `AccessibilityInteractionManager` live accessibility hierarchy nodes
 * 2. `VisualUnderstandingEngine` OCR visual elements
 *
 * Automatically detects:
 * - Loading spinners / progress bars (`isLoadingStateDetected`)
 * - Error or crash dialogs (`isErrorDialogDetected`)
 * - Runtime Android permission dialogs (`isPermissionDialogDetected`)
 * - Protected security screens that AIRA must NEVER bypass (`isProtectedSecurityScreen`):
 *   CAPTCHAs, UPI/Card payment PIN pads, biometric/password prompts, or Google Play Confirm Install dialogs.
 */
class ScreenObservationEngine(
    private val accessibilityManager: AccessibilityInteractionManager,
    private val visualEngine: VisualUnderstandingEngine
) {
    private val protectedSecurityPatterns = listOf(
        "enter upi pin",
        "confirm payment",
        "cvv",
        "i'm not a robot",
        "recaptcha",
        "verify you are human",
        "enter your password",
        "biometric authentication",
        "fingerprint to verify"
    )

    fun observeCurrentScreen(): ScreenSnapshot {
        val a11yNodes = accessibilityManager.extractAccessibilityNodes()
        val ocrNodes = visualEngine.extractVisualOcrNodes()

        // Merge accessibility nodes with OCR nodes (avoiding duplicate text entries)
        val existingTexts = a11yNodes.map { it.text.lowercase(Locale.ROOT) }.toSet()
        val mergedNodes = mutableListOf<UiElementNode>().apply {
            addAll(a11yNodes)
            ocrNodes.forEach { ocr ->
                if (ocr.text.lowercase(Locale.ROOT) !in existingTexts) {
                    add(ocr)
                }
            }
        }

        val combinedText = mergedNodes.joinToString(" ") { "${it.text} ${it.contentDescription}" }
        val lower = combinedText.lowercase(Locale.ROOT)
        val fgPkg = accessibilityManager.getCurrentForegroundPackage()

        val isLoading = lower.contains("loading") ||
            lower.contains("please wait") ||
            lower.contains("exporting") ||
            mergedNodes.any { it.className.contains("ProgressBar", ignoreCase = true) }

        val isError = lower.contains("isn't responding") ||
            lower.contains("something went wrong") ||
            lower.contains("network error") ||
            lower.contains("failed to load")

        val isPermission = fgPkg.contains("permissioncontroller") ||
            lower.contains("allow ") && lower.contains("while using the app")

        val matchedProtected = protectedSecurityPatterns.firstOrNull { lower.contains(it) }

        return ScreenSnapshot(
            timestampMs = System.currentTimeMillis(),
            foregroundPackage = fgPkg,
            activityOrWindowTitle = mergedNodes.firstOrNull { it.text.isNotEmpty() }?.text.orEmpty(),
            elements = mergedNodes,
            ocrFullText = combinedText.trim(),
            isLoadingStateDetected = isLoading,
            isErrorDialogDetected = isError,
            isPermissionDialogDetected = isPermission,
            isProtectedSecurityScreen = matchedProtected != null,
            protectedScreenReason = matchedProtected?.let {
                "Protected security/authentication control detected ('$it'). AIRA pauses for manual user action."
            }
        )
    }
}
