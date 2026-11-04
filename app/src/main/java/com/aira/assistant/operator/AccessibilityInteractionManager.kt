package com.aira.assistant.operator

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.UiBounds
import com.aira.assistant.domain.model.UiElementNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Authorized Android Accessibility Service (`AiraAccessibilityService`).
 *
 * Provides human-like UI inspection and touch/gesture execution ONLY when enabled by the user
 * in Android Accessibility Settings and while `emergencyStopActive` is false.
 */
class AiraAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var activeInstance: AiraAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _foregroundPackage = MutableStateFlow("")
        val foregroundPackage: StateFlow<String> = _foregroundPackage.asStateFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        _isServiceConnected.value = true
        AiraLogger.i("AiraAccessibilityService", "Accessibility Service connected and ready.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty()) {
            _foregroundPackage.value = pkg
        }
    }

    override fun onInterrupt() {
        AiraLogger.w("AiraAccessibilityService", "Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        activeInstance = null
        _isServiceConnected.value = false
        super.onDestroy()
    }
}

/**
 * Human-Like Screen & Accessibility Interaction Manager (`AccessibilityInteractionManager`).
 *
 * Supports:
 * - Traversing live `AccessibilityNodeInfo` hierarchies into structured `UiElementNode` items
 * - Tapping identified nodes (`ACTION_CLICK`) or exact `(x, y)` coordinates (`GestureDescription`)
 * - Long-pressing coordinates or elements
 * - Swiping vertically / horizontally and scrolling (`ACTION_SCROLL_FORWARD` / `GestureDescription`)
 * - Typing text (`ACTION_SET_TEXT`) and clipboard copy/paste (`ACTION_PASTE`)
 * - Navigating Back, Home, and Recents (`GLOBAL_ACTION_BACK`, `GLOBAL_ACTION_HOME`, `GLOBAL_ACTION_RECENTS`)
 * - Immediate Emergency Stop (`triggerEmergencyStop()`) that halts all touch/gesture dispatch immediately
 */
