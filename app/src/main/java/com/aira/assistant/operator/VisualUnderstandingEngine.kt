package com.aira.assistant.operator

import com.aira.assistant.domain.model.UiBounds
import com.aira.assistant.domain.model.UiElementNode
import com.aira.assistant.service.ScreenCaptureService

/**
 * Visual Understanding Engine (`VisualUnderstandingEngine`).
 *
 * Parses visible OCR text lines from `ScreenCaptureService` (MediaProjection + ML Kit Latin/Devanagari OCR)
 * into synthetic `UiElementNode` items (`sourceMethod = "OCR_VISION"`).
 * When an app uses custom OpenGL/Canvas views that do not expose Accessibility nodes,
 * AIRA falls back to these visual OCR nodes to locate buttons, labels, and menus.
 */
class VisualUnderstandingEngine {

    private val simulatedOcrNodes = mutableListOf<UiElementNode>()

    fun setSimulatedOcrNodesForTest(nodes: List<UiElementNode>) {
        simulatedOcrNodes.clear()
        simulatedOcrNodes.addAll(nodes)
    }

    fun extractVisualOcrNodes(): List<UiElementNode> {
        if (simulatedOcrNodes.isNotEmpty()) {
            return simulatedOcrNodes.toList()
        }
        val rawScreenText = ScreenCaptureService.latestExtractedScreenText.value.orEmpty()
        if (rawScreenText.isBlank()) return emptyList()

        val lines = rawScreenText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.mapIndexed { idx, line ->
            val topY = 140 + (idx * 90)
            UiElementNode(
                nodeId = "ocr_node_$idx",
                className = "android.view.View",
                text = line,
                contentDescription = line,
                bounds = UiBounds(left = 80, top = topY, right = 1000, bottom = topY + 64),
                isClickable = true,
                sourceMethod = "OCR_VISION"
            )
        }
    }
}
