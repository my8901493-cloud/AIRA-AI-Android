package com.aira.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.aira.assistant.ai.ModelCatalogManager
import com.aira.assistant.core.NetworkConnectivityObserver
import com.aira.assistant.core.SecurityPreferences
import com.aira.assistant.data.repository.ConversationRepository
import com.aira.assistant.data.repository.MemoryRepository
import com.aira.assistant.service.ScreenCaptureService
import com.aira.assistant.tools.AiraToolRegistry
import com.aira.assistant.ui.AiraNavHost
import com.aira.assistant.ui.theme.AiraTheme
import com.aira.assistant.ui.viewmodel.AiraViewModel
import com.aira.assistant.voice.stt.ModularAndroidSttEngine
import com.aira.assistant.voice.tts.ModularStreamingTtsEngine

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AiraViewModel

    private val requestMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onOrbTapped()
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START_PROJECTION
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AiraApplication
        val db = app.database

        viewModel = AiraViewModel(
            preferences = SecurityPreferences(this),
            modelCatalogManager = ModelCatalogManager(this),
            memoryRepository = MemoryRepository(db.memoryDao()),
            conversationRepository = ConversationRepository(db.conversationDao()),
            toolRegistry = AiraToolRegistry(this, db.assistantItemsDao()),
            connectivityObserver = NetworkConnectivityObserver(this),
            sttEngineOverride = ModularAndroidSttEngine(this),
            ttsEngineOverride = ModularStreamingTtsEngine(this)
        )

        handleIncomingShareIntent(intent)

        setContent {
            AiraTheme {
                AiraNavHost(
                    viewModel = viewModel,
                    onRequestMicPermissionAndToggle = { checkMicPermissionAndToggle() },
                    onToggleScreenSharing = { toggleScreenSharing() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrEmpty()) {
                viewModel.sendTextOrVoiceUtterance("Please summarize this shared text: $sharedText")
            }
        }
    }

    private fun checkMicPermissionAndToggle() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.onOrbTapped()
        } else {
            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun toggleScreenSharing() {
        if (ScreenCaptureService.isScreenSharingActive.value) {
            val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP_PROJECTION
            }
            startService(stopIntent)
        } else {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
        }
    }
}
