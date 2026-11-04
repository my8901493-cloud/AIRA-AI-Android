package com.aira.assistant.mobile

import com.aira.assistant.domain.model.ScreenSnapshot
import com.aira.assistant.domain.model.UiBounds
import kotlinx.serialization.Serializable

@Serializable
data class GroundedScreenMark(
    val markIndex: Int, // [1], [2], [3]...
    val label: String,
    val actionType: String, // CLICKABLE, EDITABLE, SCROLLABLE
    val bounds: UiBounds,
    val sourceMethod: String
)

@Serializable
data class ScreenDiffResult(
    val screenChangedAfterAction: Boolean,
    val newElementsAppeared: Int,
    val foregroundPackageChanged: Boolean,
    val verificationNote: String
)

/**
 * Set-of-Mark Visual Element Grounding & Closed-Loop Screen Diff Verifier (`ScreenDiffAndGroundingEngine`).
 *
 * 1. Assigns every interactive UI element on the current screen a deterministic index tag (`[1]`, `[2]`, `[3]`)
 *    with exact center coordinates `(centerX, centerY)` so the planner never guesses raw pixel coordinates.
 * 2. Compares the `before` and `after` `ScreenSnapshot` following every tap/scroll/type action
 *    to verify that the screen actually responded before moving to the next step.
 */
class ScreenDiffAndGroundingEngine {

    fun buildSetOfMarkTags(snapshot: ScreenSnapshot): List<GroundedScreenMark> {
        val interactive = snapshot.elements.filter {
            it.isClickable || it.isEditable || it.isScrollable || it.text.isNotBlank() || it.contentDescription.isNotBlank()
        }
        return interactive.mapIndexed { idx, node ->
            val label = node.text.ifBlank { node.contentDescription.ifBlank { node.viewIdResourceName ?: node.className } }
            val actionType = when {
                node.isEditable -> "EDITABLE"
                node.isScrollable -> "SCROLLABLE"
                else -> "CLICKABLE"
            }
            GroundedScreenMark(
                markIndex = idx + 1,
                label = label.take(64),
                actionType = actionType,
                bounds = node.bounds,
                sourceMethod = node.sourceMethod
            )
        }
    }

    fun formatGroundedPromptView(marks: List<GroundedScreenMark>): String {
        if (marks.isEmpty()) return "[No interactive elements detected on screen]"
        return marks.joinToString("
") { m ->
            "[${m.markIndex}] (${m.actionType}) "${m.label}" @ (${m.bounds.centerX}, ${m.bounds.centerY}) via ${m.sourceMethod}"
        }
    }

    fun verifyScreenChanged(before: ScreenSnapshot, after: ScreenSnapshot): ScreenDiffResult {
        val pkgChanged = before.foregroundPackage != after.foregroundPackage
        val beforeLabels = before.elements.map { "${it.text}|${it.bounds.centerY}" }.toSet()
        val afterLabels = after.elements.map { "${it.text}|${it.bounds.centerY}" }.toSet()

        val newlyAdded = (afterLabels - beforeLabels).size
        val changed = pkgChanged || newlyAdded > 0 || before.ocrFullText != after.ocrFullText

        return ScreenDiffResult(
            screenChangedAfterAction = changed,
            newElementsAppeared = newlyAdded,
            foregroundPackageChanged = pkgChanged,
            verificationNote = if (changed) {
                "Verified screen transition (pkgChanged=$pkgChanged, newElements=$newlyAdded)"
            } else {
                "Screen did not change after action; triggering scroll or fallback OCR lookup"
            }
        )
    }
}
