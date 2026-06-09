package io.superkeyboard.settings.screens

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.superkeyboard.settings.SettingsViewModel

@Composable
fun SettingsNavHost(viewModel: SettingsViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainSettingsScreen(
                onNavigateToAppearance = { navController.navigate("appearance") },
                onNavigateToClipboard = { navController.navigate("clipboard") },
                onNavigateToAi = { navController.navigate("ai") },
                onNavigateToAbout = { navController.navigate("about") }
            )
        }
        composable("appearance") {
            AppearanceScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("clipboard") {
            ClipboardSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("ai") {
            AiSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("about") {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
