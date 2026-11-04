package com.aira.assistant.voice

import com.aira.assistant.ai.AiraPersonaPromptBuilder
import com.aira.assistant.ai.LlmEngine
import com.aira.assistant.ai.LlmGenerationRequest
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.AiraLanguage
import com.aira.assistant.domain.model.ChatMessage
import com.aira.assistant.domain.model.MemoryItem
import com.aira.assistant.domain.model.VoiceState
import com.aira.assistant.domain.model.VoiceTelemetry
import com.aira.assistant.voice.stt.SttEngine
import com.aira.assistant.voice.stt.SttEvent
import com.aira.assistant.voice.tts.TtsEngine
import com.aira.assistant.voice.vad.VadProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Full-Duplex Real-Time Voice Conversation Coordinator (`VoiceConversationCoordinator`).
 *
 * Implements the complete pipeline:
 * Microphone -> Audio Capture -> VAD (with AEC/NS) -> STT -> AI Reasoning -> Streaming TTS -> Audio Playback
 *
 * Explicitly manages the 6 required states:
 * `IDLE`, `LISTENING`, `THINKING`, `SPEAKING`, `INTERRUPTING`, `ERROR`
 *
 * Enforces strict turn-generation IDs (`turnGenerationId`) so that when a user barges in,
 * any stale LLM tokens or queued TTS chunks from an older turn are immediately discarded.
 */
