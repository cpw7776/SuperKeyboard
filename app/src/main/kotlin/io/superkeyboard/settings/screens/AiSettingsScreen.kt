package io.superkeyboard.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.superkeyboard.R
import io.superkeyboard.ai.AiPreset
import io.superkeyboard.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val enabled by viewModel.aiEnabled.collectAsState()
    val endpointUrl by viewModel.aiEndpointUrl.collectAsState()
    val model by viewModel.aiModel.collectAsState()
    val targetLang by viewModel.aiTargetLang.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()
    val presets by viewModel.aiPresets.collectAsState()

    // Local, write-only buffer for the key field — never seeded from the stored key (ADR D2).
    var apiKeyDraft by remember { mutableStateOf("") }

    // Preset editor dialog state: null = closed; index = -1 means "add new".
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_ai)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_ai_cancel))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Master enable switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_ai_enabled), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_ai_enabled_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = { viewModel.setAiEnabled(it) })
            }

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // Endpoint URL
            OutlinedTextField(
                value = endpointUrl,
                onValueChange = { viewModel.setAiEndpointUrl(it) },
                label = { Text(stringResource(R.string.settings_ai_endpoint_label)) },
                placeholder = { Text(stringResource(R.string.settings_ai_endpoint_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Model
            OutlinedTextField(
                value = model,
                onValueChange = { viewModel.setAiModel(it) },
                label = { Text(stringResource(R.string.settings_ai_model_label)) },
                placeholder = { Text(stringResource(R.string.settings_ai_model_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Default translate language
            OutlinedTextField(
                value = targetLang,
                onValueChange = { viewModel.setAiTargetLang(it) },
                label = { Text(stringResource(R.string.settings_ai_target_lang_label)) },
                placeholder = { Text(stringResource(R.string.settings_ai_target_lang_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // API key — masked, write-only. Never render the stored key.
            OutlinedTextField(
                value = apiKeyDraft,
                onValueChange = { apiKeyDraft = it },
                label = { Text(stringResource(R.string.settings_ai_api_key_label)) },
                placeholder = { Text(stringResource(R.string.settings_ai_api_key_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = if (hasApiKey) {
                    { Text(stringResource(R.string.settings_ai_api_key_set)) }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.setApiKey(apiKeyDraft)
                        apiKeyDraft = ""
                    },
                    enabled = apiKeyDraft.isNotBlank()
                ) {
                    Text(stringResource(R.string.settings_ai_api_key_save))
                }
                OutlinedButton(
                    onClick = {
                        viewModel.clearApiKey()
                        apiKeyDraft = ""
                    },
                    enabled = hasApiKey,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.settings_ai_api_key_clear))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_ai_presets), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { editingIndex = -1 }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_ai_preset_add))
                }
            }

            if (presets.isEmpty()) {
                Text(
                    stringResource(R.string.settings_ai_presets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                presets.forEachIndexed { index, preset ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                preset.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editingIndex = index }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.settings_ai_preset_edit))
                        }
                        IconButton(onClick = { viewModel.deletePreset(index) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.settings_ai_preset_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Privacy note
            Text(
                stringResource(R.string.settings_ai_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    editingIndex?.let { index ->
        val existing = presets.getOrNull(index)
        PresetEditorDialog(
            initial = existing,
            onDismiss = { editingIndex = null },
            onConfirm = { name, prompt ->
                if (index == -1) viewModel.addPreset(name, prompt)
                else viewModel.updatePreset(index, name, prompt)
                editingIndex = null
            }
        )
    }
}

@Composable
private fun PresetEditorDialog(
    initial: AiPreset?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, prompt: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var prompt by remember { mutableStateOf(initial?.prompt ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.settings_ai_preset_add else R.string.settings_ai_preset_edit
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_ai_preset_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.settings_ai_preset_prompt_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, prompt) },
                enabled = name.isNotBlank() && prompt.isNotBlank()
            ) {
                Text(stringResource(R.string.settings_ai_preset_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_ai_cancel))
            }
        }
    )
}
