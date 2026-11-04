package com.aira.assistant.mobile

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.aira.assistant.core.AiraLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Floating System Overlay HUD (`AiraFloatingOverlayService`).
 *
 * Displays a compact, translucent pill at the top of the phone screen while AIRA is operating
 * other apps (Chrome, YouTube, Video Editors), showing:
 * - Current step / action being performed
 * - A 1-tap red **STOP** button that halts all screen automation immediately.
 */
class AiraFloatingOverlayService : Service() {

    companion object {
        const val ACTION_SHOW_OVERLAY = "com.aira.assistant.action.SHOW_OVERLAY"
        const val ACTION_UPDATE_STATUS = "com.aira.assistant.action.UPDATE_OVERLAY_STATUS"
        const val ACTION_HIDE_OVERLAY = "com.aira.assistant.action.HIDE_OVERLAY"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        private val _isOverlayVisible = MutableStateFlow(false)
        val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

        private val _overlayStatusText = MutableStateFlow("AIRA Operator Ready")
        val overlayStatusText: StateFlow<String> = _overlayStatusText.asStateFlow()

        var onEmergencyStopRequested: (() -> Unit)? = null

        fun canDrawOverlays(context: Context?): Boolean {
            if (context == null) return true
            return Settings.canDrawOverlays(context)
        }

        fun updateStatusForTest(text: String, visible: Boolean = true) {
            _overlayStatusText.value = text
            _isOverlayVisible.value = visible
        }
    }

    private var windowManager: WindowManager? = null
    private var rootContainer: LinearLayout? = null
    private var statusTextView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE_OVERLAY -> {
                removeOverlayView()
                stopSelf()
            }
            ACTION_UPDATE_STATUS -> {
                val status = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "AIRA Operating Screen…"
                _overlayStatusText.value = status
                statusTextView?.text = status
            }
            else -> {
                val initial = intent?.getStringExtra(EXTRA_STATUS_TEXT) ?: "AIRA Live Operator Active"
                _overlayStatusText.value = initial
                showOverlayView(initial)
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlayView(initialStatus: String) {
        if (!Settings.canDrawOverlays(this)) {
            AiraLogger.w("FloatingOverlay", "SYSTEM_ALERT_WINDOW permission not granted yet.")
            return
        }
        if (rootContainer != null) {
            statusTextView?.text = initialStatus
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 48
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#D90E1120"))
            setPadding(32, 16, 32, 16)
        }

        val tv = TextView(this).apply {
            text = initialStatus
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 24, 0)
        }

        val stopBtn = Button(this).apply {
            text = "STOP"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#E11D48"))
            setOnClickListener {
                onEmergencyStopRequested?.invoke()
                removeOverlayView()
                stopSelf()
            }
        }

        container.addView(tv)
        container.addView(stopBtn)
        windowManager?.addView(container, params)

        rootContainer = container
        statusTextView = tv
        _isOverlayVisible.value = true
    }

    private fun removeOverlayView() {
        rootContainer?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        rootContainer = null
        statusTextView = null
        _isOverlayVisible.value = false
    }

    override fun onDestroy() {
        removeOverlayView()
        super.onDestroy()
    }
}
