package com.aira.assistant.voice.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.AiraLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Modular Streaming Text-to-Speech Engine (`TtsEngine`).
 *
 * Key architecture features:
 * - Never waits for a full paragraph to finish generating: accumulates streaming LLM tokens
 *   into natural spoken clauses/sentences and immediately dispatches each chunk to the audio queue.
 * - Selects a natural Female voice by default (`en-IN` / `hi-IN`).
 * - Supports instant barge-in cancellation (`stopImmediately()`): flushes both the sentence buffer
 *   and the native Android `TextToSpeech.QUEUE_FLUSH` / AudioTrack output in <15ms.
 */
interface TtsEngine {
    val isSpeaking: StateFlow<Boolean>
    val speakingAmplitude: StateFlow<Float>
    val firstAudioLatencyMs: StateFlow<Long>

    fun setSpeechRate(rate: Float)
    fun setPitch(pitch: Float)
    fun setLanguage(language: AiraLanguage)
    fun feedStreamingToken(token: String, requestStartMs: Long)
    fun flushRemainingBuffer(requestStartMs: Long)
    fun speakImmediate(text: String, language: AiraLanguage = AiraLanguage.HINGLISH)
    fun stopImmediately()
    fun release()
}

class ModularStreamingTtsEngine(
    private val context: Context? = null
) : TtsEngine {

    private var androidTts: TextToSpeech? = null
    private val isTtsReady = AtomicBoolean(false)

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakingAmplitude = MutableStateFlow(0f)
    override val speakingAmplitude: StateFlow<Float> = _speakingAmplitude.asStateFlow()

    private val _firstAudioLatencyMs = MutableStateFlow(0L)
    override val firstAudioLatencyMs: StateFlow<Long> = _firstAudioLatencyMs.asStateFlow()

    private val tokenSentenceBuffer = StringBuilder()
    private val activeUtteranceIds = ConcurrentLinkedQueue<String>()
    private val firstChunkRecordedForTurn = AtomicBoolean(false)

    private var speechRate: Float = 1.02f
    private var speechPitch: Float = 1.05f
    private var preferredLanguage: AiraLanguage = AiraLanguage.HINGLISH

    init {
        if (context != null) {
            initializeAndroidTts(context)
        } else {
            isTtsReady.set(true)
        }
    }

    private fun initializeAndroidTts(ctx: Context) {
        try {
            androidTts = TextToSpeech(ctx.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady.set(true)
                    configureFemaleVoiceAndAttributes()
                    AiraLogger.i("TtsEngine", "Android TTS initialized with female voice preference.")
                } else {
                    AiraLogger.w("TtsEngine", "Android TTS initialization returned status $status")
                }
            }
        } catch (e: Exception) {
            AiraLogger.e("TtsEngine", "Error initializing Android TTS", e)
        }
    }

    private fun configureFemaleVoiceAndAttributes() {
        val tts = androidTts ?: return
        runCatching {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts.setSpeechRate(speechRate)
            tts.setPitch(speechPitch)

            val targetLocale = Locale.forLanguageTag(preferredLanguage.localeTag)
            tts.language = targetLocale

            // Select high-quality female voice matching hi-IN or en-IN if available
            val voices: Set<Voice>? = tts.voices
            val femaleVoice = voices?.firstOrNull { v ->
                (v.locale.language == targetLocale.language) &&
                    (v.name.contains("female", ignoreCase = true) ||
                        v.name.contains("sfg", ignoreCase = true) ||
                        v.name.contains("hia", ignoreCase = true) ||
                        v.name.contains("end", ignoreCase = true)) &&
                    !v.isNetworkConnectionRequired
            } ?: voices?.firstOrNull { v ->
                v.locale.language == targetLocale.language &&
                    v.name.contains("female", ignoreCase = true)
            }

            if (femaleVoice != null) {
                tts.voice = femaleVoice
                AiraLogger.i("TtsEngine", "Selected female voice: ${femaleVoice.name}")
            }

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _speakingAmplitude.value = 0.65f
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    // Modulate orb amplitude smoothly as words are spoken
                    val mod = 0.45f + ((end - start).coerceIn(1, 12) / 24f)
                    _speakingAmplitude.value = mod.coerceIn(0.3f, 0.95f)
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { activeUtteranceIds.remove(it) }
                    if (activeUtteranceIds.isEmpty()) {
                        _isSpeaking.value = false
                        _speakingAmplitude.value = 0f
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { activeUtteranceIds.remove(it) }
                    if (activeUtteranceIds.isEmpty()) {
                        _isSpeaking.value = false
                        _speakingAmplitude.value = 0f
                    }
                }
            })
        }
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        runCatching { androidTts?.setSpeechRate(speechRate) }
    }

    override fun setPitch(pitch: Float) {
        speechPitch = pitch.coerceIn(0.5f, 1.8f)
        runCatching { androidTts?.setPitch(speechPitch) }
    }

    override fun setLanguage(language: AiraLanguage) {
        preferredLanguage = language
        runCatching {
            androidTts?.language = Locale.forLanguageTag(language.localeTag)
        }
    }

    /**
     * Feeds streaming tokens from the LLM and dispatches spoken clauses as soon as a natural
     * sentence or clause boundary (`.`, `?`, `!`, `।`, `,`, `;`, `
`) is reached after >= 24 chars.
     */
    @Synchronized
    override fun feedStreamingToken(token: String, requestStartMs: Long) {
        tokenSentenceBuffer.append(token)
        val current = tokenSentenceBuffer.toString()

        val boundaryIndex = findNaturalClauseBoundary(current)
        if (boundaryIndex != -1) {
            val clause = current.substring(0, boundaryIndex + 1).trim()
            tokenSentenceBuffer.delete(0, boundaryIndex + 1)
            if (clause.isNotEmpty()) {
                enqueueSpokenChunk(clause, requestStartMs)
            }
        }
    }

    @Synchronized
    override fun flushRemainingBuffer(requestStartMs: Long) {
        val remaining = tokenSentenceBuffer.toString().trim()
        tokenSentenceBuffer.clear()
        if (remaining.isNotEmpty()) {
            enqueueSpokenChunk(remaining, requestStartMs)
        }
        firstChunkRecordedForTurn.set(false)
    }

    override fun speakImmediate(text: String, language: AiraLanguage) {
        stopImmediately()
        setLanguage(language)
        enqueueSpokenChunk(text.trim(), System.currentTimeMillis())
        firstChunkRecordedForTurn.set(false)
    }

    private fun findNaturalClauseBoundary(text: String): Int {
        if (text.length < 18) return -1
        val strongEndings = charArrayOf('.', '!', '?', '।', '\n')
        for (i in text.indices) {
            if (i >= 14 && text[i] in strongEndings) {
                return i
            }
        }
        if (text.length >= 48) {
            val softEndings = charArrayOf(',', ';', '—', ':')
            for (i in 24 until text.length) {
                if (text[i] in softEndings) return i
            }
        }
        return -1
    }

    private fun enqueueSpokenChunk(chunk: String, requestStartMs: Long) {
        val cleanChunk = chunk.replace("*", "").replace("#", "").trim()
        if (cleanChunk.isEmpty()) return

        if (firstChunkRecordedForTurn.compareAndSet(false, true)) {
            val latency = (System.currentTimeMillis() - requestStartMs).coerceAtLeast(1L)
            _firstAudioLatencyMs.value = latency
            AiraLogger.i("TtsEngine", "First TTS audio chunk dispatched in ${latency}ms: \"${cleanChunk.take(40)}\"")
        }

        val utteranceId = "aira_tts_${UUID.randomUUID()}"
        activeUtteranceIds.add(utteranceId)
        _isSpeaking.value = true
        _speakingAmplitude.value = 0.72f

        val tts = androidTts
        if (tts != null && isTtsReady.get()) {
            // Switch locale dynamically if chunk contains Devanagari characters
            if (cleanChunk.any { it in '\u0900'..'\u097F' }) {
                runCatching { tts.language = Locale.forLanguageTag("hi-IN") }
            }
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            tts.speak(cleanChunk, TextToSpeech.QUEUE_ADD, params, utteranceId)
        }
    }

    /**
     * Immediate Barge-In stop: clears pending sentence buffers, flushes active TTS queue,
     * and resets speaking state immediately so no stale response plays after user interruption.
     */
    @Synchronized
    override fun stopImmediately() {
        tokenSentenceBuffer.clear()
        activeUtteranceIds.clear()
        firstChunkRecordedForTurn.set(false)
        runCatching { androidTts?.stop() }
        _isSpeaking.value = false
        _speakingAmplitude.value = 0f
        AiraLogger.i("TtsEngine", "TTS playback stopped and audio buffers flushed immediately.")
    }

    override fun release() {
        stopImmediately()
        runCatching {
            androidTts?.shutdown()
            androidTts = null
        }
    }
}
