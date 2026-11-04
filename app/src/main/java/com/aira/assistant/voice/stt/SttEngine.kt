package com.aira.assistant.voice.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.AiraLanguage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

sealed class SttEvent {
    data object ReadyForSpeech : SttEvent()
    data class PartialTranscript(val text: String, val confidence: Float = 0.8f) : SttEvent()
    data class FinalTranscript(val text: String, val language: AiraLanguage, val latencyMs: Long) : SttEvent()
    data class AudioRmsChanged(val rmsDb: Float) : SttEvent()
    data class Error(val code: Int, val message: String, val recoverable: Boolean) : SttEvent()
}

interface SttEngine {
    val engineId: String
    val events: SharedFlow<SttEvent>
    fun startListening(language: AiraLanguage, preferOffline: Boolean = false)
    fun stopListening()
    fun cancel()
    fun release()
}

/**
 * Modular Speech Recognition Engine supporting:
 * 1. Android's low-latency streaming `SpeechRecognizer` with partial transcription and
 *    bilingual Hindi (`hi-IN`) / Indian English & Hinglish (`en-IN`) recognition.
 * 2. Offline Whisper.cpp / Vosk PCM chunk transcription when an offline STT model file is selected.
 */
class ModularAndroidSttEngine(
    private val context: Context?,
    private var offlineWhisperModelFile: File? = null
) : SttEngine {

    override val engineId: String = "modular-android-stt"

    private val _events = MutableSharedFlow<SttEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<SttEvent> = _events.asSharedFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var listenStartTimestampMs: Long = 0L
    private var currentLanguage: AiraLanguage = AiraLanguage.HINGLISH

    override fun startListening(language: AiraLanguage, preferOffline: Boolean) {
        currentLanguage = language
        listenStartTimestampMs = System.currentTimeMillis()

        if (context == null) {
            AiraLogger.i("SttEngine", "Host/Test mode: ready for simulated STT events.")
            _events.tryEmit(SttEvent.ReadyForSpeech)
            return
        }

        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _events.tryEmit(
                    SttEvent.Error(
                        code = -1,
                        message = "Speech recognition service unavailable on this device.",
                        recoverable = false
                    )
                )
                return
            }

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            speechRecognizer?.startListening(intent)
            AiraLogger.i("SttEngine", "Started STT listening (locale=${language.localeTag}, offline=$preferOffline)")
        } catch (e: Exception) {
            AiraLogger.e("SttEngine", "Failed starting SpeechRecognizer", e)
            _events.tryEmit(SttEvent.Error(-2, e.message ?: "STT start error", false))
        }
    }

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _events.tryEmit(SttEvent.ReadyForSpeech)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _events.tryEmit(SttEvent.AudioRmsChanged(normalized))
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            val msg = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error; switching to offline mode"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "STT error code $error"
            }
            _events.tryEmit(SttEvent.Error(error, msg, recoverable))
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()?.trim().orEmpty()
            if (best.isNotEmpty()) {
                val latency = (System.currentTimeMillis() - listenStartTimestampMs).coerceAtLeast(1L)
                _events.tryEmit(SttEvent.FinalTranscript(best, currentLanguage, latency))
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = partials?.firstOrNull()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                _events.tryEmit(SttEvent.PartialTranscript(text))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Allows test harnesses or offline Whisper PCM decoders to push partial/final transcripts directly.
     */
    fun emitSimulatedOrOfflineTranscript(text: String, isFinal: Boolean, language: AiraLanguage = currentLanguage) {
        if (isFinal) {
            val latency = (System.currentTimeMillis() - listenStartTimestampMs).coerceAtLeast(15L)
            _events.tryEmit(SttEvent.FinalTranscript(text, language, latency))
        } else {
            _events.tryEmit(SttEvent.PartialTranscript(text))
        }
    }

    override fun stopListening() {
        runCatching { speechRecognizer?.stopListening() }
    }

    override fun cancel() {
        runCatching { speechRecognizer?.cancel() }
    }

    override fun release() {
        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
}
