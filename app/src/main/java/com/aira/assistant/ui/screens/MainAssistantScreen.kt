package com.aira.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aira.assistant.domain.model.VoiceState
import com.aira.assistant.ui.components.AiraOrb
import com.aira.assistant.ui.components.ScreenSharingEdgeGlow
import com.aira.assistant.ui.theme.AiraBackgroundDark
import com.aira.assistant.ui.theme.AiraBlueAccent
import com.aira.assistant.ui.theme.AiraCyanGlow
import com.aira.assistant.ui.theme.AiraErrorCoral
import com.aira.assistant.ui.theme.AiraGlassBorder
import com.aira.assistant.ui.theme.AiraGlassSurface
import com.aira.assistant.ui.theme.AiraOfflineAmber
import com.aira.assistant.ui.theme.AiraOnlineEmerald
import com.aira.assistant.ui.theme.AiraPurpleDeep
import com.aira.assistant.ui.theme.AiraPurplePrimary
import com.aira.assistant.ui.theme.AiraTextPrimary
import com.aira.assistant.ui.theme.AiraTextSecondary
import com.aira.assistant.ui.viewmodel.AiraViewModel

@Composable
fun MainAssistantScreen(
    viewModel: AiraViewModel,
    onNavigateToAutonomousStudio: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRequestMicPermissionAndToggle: () -> Unit,
    onToggleScreenSharing: () -> Unit
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val audioLevel by viewModel.orbAudioLevel.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isScreenSharing by viewModel.isScreenSharingActive.collectAsState()
    val continuousMode by viewModel.continuousMode.collectAsState()
    val pendingPlan by viewModel.pendingConfirmationPlan.collectAsState()

    var quickInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        AiraBackgroundDark,
                        Color(0xFF0B0E22),
                        AiraBackgroundDark
                    )
                )
            )
    ) {
        ScreenSharingEdgeGlow(isActive = isScreenSharing)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Glassmorphic Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AiraGlassSurface)
                    .border(1.dp, AiraGlassBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) AiraOnlineEmerald else AiraOfflineAmber)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOnline) "AIRA • Online" else "AIRA • Offline Local",
                        style = MaterialTheme.typography.titleMedium,
                        color = AiraTextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateToAutonomousStudio) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Autonomous Execution Studio",
                            tint = AiraCyanGlow
                        )
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Conversation History",
                            tint = AiraTextPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToMemories) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Saved Memories",
                            tint = AiraTextPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AiraTextPrimary
                        )
                    }
                }
            }

            if (isScreenSharing) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = AiraPurpleDeep.copy(alpha = 0.65f),
                    modifier = Modifier.border(1.dp, AiraCyanGlow, RoundedCornerShape(50))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenShare,
                            contentDescription = null,
                            tint = AiraCyanGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Screen Assistant Active",
                            style = MaterialTheme.typography.labelMedium,
                            color = AiraTextPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(onClick = onToggleScreenSharing) {
                            Text("Stop", color = AiraErrorCoral)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 3. Central Animated AI Orb (Zero text clutter underneath)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AiraOrb(
                    voiceState = voiceState,
                    audioLevel = audioLevel,
                    orbSize = 265.dp,
                    onOrbTap = onRequestMicPermissionAndToggle
                )
            }

            // 4. Bottom Glassmorphic Control Dock
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickInput,
                        onValueChange = { quickInput = it },
                        placeholder = {
                            Text(
                                "Ask AIRA or assign an autonomous task…",
                                color = AiraTextSecondary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (quickInput.isNotBlank()) {
                                viewModel.sendTextOrVoiceUtterance(quickInput)
                                quickInput = ""
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = AiraBlueAccent
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(AiraGlassSurface)
                        .border(1.dp, AiraGlassBorder, RoundedCornerShape(32.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.setContinuousMode(!continuousMode) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Continuous Conversation Mode",
                            tint = if (continuousMode) AiraCyanGlow else AiraTextSecondary
                        )
                    }

                    FloatingActionButton(
                        onClick = onRequestMicPermissionAndToggle,
                        shape = CircleShape,
                        containerColor = when (voiceState) {
                            VoiceState.LISTENING -> AiraCyanGlow
                            VoiceState.SPEAKING -> AiraPurplePrimary
                            VoiceState.ERROR -> AiraErrorCoral
                            else -> AiraPurplePrimary
                        }
                    ) {
                        Icon(
                            imageVector = if (voiceState.isActiveSession) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Microphone Control",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = onToggleScreenSharing) {
                        Icon(
                            imageVector = if (isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                            contentDescription = "Screen Assistant Toggle",
                            tint = if (isScreenSharing) AiraErrorCoral else AiraTextSecondary
                        )
                    }
                }
            }
        }

        pendingPlan?.let { plan ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissPendingAgentAction() },
                title = { Text("Confirm Assistant Action") },
                text = {
                    Text(plan.confirmationMessage ?: "Allow AIRA to execute: ${plan.detectedIntent}?")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmPendingAgentAction() }) {
                        Text("Allow", color = AiraCyanGlow)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissPendingAgentAction() }) {
                        Text("Cancel", color = AiraTextSecondary)
                    }
                }
            )
        }
    }
}
