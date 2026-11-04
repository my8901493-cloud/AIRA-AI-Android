package com.aira.assistant.operator

import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.ScreenSnapshot
import com.aira.assistant.domain.model.UiElementNode

/**
 * Real-World Human-Like Android Device Controller (`AndroidDeviceController`).
 *
 * Never assumes screen coordinates stay static:
 * 1. Observes a fresh `ScreenSnapshot` before every action.
 * 2. Refuses to interact if a protected security screen (CAPTCHA, payment confirmation, password/PIN)
 *    is on screen, returning control to the user immediately.
 * 3. Tries Accessibility Node matching first; if the node is not exposed in the Accessibility tree,
 *    automatically falls back to `VisualUnderstandingEngine` OCR coordinates.
 * 4. Re-evaluates the screen after navigation, scrolling, or layout changes.
 */
class AndroidDeviceController(
    val accessibilityManager: AccessibilityInteractionManager,
    val visualEngine: VisualUnderstandingEngine,
    val observationEngine: ScreenObservationEngine = ScreenObservationEngine(accessibilityManager, visualEngine)
) {
    fun captureAndUnderstandScreen(): ScreenSnapshot {
        return observationEngine.observeCurrentScreen()
    }

    /**
     * Finds and taps a UI control by label/description using Accessibility first, then Visual OCR fallback,
     * and optionally scrolls down once if the element is below the fold.
     */
    fun findAndTapElement(
        targetLabel: String,
        allowScrollSearch: Boolean = true
    ): Result<ScreenSnapshot> {
        if (accessibilityManager.isEmergencyStopped) {
            return Result.failure(IllegalStateException("Screen operation halted by user Emergency Stop."))
        }

        var snapshot = observationEngine.observeCurrentScreen()
        if (snapshot.isProtectedSecurityScreen) {
            return Result.failure(SecurityException(snapshot.protectedScreenReason ?: "Protected screen detected."))
        }

        val node = findMatchingNode(snapshot.elements, targetLabel)
        if (node != null) {
            AiraLogger.i(
                "AndroidDeviceController",
                "Tapping '${node.text.ifEmpty { node.contentDescription }}' via ${node.sourceMethod} at (${node.bounds.centerX}, ${node.bounds.centerY})"
            )
            accessibilityManager.tapCoordinates(node.bounds.centerX, node.bounds.centerY)
            return Result.success(observationEngine.observeCurrentScreen())
        }

        if (allowScrollSearch) {
            accessibilityManager.scrollVertical(forward = true)
            snapshot = observationEngine.observeCurrentScreen()
            val afterScrollNode = findMatchingNode(snapshot.elements, targetLabel)
            if (afterScrollNode != null) {
                accessibilityManager.tapCoordinates(afterScrollNode.bounds.centerX, afterScrollNode.bounds.centerY)
                return Result.success(observationEngine.observeCurrentScreen())
            }
        }

        return Result.failure(NoSuchElementException("UI element '$targetLabel' not found on current screen."))
    }

    fun typeTextInField(text: String, fieldLabelHint: String? = null): Result<ScreenSnapshot> {
        if (accessibilityManager.isEmergencyStopped) {
            return Result.failure(IllegalStateException("Screen operation halted by user Emergency Stop."))
        }
        val snapshot = observationEngine.observeCurrentScreen()
        if (snapshot.isProtectedSecurityScreen) {
            return Result.failure(SecurityException(snapshot.protectedScreenReason ?: "Protected screen detected."))
        }

        if (fieldLabelHint != null) {
            findMatchingNode(snapshot.elements, fieldLabelHint)?.let { fieldNode ->
                accessibilityManager.tapCoordinates(fieldNode.bounds.centerX, fieldNode.bounds.centerY)
            }
        }
        val typed = accessibilityManager.typeTextIntoFocusedOrMatchedField(text, fieldLabelHint)
        return if (typed) {
            Result.success(observationEngine.observeCurrentScreen())
        } else {
            Result.failure(IllegalStateException("Failed to type text into field '${fieldLabelHint.orEmpty()}'."))
        }
    }

    fun stopImmediately() {
        accessibilityManager.triggerEmergencyStop()
    }

    private fun findMatchingNode(nodes: List<UiElementNode>, query: String): UiElementNode? {
        // Prefer ACCESSIBILITY_NODE first, then OCR_VISION fallback
        val a11yHit = nodes.firstOrNull {
            it.sourceMethod == "ACCESSIBILITY_NODE" && (
                it.text.contains(query, ignoreCase = true) ||
                    it.contentDescription.contains(query, ignoreCase = true) ||
                    (it.viewIdResourceName?.contains(query, ignoreCase = true) == true)
                )
        }
        if (a11yHit != null) return a11yHit

        return nodes.firstOrNull {
            it.sourceMethod == "OCR_VISION" && (
                it.text.contains(query, ignoreCase = true) ||
                    it.contentDescription.contains(query, ignoreCase = true)
                )
        }
    }
}
