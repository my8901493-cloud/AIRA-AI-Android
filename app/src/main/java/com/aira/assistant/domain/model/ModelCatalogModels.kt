package com.aira.assistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ModelModality {
    LLM,
    STT,
    TTS,
    VISION
}

@Serializable
enum class ModelProviderType(val label: String) {
    LOCAL_ON_DEVICE("On-Device (Offline)"),
    LOCAL_NETWORK("Local Network Server (Ollama / llama.cpp)"),
    ANDROID_SYSTEM("Android Native Engine"),
    OPTIONAL_CLOUD("Optional Cloud Endpoint")
}

@Serializable
data class AiModelSpec(
    val id: String,
    val name: String,
    val modality: ModelModality,
    val providerType: ModelProviderType,
    val description: String,
    val fileSizeMb: Int,
    val minRamMb: Int,
    val supportsHindi: Boolean,
    val supportsHinglish: Boolean,
    val supportsStreaming: Boolean,
    val downloadUrl: String = "",
    val sha256Checksum: String = "",
    val fileName: String = "",
    val isDownloaded: Boolean = false,
    val isDefault: Boolean = false
)

@Serializable
data class DeviceHardwareProfile(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val availableStorageMb: Long,
    val cpuCores: Int,
    val supportedAbis: List<String>,
    val isLowRamDevice: Boolean
) {
    val recommendedMaxLlmSizeMb: Int
        get() = when {
            totalRamMb >= 11500 -> 2600 // 12GB+ RAM -> up to ~2.5B/3B Q4_K_M
            totalRamMb >= 7500 -> 1600  // 8GB RAM -> Gemma-2-2B Q4_K_M / Llama-3.2-1B
            totalRamMb >= 5500 -> 950   // 6GB RAM -> Qwen2.5-1.5B / Llama-3.2-1B Q4
            else -> 550                 // 4GB RAM -> Qwen2.5-0.5B Q4 / SmolLM2-360M
        }
}
