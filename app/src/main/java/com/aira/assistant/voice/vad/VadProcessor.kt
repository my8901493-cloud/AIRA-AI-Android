package com.aira.assistant.voice.vad

import com.aira.assistant.ai.local.NativeBridge

data class VadDecision(
    val isSpeechFrame: Boolean,
    val isBargeInTriggered: Boolean,
    val isEndOfTurnDetected: Boolean,
    val rmsEnergy: Float,
    val zeroCrossingRate: Float
)

/**
 * Real-Time Voice Activity Detector (`VadProcessor`) with Echo-Aware Barge-In Thresholding.
 *
 * Prevents false self-interruption from AIRA's own speaker output while detecting genuine
 * human barge-in speech within ~60-90ms (3 consecutive human-voice-band frames).
 */
class VadProcessor(
    var baseEnergyThreshold: Float = 0.032f,
    var playbackEchoGuardMultiplier: Float = 2.15f,
    var bargeInConsecutiveFramesRequired: Int = 3,
    var endOfTurnSilenceMs: Long = 680L
) {
    private var consecutiveSpeechFrames: Int = 0
    private var hasSpokenInCurrentTurn: Boolean = false
    private var lastSpeechTimestampMs: Long = 0L

    fun resetTurn() {
        consecutiveSpeechFrames = 0
        hasSpokenInCurrentTurn = false
        lastSpeechTimestampMs = 0L
    }

    /**
     * Evaluates a 16-bit PCM frame (typically 20ms @ 16kHz = 320 samples).
     *
     * @param pcmFrame 16-bit mono PCM audio samples
     * @param isAssistantCurrentlySpeaking true when TTS playback is active
     * @param isHardwareAecActive true if Android hardware `AcousticEchoCanceler` is enabled on the mic session
     * @param nowMs current monotonic timestamp in ms
     */
    fun processPcmFrame(
        pcmFrame: ShortArray,
        isAssistantCurrentlySpeaking: Boolean,
        isHardwareAecActive: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): VadDecision {
        val (rms, zcr) = NativeBridge.computeAudioMetrics(pcmFrame, pcmFrame.size)

        // Human speech typically has ZCR between 0.02 and 0.42 at 16kHz; pure stationary hum or clicks fall outside
        val isHumanVoiceBandZcr = zcr in 0.015f..0.45f

        // Raise threshold during assistant playback to reject residual speaker bleed when AEC is partial
        val effectiveThreshold = if (isAssistantCurrentlySpeaking) {
            val guard = if (isHardwareAecActive) playbackEchoGuardMultiplier else (playbackEchoGuardMultiplier * 1.35f)
            baseEnergyThreshold * guard
        } else {
            baseEnergyThreshold
        }

        val isSpeech = (rms >= effectiveThreshold) && isHumanVoiceBandZcr

        if (isSpeech) {
            consecutiveSpeechFrames++
            if (consecutiveSpeechFrames >= 2) {
                hasSpokenInCurrentTurn = true
                lastSpeechTimestampMs = nowMs
            }
        } else {
            consecutiveSpeechFrames = 0
        }

        val bargeIn = isAssistantCurrentlySpeaking &&
            (consecutiveSpeechFrames >= bargeInConsecutiveFramesRequired)

        val endOfTurn = !isAssistantCurrentlySpeaking &&
            hasSpokenInCurrentTurn &&
            !isSpeech &&
            lastSpeechTimestampMs > 0L &&
            ((nowMs - lastSpeechTimestampMs) >= endOfTurnSilenceMs)

        return VadDecision(
            isSpeechFrame = isSpeech,
            isBargeInTriggered = bargeIn,
            isEndOfTurnDetected = endOfTurn,
            rmsEnergy = rms,
            zeroCrossingRate = zcr
        )
    }
}
