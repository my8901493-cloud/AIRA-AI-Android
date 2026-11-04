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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.aira.assistant.domain.model.AutonomousTaskStage
import com.aira.assistant.domain.model.AutonomousTaskSubmission
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

/**
 * Autonomous Task Submission & Progress Execution Interface (`AutonomousTaskScreen`).
 *
 * Allows the user to:
 * - Type a natural-language objective (e.g., "Build APK from this project", "Make a 30s Hindi video from this script", "Generate a voiceover MP3", "Fix this broken Kotlin code").
 * - Paste source code or a full video/voiceover script.
 * - Attach project ZIPs or local files.
 * - Observe real progress stages (`Analyzing -> Planning -> Selecting Tools -> Executing -> Validating -> Completed`).
 * - Inspect automatic tool selection decisions, pause/cancel active execution safely, and view verified output deliverables.
 */
@Composable
fun AutonomousTaskScreen(
    viewModel: AiraViewModel,
    onPickFile: ((String) -> Unit) -> Unit = {},
    onBack: () -> Unit
) {
    val activeTask by viewModel.activeAutonomousTask.collectAsState()
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
                        text = "AIRA Autonomous Execution Studio",
                        style = MaterialTheme.typography.titleLarge,
                        color = AiraTextPrimary
                    )
                }
            }
        }

        // 1. Task Submission Card
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
                    text = "Submit Any Objective, Script, Code, or Project",
                    style = MaterialTheme.typography.titleMedium,
                    color = AiraCyanGlow
                )

                OutlinedTextField(
                    value = objectiveText,
                    onValueChange = { objectiveText = it },
                    label = { Text("What should AIRA accomplish?") },
                    placeholder = { Text("e.g., Produce a 30s Hindi MP4 video / Build APK / Generate Voiceover MP3 / Fix broken code") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = codeOrScriptText,
                    onValueChange = { codeOrScriptText = it },
                    label = { Text("Paste Script, Narration Text, or Source Code (Optional)") },
                    placeholder = { Text("Paste Hindi/English video script, narration text, or Kotlin/Java/Python code here…") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { videoAspect = if (videoAspect == "16:9") "9:16" else "16:9" }) {
                            Text("Video Format: $videoAspect")
                        }
                        OutlinedButton(
                            onClick = {
                                onPickFile { path ->
                                    attachedFilePaths = attachedFilePaths + path
                                }
                            }
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null)
                            Text("Attach File/ZIP (${attachedFilePaths.size})")
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
                        Text("Start Autonomous Execution")
                    }
                }
            }
        }

        // 2. Live Progress Stage Stepper & Active Execution Monitor
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${task.category.title} • ${task.stage.displayLabel}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (task.stage == AutonomousTaskStage.COMPLETED) AiraOnlineEmerald else AiraCyanGlow
                            )
                            Text(
                                text = "Analyzing → Planning → Selecting Tools → Executing → Validating → Completed",
                                style = MaterialTheme.typography.labelMedium,
                                color = AiraTextSecondary
                            )
                        }
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

                    if (task.stage == AutonomousTaskStage.WAITING_APPROVAL) {
                        Text(
                            text = task.requiresApprovalReason ?: "Authorization required before proceeding.",
                            color = AiraErrorCoral
                        )
                        Button(onClick = { viewModel.approveWaitingAutonomousTask() }) {
                            Text("Approve & Continue")
                        }
                    }

                    // Autonomous Tool Selection Decisions
                    if (task.toolDecisions.isNotEmpty()) {
                        Text("Autonomous Tool Selection:", style = MaterialTheme.typography.labelMedium, color = AiraBlueAccent)
                        task.toolDecisions.forEach { dec ->
                            Text(
                                text = "• ${dec.capabilityRequired}: ${dec.selectionRationale}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AiraTextSecondary
                            )
                        }
                    }

                    // Step-by-Step Progress
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
                        if (step.logSummary.isNotBlank()) {
                            Text(
                                text = "   ↳ ${step.logSummary}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AiraTextSecondary
                            )
                        }
                    }

                    // Generated Deliverables & Validation Report
                    if (task.outputFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Verified Output Deliverables:", style = MaterialTheme.typography.titleMedium, color = AiraOnlineEmerald)
                        task.outputFiles.forEach { filePath ->
                            Text("✓ $filePath", style = MaterialTheme.typography.bodyMedium, color = AiraTextPrimary)
                        }
                    }

                    task.validationReport?.let { valReport ->
                        Text(
                            text = "Quality Gate: ${if (valReport.passed) "PASSED" else "FAILED"} (${valReport.fileSizeBytes} bytes) • Checks: ${valReport.checksPerformed.entries.joinToString { "${it.key}=${it.value}" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (valReport.passed) AiraOnlineEmerald else AiraErrorCoral
                        )
                    }
                }
            }
        }

        // 3. Past Autonomous Tasks History
        if (taskHistory.isNotEmpty()) {
            item {
                Text("Autonomous Task History (${taskHistory.size})", style = MaterialTheme.typography.titleMedium, color = AiraBlueAccent)
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
