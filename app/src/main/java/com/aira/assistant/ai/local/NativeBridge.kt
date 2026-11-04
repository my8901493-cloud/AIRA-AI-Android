package com.aira.assistant.ai.local

import com.aira.assistant.core.AiraLogger
import java.io.File
import kotlin.math.sqrt

/**
 * JNI Bridge to `libaira_native.so` with pure-Kotlin fallback when running inside
 * JVM unit tests where Android's native `.so` is not loaded.
 */
object NativeBridge {
    private var nativeLoaded = false

    init {
        try {
            System.loadLibrary("aira_native")
            nativeLoaded = true
            AiraLogger.i("NativeBridge", "Loaded libaira_native.so successfully.")
        } catch (t: Throwable) {
            nativeLoaded = false
            AiraLogger.w("NativeBridge", "Running with pure-Kotlin DSP fallback (JVM/host mode).")
        }
    }

    private external fun computeAudioMetricsNative(pcmBuffer: ShortArray, length: Int): FloatArray
    private external fun validateGgufHeaderNative(filePath: String): Boolean

    /**
     * Returns Pair(rmsNormalized [0..1], zeroCrossingRate [0..1]).
     */
    fun computeAudioMetrics(pcmBuffer: ShortArray, length: Int = pcmBuffer.size): Pair<Float, Float> {
        val safeLen = length.coerceAtMost(pcmBuffer.size)
        if (safeLen <= 0) return 0f to 0f

        if (nativeLoaded) {
            return try {
                val res = computeAudioMetricsNative(pcmBuffer, safeLen)
                Pair(res.getOrElse(0) { 0f }, res.getOrElse(1) { 0f })
            } catch (t: Throwable) {
                computeKotlinFallback(pcmBuffer, safeLen)
            }
        }
        return computeKotlinFallback(pcmBuffer, safeLen)
    }

    private fun computeKotlinFallback(pcmBuffer: ShortArray, length: Int): Pair<Float, Float> {
        var sumSquares = 0.0
        var zeroCrossings = 0
        for (i in 0 until length) {
            val norm = pcmBuffer[i].toDouble() / 32768.0
            sumSquares += norm * norm
            if (i > 0) {
                if ((pcmBuffer[i] >= 0 && pcmBuffer[i - 1] < 0) ||
                    (pcmBuffer[i] < 0 && pcmBuffer[i - 1] >= 0)
                ) {
                    zeroCrossings++
                }
            }
        }
        val rms = sqrt(sumSquares / length.toDouble()).toFloat()
        val zcr = zeroCrossings.toFloat() / length.toFloat()
        return rms to zcr
    }

    fun isValidGgufFile(file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        if (nativeLoaded) {
            return try {
                validateGgufHeaderNative(file.absolutePath)
            } catch (_: Throwable) {
                checkMagicKotlin(file)
            }
        }
        return checkMagicKotlin(file)
    }

    private fun checkMagicKotlin(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 &&
                    header[0] == 'G'.code.toByte() &&
                    header[1] == 'G'.code.toByte() &&
                    header[2] == 'U'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }
}
