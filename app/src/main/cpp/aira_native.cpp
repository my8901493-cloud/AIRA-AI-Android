#include <jni.h>
#include <cmath>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

extern "C" {

/**
 * Computes normalized Root-Mean-Square (RMS) energy and Zero-Crossing Rate (ZCR)
 * on a 16-bit PCM audio frame for ultra-low-latency (<1ms) native VAD & Barge-In detection.
 * Returns a 2-element float array: [rmsNormalized, zeroCrossingRate].
 */
JNIEXPORT jfloatArray JNICALL
Java_com_aira_assistant_ai_local_NativeBridge_computeAudioMetricsNative(
        JNIEnv *env,
        jobject /* this */,
        jshortArray pcmBuffer,
        jint length) {
    jfloatArray result = env->NewFloatArray(2);
    if (length <= 0 || pcmBuffer == nullptr) {
        float zeros[2] = {0.0f, 0.0f};
        env->SetFloatArrayRegion(result, 0, 2, zeros);
        return result;
    }

    jshort *samples = env->GetShortArrayElements(pcmBuffer, nullptr);
    double sumSquares = 0.0;
    int zeroCrossings = 0;

    for (int i = 0; i < length; ++i) {
        double normalized = static_cast<double>(samples[i]) / 32768.0;
        sumSquares += normalized * normalized;
        if (i > 0) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) ||
                (samples[i] < 0 && samples[i - 1] >= 0)) {
                zeroCrossings++;
            }
        }
    }

    env->ReleaseShortArrayElements(pcmBuffer, samples, JNI_ABORT);

    float rms = static_cast<float>(std::sqrt(sumSquares / static_cast<double>(length)));
    float zcr = static_cast<float>(zeroCrossings) / static_cast<float>(length);

    float out[2] = {rms, zcr};
    env->SetFloatArrayRegion(result, 0, 2, out);
    return result;
}

/**
 * Validates whether a local model file on disk has a valid GGUF magic header ("GGUF").
 * Used before attempting mmap loading on Android devices to prevent native segfaults on partial downloads.
 */
JNIEXPORT jboolean JNICALL
Java_com_aira_assistant_ai_local_NativeBridge_validateGgufHeaderNative(
        JNIEnv *env,
        jobject /* this */,
        jstring filePath) {
    if (filePath == nullptr) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(filePath, nullptr);
    std::ifstream file(path, std::ios::binary);
    env->ReleaseStringUTFChars(filePath, path);

    if (!file.is_open()) return JNI_FALSE;

    char magic[4] = {0, 0, 0, 0};
    file.read(magic, 4);
    if (file.gcount() < 4) return JNI_FALSE;

    return (magic[0] == 'G' && magic[1] == 'G' && magic[2] == 'U' && magic[3] == 'F')
           ? JNI_TRUE
           : JNI_FALSE;
}

} // extern "C"
