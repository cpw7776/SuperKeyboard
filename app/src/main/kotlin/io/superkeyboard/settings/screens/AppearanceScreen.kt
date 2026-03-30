package io.superkeyboard.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.superkeyboard.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val keyboardHeight by viewModel.keyboardHeightFactor.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Theme selector
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                    FilterChip(
                        selected = themeMode == value,
                        onClick = { viewModel.setThemeMode(value) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Keyboard height
            Text(
                text = "Keyboard Height",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Small", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = keyboardHeight,
                    onValueChange = { viewModel.setKeyboardHeightFactor(it) },
                    valueRange = 0.8f..1.4f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("Large", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Haptic feedback toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Haptic Feedback", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Vibrate on keypress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hapticEnabled,
                    onCheckedChange = { viewModel.setHapticEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sound toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Sound on Keypress", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Play click sound when typing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = { viewModel.setSoundEnabled(it) }
                )
            }
        }
    }
}