class VoiceConversationCoordinator(
    private val scope: CoroutineScope,
    private val sttEngine: SttEngine,
    private val ttsEngine: TtsEngine,
    private val llmEngine: LlmEngine,
    private val vadProcessor: VadProcessor = VadProcessor(),
    private val audioCaptureManager: AudioCaptureManager = AudioCaptureManager()
) {
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _livePartialTranscript = MutableStateFlow("")
    val livePartialTranscript: StateFlow<String> = _livePartialTranscript.asStateFlow()

    private val _streamingAssistantReply = MutableStateFlow("")
    val streamingAssistantReply: StateFlow<String> = _streamingAssistantReply.asStateFlow()

    private val _orbAudioLevel = MutableStateFlow(0f)
    val orbAudioLevel: StateFlow<Float> = _orbAudioLevel.asStateFlow()

    private val _telemetry = MutableStateFlow(VoiceTelemetry())
    val telemetry: StateFlow<VoiceTelemetry> = _telemetry.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var continuousConversationEnabled: Boolean = true
    var selectedLanguage: AiraLanguage = AiraLanguage.HINGLISH
    var preferOfflineRecognition: Boolean = false

    private val turnGenerationId = AtomicLong(0L)
    private var activeReasoningJob: Job? = null

    var onTurnCompleted: (suspend (userText: String, assistantText: String, language: AiraLanguage, wasInterrupted: Boolean) -> Unit)? = null
    var contextProvider: (suspend (userQuery: String) -> TurnContextBundle)? = null

    data class TurnContextBundle(
        val history: List<ChatMessage> = emptyList(),
        val memories: List<MemoryItem> = emptyList(),
        val screenContext: String? = null,
        val toolOutput: String? = null
    )

    init {
        observeSttEvents()
        observeTtsState()
    }

    private fun observeSttEvents() {
        scope.launch {
            sttEngine.events.collect { event ->
                when (event) {
                    is SttEvent.ReadyForSpeech -> {
                        if (_voiceState.value != VoiceState.SPEAKING) {
                            _voiceState.value = VoiceState.LISTENING
                        }
                    }
                    is SttEvent.PartialTranscript -> {
                        // If user begins speaking while AIRA is speaking, trigger instant barge-in
                        if (_voiceState.value == VoiceState.SPEAKING && event.text.length >= 2) {
                            triggerBargeIn("STT partial speech detected: ${event.text}")
                        }
                        _livePartialTranscript.value = event.text
                    }
                    is SttEvent.FinalTranscript -> {
                        _livePartialTranscript.value = event.text
                        _telemetry.value = _telemetry.value.copy(sttPartialLatencyMs = event.latencyMs)
                        processUserUtterance(event.text)
                    }
                    is SttEvent.AudioRmsChanged -> {
                        if (_voiceState.value == VoiceState.LISTENING) {
                            _orbAudioLevel.value = event.rmsDb
                        }
                    }
                    is SttEvent.Error -> {
                        if (!event.recoverable) {
                            _errorMessage.value = event.message
                            _voiceState.value = VoiceState.ERROR
                        } else if (continuousConversationEnabled && _voiceState.value == VoiceState.LISTENING) {
                            sttEngine.startListening(selectedLanguage, preferOfflineRecognition)
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }
                }
            }
        }
    }

    private fun observeTtsState() {
        scope.launch {
            ttsEngine.isSpeaking.collect { speaking ->
                if (speaking && _voiceState.value == VoiceState.THINKING) {
                    _voiceState.value = VoiceState.SPEAKING
                } else if (!speaking && _voiceState.value == VoiceState.SPEAKING && activeReasoningJob?.isActive != true) {
                    if (continuousConversationEnabled) {
                        transitionToListening()
                    } else {
                        _voiceState.value = VoiceState.IDLE
                        _orbAudioLevel.value = 0f
                    }
                }
            }
        }
        scope.launch {
            ttsEngine.speakingAmplitude.collect { amp ->
                if (_voiceState.value == VoiceState.SPEAKING) {
                    _orbAudioLevel.value = amp
                }
            }
        }
        scope.launch {
            ttsEngine.firstAudioLatencyMs.collect { ms ->
                if (ms > 0L) {
                    _telemetry.value = _telemetry.value.copy(ttsFirstAudioLatencyMs = ms)
                }
            }
        }
    }

    /**
     * Starts a voice session (single turn or continuous full-duplex conversation).
     */
    fun startSession(enableMicCapture: Boolean = true) {
        _errorMessage.value = null
        vadProcessor.resetTurn()

        if (enableMicCapture) {
            audioCaptureManager.startFullDuplexCapture(
                scope = scope,
                onPcmFrame = { frame -> onRawMicrophoneFrame(frame) },
                onError = { err ->
                    _errorMessage.value = err
                    _voiceState.value = VoiceState.ERROR
                }
            )
            _telemetry.value = _telemetry.value.copy(
                isEchoCancelerEnabled = audioCaptureManager.isHardwareAecEnabled,
                isNoiseSuppressorEnabled = audioCaptureManager.isHardwareNsEnabled
            )
        }
        transitionToListening()
    }

    fun transitionToListening() {
        vadProcessor.resetTurn()
        _livePartialTranscript.value = ""
        _voiceState.value = VoiceState.LISTENING
        sttEngine.startListening(selectedLanguage, preferOfflineRecognition)
    }

    /**
     * Processes each 20ms PCM microphone frame through the VAD & Echo-Guard Barge-In detector.
     */
    fun onRawMicrophoneFrame(pcmFrame: ShortArray, nowMs: Long = System.currentTimeMillis()) {
        val isSpeakingNow = _voiceState.value == VoiceState.SPEAKING || ttsEngine.isSpeaking.value
        val decision = vadProcessor.processPcmFrame(
            pcmFrame = pcmFrame,
            isAssistantCurrentlySpeaking = isSpeakingNow,
            isHardwareAecActive = audioCaptureManager.isHardwareAecEnabled,
            nowMs = nowMs
        )

        if (_voiceState.value == VoiceState.LISTENING) {
            _orbAudioLevel.value = (decision.rmsEnergy * 4.5f).coerceIn(0f, 1f)
        }

        _telemetry.value = _telemetry.value.copy(currentRmsEnergy = decision.rmsEnergy)

        if (decision.isBargeInTriggered) {
            triggerBargeIn("VAD detected user voice energy (${decision.rmsEnergy}) during playback")
        }
    }

    /**
     * Instant Barge-In handler:
     * 1. Increments `turnGenerationId` so any in-flight tokens from the interrupted turn are dropped.
     * 2. Cancels active LLM streaming coroutine and native/network generation.
     * 3. Stops TTS playback and flushes all queued audio chunks immediately.
     * 4. Transitions state `SPEAKING` -> `INTERRUPTING` -> `LISTENING`.
     */
    fun triggerBargeIn(reason: String = "User interrupted AIRA") {
        val bargeStartMs = System.currentTimeMillis()
        val interruptedReply = _streamingAssistantReply.value
        turnGenerationId.incrementAndGet()

        _voiceState.value = VoiceState.INTERRUPTING
        AiraLogger.i("VoiceCoordinator", "BARGE-IN triggered: $reason")

        activeReasoningJob?.cancel(CancellationException("User barge-in"))
        activeReasoningJob = null
        llmEngine.cancelActiveGeneration()
        ttsEngine.stopImmediately()

        val cancelElapsed = (System.currentTimeMillis() - bargeStartMs).coerceAtLeast(1L)
        _telemetry.value = _telemetry.value.copy(bargeInCancelLatencyMs = cancelElapsed)

        if (interruptedReply.isNotBlank()) {
            scope.launch {
                onTurnCompleted?.invoke(
                    _livePartialTranscript.value,
                    "$interruptedReply [interrupted]",
                    selectedLanguage,
                    true
                )
            }
        }

        _streamingAssistantReply.value = ""
        transitionToListening()
    }

    /**
     * Executes AI reasoning + streaming TTS for a finalized user utterance (from voice or text input).
     */
    fun processUserUtterance(rawUserText: String) {
        val cleaned = rawUserText.trim()
        if (cleaned.isEmpty()) return

        // If AIRA was speaking when a new user utterance arrived, interrupt immediately first
        if (_voiceState.value == VoiceState.SPEAKING || activeReasoningJob?.isActive == true) {
            triggerBargeIn("New user utterance received while busy")
        }

        val currentTurnId = turnGenerationId.incrementAndGet()
        val turnStartMs = System.currentTimeMillis()

        val detectedLang = AiraPersonaPromptBuilder.detectLanguageFromUtterance(cleaned)
        val effectiveLanguage = if (detectedLang != AiraLanguage.ENGLISH) detectedLang else selectedLanguage

        _voiceState.value = VoiceState.THINKING
        _streamingAssistantReply.value = ""

        activeReasoningJob = scope.launch {
            try {
                val contextBundle = contextProvider?.invoke(cleaned) ?: TurnContextBundle()
                val request = LlmGenerationRequest(
                    userPrompt = cleaned,
                    language = effectiveLanguage,
                    conversationHistory = contextBundle.history,
                    relevantMemories = contextBundle.memories,
                    screenContextText = contextBundle.screenContext,
                    toolContextOutput = contextBundle.toolOutput
                )

                ttsEngine.setLanguage(effectiveLanguage)
                val responseBuilder = StringBuilder()
                var firstTokenRecorded = false

                llmEngine.streamResponse(request).collect { token ->
                    if (turnGenerationId.get() != currentTurnId) {
                        throw CancellationException("Stale turn $currentTurnId superseded")
                    }

                    if (!firstTokenRecorded) {
                        firstTokenRecorded = true
                        val firstTokenMs = (System.currentTimeMillis() - turnStartMs).coerceAtLeast(1L)
                        _telemetry.value = _telemetry.value.copy(llmFirstTokenLatencyMs = firstTokenMs)
                        _voiceState.value = VoiceState.SPEAKING
                    }

                    responseBuilder.append(token)
                    _streamingAssistantReply.value = responseBuilder.toString()
                    ttsEngine.feedStreamingToken(token, turnStartMs)
                }

                if (turnGenerationId.get() == currentTurnId) {
                    ttsEngine.flushRemainingBuffer(turnStartMs)
                    val finalReply = responseBuilder.toString().trim()
                    val totalTurnMs = (System.currentTimeMillis() - turnStartMs).coerceAtLeast(1L)
                    _telemetry.value = _telemetry.value.copy(totalTurnAroundMs = totalTurnMs)

                    onTurnCompleted?.invoke(cleaned, finalReply, effectiveLanguage, false)

                    if (!ttsEngine.isSpeaking.value) {
                        if (continuousConversationEnabled) {
                            transitionToListening()
                        } else {
                            _voiceState.value = VoiceState.IDLE
                            _orbAudioLevel.value = 0f
                        }
                    }
                }
            } catch (ce: CancellationException) {
                AiraLogger.i("VoiceCoordinator", "Turn $currentTurnId cancelled cleanly.")
            } catch (e: Exception) {
                AiraLogger.e("VoiceCoordinator", "Error processing user utterance", e)
                _errorMessage.value = e.message ?: "Reasoning error"
                _voiceState.value = VoiceState.ERROR
            }
        }
    }

    fun stopSession() {
        turnGenerationId.incrementAndGet()
        activeReasoningJob?.cancel()
        activeReasoningJob = null
        llmEngine.cancelActiveGeneration()
        ttsEngine.stopImmediately()
        sttEngine.cancel()
        audioCaptureManager.stopCapture()
        _livePartialTranscript.value = ""
        _orbAudioLevel.value = 0f
        _voiceState.value = VoiceState.IDLE
    }

    fun release() {
        stopSession()
        sttEngine.release()
        ttsEngine.release()
    }
}
