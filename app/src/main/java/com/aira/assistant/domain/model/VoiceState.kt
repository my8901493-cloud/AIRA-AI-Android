package com.aira.assistant.domain.model

/**
 * Explicit voice pipeline and central AI Orb states required by AIRA AI specification.
 */
enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTING,
    ERROR;

    val isActiveSession: Boolean
        get() = this == LISTENING || this == THINKING || this == SPEAKING || this == INTERRUPTING
}

/**
 * Supported conversation languages for AIRA AI.
 */
enum class AiraLanguage(val code: String, val displayName: String, val localeTag: String) {
    ENGLISH("en", "English (India)", "en-IN"),
    HINDI("hi", "हिन्दी (Hindi)", "hi-IN"),
    HINGLISH("hi-Latn", "Hinglish (Hindi + English)", "en-IN");

    companion object {
        fun fromCode(code: String): AiraLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: HINGLISH
    }
}

/**
 * Latency & voice session telemetry captured in real time.
 */
data class VoiceTelemetry(
    val vadTriggerMs: Long = 0L,
    val sttPartialLatencyMs: Long = 0L,
    val llmFirstTokenLatencyMs: Long = 0L,
    val ttsFirstAudioLatencyMs: Long = 0L,
    val totalTurnAroundMs: Long = 0L,
    val bargeInCancelLatencyMs: Long = 0L,
    val currentRmsEnergy: Float = 0f,
    val isEchoCancelerEnabled: Boolean = false,
    val isNoiseSuppressorEnabled: Boolean = false
)
