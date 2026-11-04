package com.aira.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.aira.assistant.core.AiraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages full-duplex microphone capture using `AudioSource.VOICE_COMMUNICATION`
 * and attaches hardware `AcousticEchoCanceler` (AEC), `NoiseSuppressor` (NS),
 * and `AutomaticGainControl` (AGC) so AIRA can detect user barge-in during TTS playback
 * without triggering on its own speaker output.
 */
class AudioCaptureManager {

    companion object {
        const val SAMPLE_RATE_HZ = 16000
        const val FRAME_SAMPLES = 320 // 20ms frame at 16kHz
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var captureJob: Job? = null

    private val running = AtomicBoolean(false)

    var isHardwareAecEnabled: Boolean = false
        private set
    var isHardwareNsEnabled: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun startFullDuplexCapture(
        scope: CoroutineScope,
        onPcmFrame: (ShortArray) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (running.get()) return true

        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_SAMPLES * 4)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                onError("Microphone AudioRecord failed to initialize.")
                return false
            }

            val sessionId = record.audioSessionId
            attachHardwareAudioEffects(sessionId)

            record.startRecording()
            audioRecord = record
            running.set(true)

            captureJob = scope.launch(Dispatchers.IO) {
                val frameBuffer = ShortArray(FRAME_SAMPLES)
                while (isActive && running.get()) {
                    val read = record.read(frameBuffer, 0, FRAME_SAMPLES)
                    if (read > 0) {
                        val copy = if (read == FRAME_SAMPLES) frameBuffer.clone() else frameBuffer.copyOf(read)
                        onPcmFrame(copy)
                    }
                }
            }
            AiraLogger.i(
                "AudioCaptureManager",
                "Full-duplex capture active (sessionId=$sessionId, AEC=$isHardwareAecEnabled, NS=$isHardwareNsEnabled)"
            )
            true
        } catch (se: SecurityException) {
            onError("Microphone permission is required for voice conversation.")
            false
        } catch (e: Exception) {
            AiraLogger.e("AudioCaptureManager", "Failed starting AudioRecord", e)
            onError("Microphone initialization error: ${e.message}")
            false
        }
    }

    private fun attachHardwareAudioEffects(audioSessionId: Int) {
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                    isHardwareAecEnabled = enabled
                }
            }
        }
        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                    isHardwareNsEnabled = enabled
                }
            }
        }
        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        }
    }

    fun stopCapture() {
        running.set(false)
        captureJob?.cancel()
        captureJob = null

        runCatching { echoCanceler?.release() }
        echoCanceler = null
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null
        runCatching { gainControl?.release() }
        gainControl = null

        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
    }
}
