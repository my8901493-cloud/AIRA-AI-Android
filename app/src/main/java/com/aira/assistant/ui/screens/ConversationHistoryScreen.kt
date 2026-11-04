package com.aira.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aira.assistant.domain.model.MessageRole
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
fun ConversationHistoryScreen(
    viewModel: AiraViewModel,
    onBack: () -> Unit
) {
    val sessions by viewModel.conversations.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()

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
                    text = "Conversation History",
                    style = MaterialTheme.typography.titleLarge,
                    color = AiraTextPrimary
                )
            }
            IconButton(onClick = { viewModel.startNewConversation() }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Session",
                    tint = AiraBlueAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Current Session Transcript (${messages.size} messages)",
            style = MaterialTheme.typography.titleMedium,
            color = AiraBlueAccent
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.role == MessageRole.USER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AiraGlassSurface)
                        .border(
                            width = 1.dp,
                            color = if (isUser) AiraBlueAccent.copy(alpha = 0.45f) else AiraPurplePrimary.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (isUser) "You" else "AIRA AI",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) AiraBlueAccent else AiraPurplePrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AiraTextPrimary
                    )
                }
            }

            if (sessions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Saved Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        color = AiraTextSecondary
                    )
                }
                items(sessions) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AiraGlassSurface)
                            .border(1.dp, AiraGlassBorder, RoundedCornerShape(14.dp))
                            .clickable { viewModel.selectConversation(session.id) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium, color = AiraTextPrimary)
                            Text("${session.messageCount} messages", style = MaterialTheme.typography.bodyMedium, color = AiraTextSecondary)
                        }
                        IconButton(onClick = { viewModel.deleteConversation(session.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = AiraErrorCoral)
                        }
                    }
                }
            }
        }
    }
}
