package com.aira.assistant.ui

import com.aira.assistant.ai.ModelCatalogManager
import com.aira.assistant.domain.model.DeviceHardwareProfile
import com.aira.assistant.domain.model.ModelModality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiraViewModelTest {

    @Test
    fun modelCatalogEnforcesDeviceRamLimits() {
        val manager = ModelCatalogManager(context = null)
        val catalog = manager.getAvailableModels()

        val lowRamPhone = DeviceHardwareProfile(
            totalRamMb = 3072L,
            availableRamMb = 1200L,
            availableStorageMb = 8192L,
            cpuCores = 8,
            supportedAbis = listOf("arm64-v8a"),
            isLowRamDevice = true
        )

        val gemma2B = catalog.first { it.id == "gemma-2-2b-it-q4_k_m" }
        val qwen05B = catalog.first { it.id == "qwen2.5-0.5b-instruct-q4_k_m" }

        assertFalse("2B model (min 6GB RAM) must be flagged incompatible on 3GB RAM device", manager.isModelCompatibleWithDevice(gemma2B, lowRamPhone))
        assertTrue("0.5B model must be compatible on 3GB RAM device", manager.isModelCompatibleWithDevice(qwen05B, lowRamPhone))
        assertTrue(catalog.any { it.modality == ModelModality.STT })
        assertTrue(catalog.any { it.modality == ModelModality.TTS })
    }
}
