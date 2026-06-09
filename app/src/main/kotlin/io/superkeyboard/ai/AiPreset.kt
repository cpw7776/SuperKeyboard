package io.superkeyboard.ai

import kotlinx.serialization.Serializable

/**
 * A user-defined AI preset: a named, reusable custom instruction (system prompt) for the
 * [AiAction.PRESET] action. Stored serialized as JSON in DataStore (`ai_presets_json`); the API key
 * never lives here. Presets are user-authored on-device only (no cloud, no defaults shipped).
 */
@Serializable
data class AiPreset(
    val name: String,
    val prompt: String
)
