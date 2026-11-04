package com.aira.assistant.mobile

import com.aira.assistant.operator.AccessibilityInteractionManager
import kotlinx.serialization.Serializable

@Serializable
data class RecordedMacroStep(
    val stepOrder: Int,
    val actionType: String, // TAP_TEXT, TAP_COORDS, TYPE_TEXT, SCROLL_DOWN, LAUNCH_APP
    val targetValue: String,
    val x: Int = 0,
    val y: Int = 0
)

@Serializable
data class SavedUserMacro(
    val macroName: String,
    val triggerPhrase: String,
    val steps: List<RecordedMacroStep>,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * "Show Once, Repeat Forever" Custom Macro & Routine Recorder (`MacroRoutineRecorder`).
 *
 * Allows the user to record a multi-step sequence of taps, text inputs, and scrolls once,
 * name it with a voice trigger phrase (e.g., *"Subah ka routine chala do"* or *"Export daily report"*),
 * and replay the entire sequence autonomously on command.
 */
class MacroRoutineRecorder(
    private val accessibilityManager: AccessibilityInteractionManager
) {
    private val savedMacros = mutableMapOf<String, SavedUserMacro>()
    private var activeRecordingSteps = mutableListOf<RecordedMacroStep>()
    var isRecording: Boolean = false
        private set

    fun startRecording() {
        activeRecordingSteps.clear()
        isRecording = true
    }

    fun recordStep(actionType: String, targetValue: String, x: Int = 0, y: Int = 0) {
        if (!isRecording) return
        activeRecordingSteps.add(
            RecordedMacroStep(
                stepOrder = activeRecordingSteps.size + 1,
                actionType = actionType,
                targetValue = targetValue,
                x = x,
                y = y
            )
        )
    }

    fun stopAndSaveMacro(macroName: String, triggerPhrase: String): SavedUserMacro {
        isRecording = false
        val macro = SavedUserMacro(
            macroName = macroName,
            triggerPhrase = triggerPhrase.lowercase().trim(),
            steps = activeRecordingSteps.toList()
        )
        savedMacros[macro.triggerPhrase] = macro
        return macro
    }

    fun replayMacroByPhrase(phrase: String): Result<Int> {
        val key = phrase.lowercase().trim()
        val macro = savedMacros.entries.firstOrNull { key.contains(it.key) || it.key.contains(key) }?.value
            ?: return Result.failure(NoSuchElementException("No saved macro matching '$phrase'"))

        var executedCount = 0
        for (step in macro.steps) {
            if (accessibilityManager.isEmergencyStopped) break
            when (step.actionType) {
                "TAP_TEXT" -> accessibilityManager.tapElementByTextOrDescription(step.targetValue)
                "TAP_COORDS" -> accessibilityManager.tapCoordinates(step.x, step.y)
                "TYPE_TEXT" -> accessibilityManager.typeTextIntoFocusedOrMatchedField(step.targetValue)
                "SCROLL_DOWN" -> accessibilityManager.scrollVertical(forward = true)
            }
            executedCount++
        }
        return Result.success(executedCount)
    }

    fun getAllSavedMacros(): List<SavedUserMacro> = savedMacros.values.toList()
}
