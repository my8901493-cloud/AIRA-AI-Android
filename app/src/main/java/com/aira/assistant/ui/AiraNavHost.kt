package com.aira.assistant.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aira.assistant.ui.screens.ConversationHistoryScreen
import com.aira.assistant.ui.screens.MainAssistantScreen
import com.aira.assistant.ui.screens.MemoryManagerScreen
import com.aira.assistant.ui.screens.SettingsScreen
import com.aira.assistant.ui.viewmodel.AiraViewModel

@Composable
fun AiraNavHost(
    viewModel: AiraViewModel,
    onRequestMicPermissionAndToggle: () -> Unit,
    onToggleScreenSharing: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainAssistantScreen(
                viewModel = viewModel,
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToMemories = { navController.navigate("memories") },
                onNavigateToSettings = { navController.navigate("settings") },
                onRequestMicPermissionAndToggle = onRequestMicPermissionAndToggle,
                onToggleScreenSharing = onToggleScreenSharing
            )
        }
        composable("history") {
            ConversationHistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("memories") {
            MemoryManagerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
