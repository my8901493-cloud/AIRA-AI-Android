package com.aira.assistant.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.aira.assistant.ai.local.NativeBridge
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.AiModelSpec
import com.aira.assistant.domain.model.DeviceHardwareProfile
import com.aira.assistant.domain.model.ModelModality
import com.aira.assistant.domain.model.ModelProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Benchmarks target Android device hardware (RAM, free storage, CPU cores, ABIs)
 * before recommending model sizes, and manages downloadable open-source LLM, STT, and TTS models.
 */
class ModelCatalogManager(
    private val context: Context? = null,
    private val modelsDirOverride: File? = null
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    val modelsDir: File by lazy {
        modelsDirOverride ?: File(context?.filesDir ?: File(System.getProperty("java.io.tmpdir")), "aira_models").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    fun profileHardware(): DeviceHardwareProfile {
        return try {
            if (context != null) {
                val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actMgr.getMemoryInfo(memInfo)

                val statFs = StatFs(Environment.getDataDirectory().path)
                val freeStorageMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024L * 1024L)

                DeviceHardwareProfile(
                    totalRamMb = memInfo.totalMem / (1024L * 1024L),
                    availableRamMb = memInfo.availMem / (1024L * 1024L),
                    availableStorageMb = freeStorageMb,
                    cpuCores = Runtime.getRuntime().availableProcessors(),
                    supportedAbis = Build.SUPPORTED_ABIS.toList(),
                    isLowRamDevice = actMgr.isLowRamDevice
                )
            } else {
                val rt = Runtime.getRuntime()
                DeviceHardwareProfile(
                    totalRamMb = 8192L,
                    availableRamMb = 4096L,
                    availableStorageMb = 16384L,
                    cpuCores = rt.availableProcessors(),
                    supportedAbis = listOf("arm64-v8a", "x86_64"),
                    isLowRamDevice = false
                )
            }
        } catch (e: Exception) {
            AiraLogger.w("ModelCatalogManager", "Hardware profiling fallback used: ${e.message}")
            DeviceHardwareProfile(
                totalRamMb = 6144L,
                availableRamMb = 3072L,
                availableStorageMb = 8192L,
                cpuCores = 8,
                supportedAbis = listOf("arm64-v8a"),
                isLowRamDevice = false
            )
        }
    }

    fun getAvailableModels(): List<AiModelSpec> {
        val baseCatalog = listOf(
            // LLM Models (Graduated by RAM tier — never blindly downloading oversized models)
            AiModelSpec(
                id = "aira-local-contextual-v1",
                name = "AIRA Built-In Instant Engine (Zero-Download)",
                modality = ModelModality.LLM,
                providerType = ModelProviderType.LOCAL_ON_DEVICE,
                description = "Built-in ultra-fast bilingual (Hindi/Hinglish/English) conversational + tool reasoning engine. Works immediately offline with zero external download.",
                fileSizeMb = 0,
                minRamMb = 512,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = true,
                isDownloaded = true,
                isDefault = true
            ),
            AiModelSpec(
                id = "qwen2.5-0.5b-instruct-q4_k_m",
                name = "Qwen 2.5 0.5B Instruct (Q4_K_M GGUF)",
                modality = ModelModality.LLM,
                providerType = ModelProviderType.LOCAL_ON_DEVICE,
                description = "Ultra-lightweight on-device GGUF model ideal for 4GB RAM Android phones. Fast token streaming & Hinglish/English support.",
                fileSizeMb = 398,
                minRamMb = 2048,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = true,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
            ),
            AiModelSpec(
                id = "llama-3.2-1b-instruct-q4_k_m",
                name = "Llama 3.2 1B Instruct (Q4_K_M GGUF)",
                modality = ModelModality.LLM,
                providerType = ModelProviderType.LOCAL_ON_DEVICE,
                description = "Balanced on-device GGUF model recommended for 6GB+ RAM devices. Strong multilingual instruction following.",
                fileSizeMb = 808,
                minRamMb = 4096,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = true,
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
            ),
            AiModelSpec(
                id = "gemma-2-2b-it-q4_k_m",
                name = "Google Gemma 2 2B Instruct (Q4_K_M GGUF)",
                modality = ModelModality.LLM,
                providerType = ModelProviderType.LOCAL_ON_DEVICE,
                description = "High-capability open weights model for 8GB+ RAM flagship Android devices. Rich Hindi, Hinglish, and coding reasoning.",
                fileSizeMb = 1630,
                minRamMb = 6144,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = true,
                downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                fileName = "gemma-2-2b-it-Q4_K_M.gguf"
            ),
            AiModelSpec(
                id = "local-network-ollama",
                name = "Local Network LLM (Ollama / llama.cpp LAN Server)",
                modality = ModelModality.LLM,
                providerType = ModelProviderType.LOCAL_NETWORK,
                description = "Connects over Wi-Fi LAN to a self-hosted Ollama or llama.cpp OpenAI-compatible server with zero RAM overhead on phone.",
                fileSizeMb = 0,
                minRamMb = 512,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = true,
                isDownloaded = true
            ),

            // STT Models
            AiModelSpec(
                id = "android-speech-recognizer",
                name = "Android Streaming SpeechRecognizer (Offline + Online)",
                modality = ModelModality.STT,
                providerType = ModelProviderType.ANDROID_SYSTEM,
                description = "Low-latency partial transcription using Android's built-in on-device/system speech engine with en-IN & hi-IN support.",
                fileSizeMb = 0,
                minRamMb = 256,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = true,
                isDownloaded = true,
                isDefault = true
            ),
            AiModelSpec(
                id = "whisper-tiny-multilingual-q5",
                name = "Whisper.cpp Tiny Multilingual (Q5_1)",
                modality = ModelModality.STT,
                providerType = ModelProviderType.LOCAL_ON_DEVICE,
                description = "Compact 32MB offline Whisper model supporting English & Hindi transcription.",
                fileSizeMb = 32,
                minRamMb = 512,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = false,
                downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
                fileName = "ggml-tiny-q5_1.bin"
            ),
            AiModelSpec(
                id = "vosk-model-small-hi-en",
                name = "Vosk Small Streaming Hindi + Indian English",
                modality = ModelModality.STT,
                providerType = ModelProviderType.LOCAL_ON_DEVICE,
                description = "Offline streaming acoustic model (~45MB) tuned for Indian English & Hindi accents.",
                fileSizeMb = 45,
                minRamMb = 512,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = true,
                downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
                fileName = "vosk-model-small-en-in-0.4.bin"
            ),

            // TTS Models
            AiModelSpec(
                id = "android-native-female-tts",
                name = "AIRA Natural Female Voice (Android Neural/Local TTS)",
                modality = ModelModality.TTS,
                providerType = ModelProviderType.ANDROID_SYSTEM,
                description = "Default female voice using sentence-chunked streaming playback with Hindi (hi-IN) and Indian English (en-IN) switching.",
                fileSizeMb = 0,
                minRamMb = 256,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = true,
                isDownloaded = true,
                isDefault = true
            ),
            AiModelSpec(
                id = "piper-en_in-pratham-medium",
                name = "Piper Offline Neural TTS (Indian English / Hindi Female)",
                modality = ModelModality.TTS,
                providerType = ModelProviderType.LOCAL_ON_DEVICE,
                description = "Fast ONNX local neural voice for sub-250ms offline synthesis.",
                fileSizeMb = 64,
                minRamMb = 512,
                supportsHindi = true,
                supportsHinglish = true,
                supportsStreaming = true,
                downloadUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/priyamvada/medium/hi_IN-priyamvada-medium.onnx",
                fileName = "hi_IN-priyamvada-medium.onnx"
            )
        )

        return baseCatalog.map { spec ->
            if (spec.fileName.isNotBlank()) {
                val localFile = File(modelsDir, spec.fileName)
                spec.copy(isDownloaded = localFile.exists() && localFile.length() > 1024L)
            } else {
                spec
            }
        }
    }

    fun isModelCompatibleWithDevice(model: AiModelSpec, profile: DeviceHardwareProfile = profileHardware()): Boolean {
        if (model.minRamMb > profile.totalRamMb) return false
        if (!model.isDownloaded && model.fileSizeMb > profile.availableStorageMb - 250) return false
        return true
    }

    suspend fun downloadModel(model: AiModelSpec): Result<File> = withContext(Dispatchers.IO) {
        if (model.downloadUrl.isBlank() || model.fileName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Model ${model.name} is built-in and does not require download."))
        }
        val profile = profileHardware()
        if (!isModelCompatibleWithDevice(model, profile)) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Device has ${profile.totalRamMb}MB RAM / ${profile.availableStorageMb}MB free storage; " +
                        "${model.name} requires at least ${model.minRamMb}MB RAM and ${model.fileSizeMb}MB storage."
                )
            )
        }

        val targetFile = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, "${model.fileName}.part")

        try {
            _downloadProgress.value = _downloadProgress.value + (model.id to 0.01f)
            val request = Request.Builder().url(model.downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure( IllegalStateException("HTTP ${response.code} downloading ${model.name}"))
                }
                val body = response.body ?: return@withContext Result.failure(IllegalStateException("Empty response body"))
                val totalBytes = body.contentLength().coerceAtLeast(1L)
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            val fraction = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 0.99f)
                            _downloadProgress.value = _downloadProgress.value + (model.id to fraction)
                        }
                        output.flush()
                    }
                }
            }

            if (model.fileName.endsWith(".gguf", ignoreCase = true)) {
                if (!NativeBridge.isValidGgufFile(tempFile)) {
                    tempFile.delete()
                    return@withContext Result.failure(IllegalStateException("Downloaded file failed GGUF magic header verification."))
                }
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
            _downloadProgress.value = _downloadProgress.value - model.id
            AiraLogger.i("ModelCatalogManager", "Successfully downloaded model ${model.name} to ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            tempFile.delete()
            _downloadProgress.value = _downloadProgress.value - model.id
            AiraLogger.e("ModelCatalogManager", "Failed downloading ${model.name}", e)
            Result.failure(e)
        }
    }

    fun deleteModel(model: AiModelSpec): Boolean {
        if (model.fileName.isBlank()) return false
        val file = File(modelsDir, model.fileName)
        return if (file.exists()) {
            val deleted = file.delete()
            AiraLogger.i("ModelCatalogManager", "Deleted model file ${model.fileName}: $deleted")
            deleted
        } else {
            false
        }
    }

    fun getStorageUsedByModelsMb(): Long {
        if (!modelsDir.exists()) return 0L
        return (modelsDir.listFiles()?.sumOf { it.length() } ?: 0L) / (1024L * 1024L)
    }
}
