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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aira.assistant.domain.model.AutonomousTaskStage
import com.aira.assistant.domain.model.AutonomousTaskSubmission
import com.aira.assistant.domain.model.OperatorExecutionState
import com.aira.assistant.ui.theme.AiraBackgroundDark
import com.aira.assistant.ui.theme.AiraBlueAccent
import com.aira.assistant.ui.theme.AiraCyanGlow
import com.aira.assistant.ui.theme.AiraErrorCoral
import com.aira.assistant.ui.theme.AiraGlassBorder
import com.aira.assistant.ui.theme.AiraGlassSurface
import com.aira.assistant.ui.theme.AiraOnlineEmerald
import com.aira.assistant.ui.theme.AiraPurplePrimary
import com.aira.assistant.ui.theme.AiraTextPrimary
import com.aira.assistant.ui.theme.AiraTextSecondary
import com.aira.assistant.ui.viewmodel.AiraViewModel

@Composable
fun AutonomousTaskScreen(
    viewModel: AiraViewModel,
    onPickFile: ((String) -> Unit) -> Unit = {},
    onBack: () -> Unit
) {
    val activeTask by viewModel.activeAutonomousTask.collectAsState()
    val operatorRecord by viewModel.activeOperatorRecord.collectAsState()
    val taskHistory by viewModel.autonomousTaskHistory.collectAsState()

    var objectiveText by remember { mutableStateOf("") }
    var codeOrScriptText by remember { mutableStateOf("") }
    var attachedFilePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var videoAspect by remember { mutableStateOf("16:9") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AiraBackgroundDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AiraTextPrimary
                        )
                    }
                    Text(
                        text = "AIRA Autonomous Operator & Studio",
                        style = MaterialTheme.typography.titleLarge,
                        color = AiraTextPrimary
                    )
                }

                // Emergency Stop Screen Control Button
                OutlinedButton(
                    onClick = { viewModel.emergencyStopAllOperations() }
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, tint = AiraErrorCoral)
                    Text(" STOP SCREEN", color = AiraErrorCoral)
                }
            }
        }

        // 1. Goal & Task Submission Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AiraGlassSurface)
                    .border(1.dp, AiraGlassBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Give AIRA Any Goal in Hindi, Hinglish, or English",
                    style = MaterialTheme.typography.titleMedium,
                    color = AiraCyanGlow
                )

                OutlinedTextField(
                    value = objectiveText,
                    onValueChange = { objectiveText = it },
                    label = { Text("Goal (Chrome, YouTube, Video Editor, APK Build, Free Tool Search)") },
                    placeholder = { Text("e.g., Chrome kholo aur is topic par research karo / Editing app kholo aur video edit karo") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = codeOrScriptText,
                    onValueChange = { codeOrScriptText = it },
                    label = { Text("Optional Script, Narration Text, or Source Code") },
                    placeholder = { Text("Paste Hindi/English script or code here…") },
                    minLines = 3,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { videoAspect = if (videoAspect == "16:9") "9:16" else "16:9" }) {
                            Text("Format: $videoAspect")
                        }
                        OutlinedButton(
                            onClick = {
                                onPickFile { path ->
                                    attachedFilePaths = attachedFilePaths + path
                                }
                            }
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null)
                            Text("Attach (${attachedFilePaths.size})")
                        }
                    }

                    Button(
                        onClick = {
                            if (objectiveText.isNotBlank() || codeOrScriptText.isNotBlank() || attachedFilePaths.isNotEmpty()) {
                                viewModel.submitAutonomousTask(
                                    AutonomousTaskSubmission(
                                        promptOrInstruction = objectiveText.ifBlank { "Execute autonomous workflow on supplied input" },
                                        attachedFiles = attachedFilePaths,
                                        pastedCodeOrScript = codeOrScriptText,
                                        preferredVideoAspect = videoAspect
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Run Operator")
                    }
                }
            }
        }

        // 2. Real-World Android Operator 13-State Monitor & Transparent Activity Log
        operatorRecord?.let { op ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AiraGlassSurface)
                        .border(1.dp, AiraCyanGlow, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Android Operator State: ${op.state.displayLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (op.state == OperatorExecutionState.COMPLETED) AiraOnlineEmerald else AiraCyanGlow
                    )
                    Text(
                        text = "Selected Tool/App: ${op.selectedToolOrApp} (${op.selectionReason})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AiraTextPrimary
                    )
                    Text(
                        text = op.currentStepDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AiraTextSecondary
                    )

                    // Missing App Installation Approval Card
                    op.pendingInstallProposal?.let { prop ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, AiraErrorCoral, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Missing App Approval Required: ${prop.recommendedAppName}", color = AiraErrorCoral)
                            Text("Why needed: ${prop.rationaleExplanation}", color = AiraTextPrimary)
                            Text("Pricing: ${prop.pricingSummary} • Permissions: ${prop.requiredPermissionsSummary}", color = AiraTextSecondary)
                            Text("Official Store Link: ${prop.officialStoreUri}", color = AiraBlueAccent)
                            Button(onClick = { viewModel.approveWaitingAutonomousTask() }) {
                                Text("Approve & Open Official Play Store")
                            }
                        }
                    }

                    // Transparent Operator Activity Log
                    if (op.activityLog.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Transparent Operator Activity Log:", style = MaterialTheme.typography.labelMedium, color = AiraBlueAccent)
                        op.activityLog.takeLast(8).forEach { entry ->
                            Text(
                                text = "• [${entry.state.name}] ${entry.targetAppOrWebsite}: ${entry.actionPerformed} (${entry.verificationNote})",
                                style = MaterialTheme.typography.labelMedium,
                                color = AiraTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3. Autonomous Studio Step-by-Step Progress
        activeTask?.let { task ->
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AiraGlassSurface)
                        .border(1.dp, AiraPurplePrimary, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${task.category.title} • ${task.stage.displayLabel}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (task.stage == AutonomousTaskStage.COMPLETED) AiraOnlineEmerald else AiraCyanGlow
                        )
                        if (!task.stage.isTerminal) {
                            IconButton(onClick = { viewModel.cancelActiveAutonomousTask() }) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Cancel Task Safely",
                                    tint = AiraErrorCoral
                                )
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { task.progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = AiraCyanGlow
                    )

                    task.steps.forEach { step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${step.stepNumber}. ${step.title} [${step.status}]",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AiraTextPrimary
                            )
                            if (step.status == "COMPLETED") {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AiraOnlineEmerald)
                            }
                        }
                    }

                    if (task.outputFiles.isNotEmpty()) {
                        Text("Verified Output Deliverables:", style = MaterialTheme.typography.titleMedium, color = AiraOnlineEmerald)
                        task.outputFiles.forEach { filePath ->
                            Text("✓ $filePath", style = MaterialTheme.typography.bodyMedium, color = AiraTextPrimary)
                        }
                    }
                }
            }
        }

        if (taskHistory.isNotEmpty()) {
            item {
                Text("Completed Task History (${taskHistory.size})", style = MaterialTheme.typography.titleMedium, color = AiraBlueAccent)
            }
            items(taskHistory) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AiraGlassSurface)
                        .border(1.dp, AiraGlassBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.category.title} — ${item.stage.displayLabel}", color = AiraTextPrimary)
                        Text("Outputs: ${item.outputFiles.size} file(s)", style = MaterialTheme.typography.labelMedium, color = AiraTextSecondary)
                    }
                }
            }
        }
    }
}
