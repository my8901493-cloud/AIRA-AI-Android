package com.aira.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aira.assistant.MainActivity
import com.aira.assistant.R
import com.aira.assistant.core.AiraLogger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Permission-Based Screen Understanding Service (`ScreenCaptureService`).
 *
 * Security & Privacy guarantees:
 * - Only starts when the user explicitly grants `MediaProjection` consent via the system dialog.
 * - Displays a persistent foreground notification with a one-tap "Stop Screen Sharing" action.
 * - Activates the side-edge glow indicator on AIRA's main UI (`isScreenSharingActive = true`).
 * - Extracts visible screen text on-device using ML Kit Text Recognition (supporting both
 *   Latin/English and Devanagari/Hindi scripts) without uploading screen frames to any cloud server.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "aira_screen_assistant_channel"
        const val NOTIFICATION_ID = 4002
        const val ACTION_START_PROJECTION = "com.aira.assistant.action.START_PROJECTION"
        const val ACTION_STOP_PROJECTION = "com.aira.assistant.action.STOP_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _isScreenSharingActive = MutableStateFlow(false)
        val isScreenSharingActive: StateFlow<Boolean> = _isScreenSharingActive.asStateFlow()

        private val _latestExtractedScreenText = MutableStateFlow<String?>(null)
        val latestExtractedScreenText: StateFlow<String?> = _latestExtractedScreenText.asStateFlow()

        fun updateSimulatedScreenContextForTest(text: String?) {
            _isScreenSharingActive.value = text != null
            _latestExtractedScreenText.value = text
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val recognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_PROJECTION -> {
                stopProjectionInternal()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultData != null) {
                    startForeground(NOTIFICATION_ID, buildForegroundNotification())
                    startMediaProjection(resultCode, resultData)
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)

            val metrics = resources.displayMetrics
            val width = (metrics.widthPixels / 2).coerceAtLeast(360)
            val height = (metrics.heightPixels / 2).coerceAtLeast(640)
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    analyzeScreenBitmapOnDevice(cropped)
                } catch (e: Exception) {
                    AiraLogger.w("ScreenCaptureService", "Frame decode skipped: ${e.message}")
                } finally {
                    image.close()
                }
            }, null)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "AiraScreenAssistantDisplay",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )

            _isScreenSharingActive.value = true
            AiraLogger.i("ScreenCaptureService", "User-authorized MediaProjection screen assistance started.")
        } catch (e: Exception) {
            AiraLogger.e("ScreenCaptureService", "Failed to start MediaProjection", e)
            stopProjectionInternal()
        }
    }

    private fun analyzeScreenBitmapOnDevice(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val extracted = visionText.text.trim()
                if (extracted.isNotEmpty()) {
                    _latestExtractedScreenText.value = extracted
                }
            }
    }

    private fun stopProjectionInternal() {
        _isScreenSharingActive.value = false
        _latestExtractedScreenText.value = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        AiraLogger.i("ScreenCaptureService", "Screen sharing stopped and resources released.")
    }

    private fun buildForegroundNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.screen_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP_PROJECTION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AIRA Screen Assistant Active")
            .setContentText("Analyzing shared screen locally. Tap Stop anytime.")
            .setContentIntent(openPendingIntent)
            .addAction(0, getString(R.string.stop_screen_sharing), stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopProjectionInternal()
        super.onDestroy()
    }
}
