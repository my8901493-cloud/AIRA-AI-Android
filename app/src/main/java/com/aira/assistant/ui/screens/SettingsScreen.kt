package com.aira.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.AiraLanguage
import com.aira.assistant.ui.theme.AiraBackgroundDark
import com.aira.assistant.ui.theme.AiraBlueAccent
import com.aira.assistant.ui.theme.AiraCyanGlow
import com.aira.assistant.ui.theme.AiraErrorCoral
import com.aira.assistant.ui.theme.AiraGlassBorder
import com.aira.assistant.ui.theme.AiraGlassSurface
import com.aira.assistant.ui.theme.AiraOnlineEmerald
import com.aira.assistant.ui.theme.AiraTextPrimary
import com.aira.assistant.ui.theme.AiraTextSecondary
import com.aira.assistant.ui.viewmodel.AiraViewModel

@Composable
fun SettingsScreen(
    viewModel: AiraViewModel,
    onBack: () -> Unit
) {
    val models by viewModel.modelsCatalog.collectAsState()
    val hardware by viewModel.hardwareProfile.collectAsState()
    val language by viewModel.selectedLanguage.collectAsState()
    val continuous by viewModel.continuousMode.collectAsState()
    val voiceSpeed by viewModel.voiceSpeed.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val logs by AiraLogger.logs.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AiraBackgroundDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AiraTextPrimary
                    )
                }
                Text("Settings & Model Manager", style = MaterialTheme.typography.titleLarge, color = AiraTextPrimary)
            }
        }

        // 1. Hardware & Storage Diagnostics
        item {
            SectionCard(title = "Device Benchmark & Resource Profile") {
                Text("Total RAM: ${hardware.totalRamMb} MB (Available: ${hardware.availableRamMb} MB)", color = AiraTextPrimary)
                Text("Free Storage: ${hardware.availableStorageMb} MB • CPU Cores: ${hardware.cpuCores}", color = AiraTextSecondary)
                Text(
                    "Recommended Max On-Device LLM Size: <= ${hardware.recommendedMaxLlmSizeMb} MB",
                    color = AiraCyanGlow
                )
                Text(
                    "Voice Pipeline Telemetry: First-Token=${telemetry.llmFirstTokenLatencyMs}ms • First-Audio=${telemetry.ttsFirstAudioLatencyMs}ms • Barge-In Cancel=${telemetry.bargeInCancelLatencyMs}ms • AEC=${telemetry.isEchoCancelerEnabled}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AiraTextSecondary
                )
            }
        }

        // 2. Language & Voice Controls
        item {
            SectionCard(title = "Language, Voice & Conversation") {
                Text("Primary Assistant Language:", color = AiraTextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiraLanguage.entries.forEach { lang ->
                        Button(
                            onClick = { viewModel.setLanguage(lang) }
                        ) {
                            Text(if (lang == language) "✓ ${lang.displayName}" else lang.displayName)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Speech Rate (${String.format("%.2f", voiceSpeed)}x)", color = AiraTextPrimary)
                Slider(
                    value = voiceSpeed,
                    onValueChange = { viewModel.setVoiceSpeed(it) },
                    valueRange = 0.6f..1.6f
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Continuous Full-Duplex Listening", color = AiraTextPrimary)
                    Switch(checked = continuous, onCheckedChange = { viewModel.setContinuousMode(it) })
                }
            }
        }

        // 3. AI, STT & TTS Model Catalog
        item {
            Text("AI, STT & TTS Model Management", style = MaterialTheme.typography.titleMedium, color = AiraBlueAccent)
        }

        items(models) { spec ->
            val compatible = viewModel.modelCatalogManager.isModelCompatibleWithDevice(spec, hardware)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AiraGlassSurface)
                    .border(1.dp, AiraGlassBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text("${spec.modality} • ${spec.name}", style = MaterialTheme.typography.titleMedium, color = AiraTextPrimary)
                Text(spec.description, style = MaterialTheme.typography.bodyMedium, color = AiraTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Size: ${spec.fileSizeMb} MB • Min RAM: ${spec.minRamMb} MB • Hindi/Hinglish: ${spec.supportsHindi}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (compatible) AiraOnlineEmerald else AiraErrorCoral
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.selectModel(spec) }) {
                        Text("Use Model", color = AiraCyanGlow)
                    }
                    if (spec.downloadUrl.isNotBlank()) {
                        if (!spec.isDownloaded) {
                            TextButton(
                                onClick = { viewModel.downloadModel(spec) },
                                enabled = compatible
                            ) {
                                Text(if (compatible) "Download" else "Exceeds Device RAM")
                            }
                        } else {
                            TextButton(onClick = { viewModel.deleteModelFile(spec) }) {
                                Text("Delete File", color = AiraErrorCoral)
                            }
                        }
                    }
                }
            }
        }

        // 4. Diagnostics & About AIRA AI
        item {
            SectionCard(title = "About AIRA AI & Diagnostics (${logs.size} events)") {
                Text("AIRA AI v1.0.0 — Native Kotlin + Jetpack Compose Personal AI Assistant", color = AiraTextPrimary)
                Text("Offline-First • Zero Mandatory Paid APIs • Hardware Echo Cancellation & Barge-In", color = AiraTextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                logs.takeLast(5).forEach { entry ->
                    Text(
                        "[${entry.timestamp}] ${entry.tag}: ${entry.message}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AiraTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AiraGlassSurface)
            .border(1.dp, AiraGlassBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AiraBlueAccent)
        content()
    }
}
