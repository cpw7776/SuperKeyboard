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
fun ClipboardSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val clipboardEnabled by viewModel.clipboardEnabled.collectAsState()
    val expiryHours by viewModel.clipboardExpiryHours.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clipboard") },
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
            // Enable clipboard history
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Clipboard History", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Store copied text locally (encrypted)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = clipboardEnabled,
                    onCheckedChange = { viewModel.setClipboardEnabled(it) }
                )
            }

            HorizontalDivider()

            // Expiry duration
            Text(
                text = "Auto-expire after",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            val options = listOf(1 to "1 hour", 6 to "6 hours", 12 to "12 hours", 24 to "24 hours", 48 to "48 hours", 0 to "Never")
            Column {
                options.forEach { (hours, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = expiryHours == hours,
                            onClick = { viewModel.setClipboardExpiryHours(hours) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Clear all
            OutlinedButton(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear All Clipboard History")
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear clipboard history?") },
            text = { Text("This will permanently delete all stored clipboard entries, including pinned items.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearClipboardHistory()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