class AccessibilityInteractionManager(
    private val context: Context? = null
) {
    private val emergencyStopFlag = AtomicBoolean(false)

    // Simulated screen nodes for deterministic host/JVM testing when not attached to physical Android WindowManager
    private val simulatedScreenNodes = mutableListOf<UiElementNode>()
    var simulatedForegroundPackage: String = "com.aira.assistant"
    private val interactionTrace = mutableListOf<String>()

    val isEmergencyStopped: Boolean
        get() = emergencyStopFlag.get()

    fun triggerEmergencyStop() {
        emergencyStopFlag.set(true)
        AiraLogger.w("AccessibilityInteractionManager", "EMERGENCY STOP activated: halting all screen interactions immediately.")
    }

    fun resetEmergencyStop() {
        emergencyStopFlag.set(false)
    }

    fun setSimulatedScreenForTest(packageName: String, nodes: List<UiElementNode>) {
        simulatedForegroundPackage = packageName
        simulatedScreenNodes.clear()
        simulatedScreenNodes.addAll(nodes)
    }

    fun getInteractionTrace(): List<String> = interactionTrace.toList()

    fun isAccessibilityReady(): Boolean {
        return AiraAccessibilityService.activeInstance != null || simulatedScreenNodes.isNotEmpty()
    }

    fun getCurrentForegroundPackage(): String {
        val livePkg = AiraAccessibilityService.foregroundPackage.value
        return if (livePkg.isNotEmpty()) livePkg else simulatedForegroundPackage
    }

    fun extractAccessibilityNodes(): List<UiElementNode> {
        if (emergencyStopFlag.get()) return emptyList()
        val service = AiraAccessibilityService.activeInstance
        val root = service?.rootInActiveWindow
        if (root != null) {
            val result = mutableListOf<UiElementNode>()
            traverseNodeTree(root, result, 0)
            return result
        }
        return simulatedScreenNodes.toList()
    }

    private fun traverseNodeTree(
        node: AccessibilityNodeInfo?,
        outList: MutableList<UiElementNode>,
        depth: Int
    ) {
        if (node == null || depth > 35) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName

        if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable || node.isEditable || node.isScrollable) {
            outList.add(
                UiElementNode(
                    nodeId = "node_${outList.size}",
                    viewIdResourceName = viewId,
                    className = node.className?.toString().orEmpty(),
                    text = text,
                    contentDescription = desc,
                    bounds = UiBounds(rect.left, rect.top, rect.right, rect.bottom),
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isEnabled = node.isEnabled,
                    sourceMethod = "ACCESSIBILITY_NODE"
                )
            )
        }

        for (i in 0 until node.childCount) {
            traverseNodeTree(node.getChild(i), outList, depth + 1)
        }
    }

    fun tapElementByTextOrDescription(query: String): Boolean {
        if (emergencyStopFlag.get()) return false
        val nodes = extractAccessibilityNodes()
        val match = nodes.firstOrNull {
            it.text.contains(query, ignoreCase = true) ||
                it.contentDescription.contains(query, ignoreCase = true) ||
                (it.viewIdResourceName?.contains(query, ignoreCase = true) == true)
        } ?: return false

        interactionTrace.add("TAP_ELEMENT(query='$query', center=(${match.bounds.centerX},${match.bounds.centerY}))")
        return tapCoordinates(match.bounds.centerX, match.bounds.centerY)
    }

    fun tapCoordinates(x: Int, y: Int, durationMs: Long = 65L): Boolean {
        if (emergencyStopFlag.get()) return false
        interactionTrace.add("TAP_COORDS($x, $y)")
        val service = AiraAccessibilityService.activeInstance
        if (service != null) {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }
        return true
    }

    fun longPressCoordinates(x: Int, y: Int, durationMs: Long = 650L): Boolean {
        if (emergencyStopFlag.get()) return false
        interactionTrace.add("LONG_PRESS($x, $y)")
        return tapCoordinates(x, y, durationMs)
    }

    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 320L): Boolean {
        if (emergencyStopFlag.get()) return false
        interactionTrace.add("SWIPE(($startX,$startY)->($endX,$endY))")
        val service = AiraAccessibilityService.activeInstance
        if (service != null) {
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.dispatchGesture(gesture, null, null)
        }
        return true
    }

    fun scrollVertical(forward: Boolean = true): Boolean {
        if (emergencyStopFlag.get()) return false
        interactionTrace.add(if (forward) "SCROLL_DOWN" else "SCROLL_UP")
        return if (forward) {
            swipe(540, 1400, 540, 450, 300L)
        } else {
            swipe(540, 450, 540, 1400, 300L)
        }
    }

    fun typeTextIntoFocusedOrMatchedField(textToType: String, fieldHint: String? = null): Boolean {
        if (emergencyStopFlag.get()) return false
        interactionTrace.add("TYPE_TEXT('$textToType', hint='${fieldHint.orEmpty()}')")
        val service = AiraAccessibilityService.activeInstance
        val root = service?.rootInActiveWindow
        if (root != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return true
            }
        }
        // Also update simulated editable node if running in test harness
        val editIdx = simulatedScreenNodes.indexOfFirst {
            it.isEditable || (fieldHint != null && (it.text.contains(fieldHint, true) || it.contentDescription.contains(fieldHint, true)))
        }
        if (editIdx >= 0) {
            simulatedScreenNodes[editIdx] = simulatedScreenNodes[editIdx].copy(text = textToType)
            return true
        }
        return simulatedScreenNodes.isNotEmpty()
    }

    fun copyToClipboard(label: String, text: String): Boolean {
        if (emergencyStopFlag.get()) return false
        interactionTrace.add("CLIPBOARD_COPY('$label')")
        if (context != null) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
        }
        return true
    }

    fun navigateBack(): Boolean {
        if (emergencyStopFlag.get()) return false
        interactionTrace.add("GLOBAL_ACTION_BACK")
        val service = AiraAccessibilityService.activeInstance
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: true
    }

    fun navigateHome(): Boolean {
        if (emergencyStopFlag.get()) return false
        interactionTrace.add("GLOBAL_ACTION_HOME")
        val service = AiraAccessibilityService.activeInstance
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: true
    }

    fun openRecentApps(): Boolean {
        if (emergencyStopFlag.get()) return false
        interactionTrace.add("GLOBAL_ACTION_RECENTS")
        val service = AiraAccessibilityService.activeInstance
        return service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: true
    }
}
