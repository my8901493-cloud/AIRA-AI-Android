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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.aira.assistant.domain.model.MemoryCategory
import com.aira.assistant.ui.theme.AiraBackgroundDark
import com.aira.assistant.ui.theme.AiraBlueAccent
import com.aira.assistant.ui.theme.AiraErrorCoral
import com.aira.assistant.ui.theme.AiraGlassBorder
import com.aira.assistant.ui.theme.AiraGlassSurface
import com.aira.assistant.ui.theme.AiraPurplePrimary
import com.aira.assistant.ui.theme.AiraTextPrimary
import com.aira.assistant.ui.theme.AiraTextSecondary
import com.aira.assistant.ui.viewmodel.AiraViewModel

@Composable
fun MemoryManagerScreen(
    viewModel: AiraViewModel,
    onBack: () -> Unit
) {
    val memories by viewModel.memories.collectAsState()
    val memoryEnabled by viewModel.memorySavingEnabled.collectAsState()

    var newSubject by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AiraBackgroundDark)
            .padding(16.dp)
    ) {
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
                    text = "Long-Term Memory Vault",
                    style = MaterialTheme.typography.titleLarge,
                    color = AiraTextPrimary
                )
            }
            IconButton(onClick = { viewModel.clearAllMemories() }) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear All Memories",
                    tint = AiraErrorCoral
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AiraGlassSurface)
                .border(1.dp, AiraGlassBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Consent to Save Personal Memories", style = MaterialTheme.typography.titleMedium, color = AiraTextPrimary)
                Text("Automatically deduplicates & blocks sensitive IDs/passwords", style = MaterialTheme.typography.bodyMedium, color = AiraTextSecondary)
            }
            Switch(
                checked = memoryEnabled,
                onCheckedChange = { viewModel.setMemorySavingEnabled(it) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Add Manual Memory
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newSubject,
                onValueChange = { newSubject = it },
                placeholder = { Text("Topic (e.g. diet)") },
                singleLine = true,
                modifier = Modifier.weight(0.4f)
            )
            OutlinedTextField(
                value = newContent,
                onValueChange = { newContent = it },
                placeholder = { Text("Memory fact…") },
                singleLine = true,
                modifier = Modifier.weight(0.6f)
            )
            Button(
                onClick = {
                    if (newSubject.isNotBlank() && newContent.isNotBlank()) {
                        viewModel.addManualMemory(MemoryCategory.USER_PREFERENCE, newSubject, newContent)
                        newSubject = ""
                        newContent = ""
                    }
                }
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(memories) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AiraGlassSurface)
                        .border(1.dp, AiraGlassBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${item.category.displayName} • ${item.subjectKey}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AiraBlueAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AiraTextPrimary
                        )
                    }
                    IconButton(onClick = { viewModel.deleteMemory(item.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Memory",
                            tint = AiraPurplePrimary
                        )
                    }
                }
            }
        }
    }
}
