package io.superkeyboard.ai

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Pure (de)serialization helpers for the user's [AiPreset] list, persisted as a JSON string in
 * DataStore (`ai_presets_json`). No Android types — runnable as a JVM unit test.
 *
 * [decode] is total (I1): blank or malformed input returns an empty list rather than throwing, so a
 * corrupt/legacy preference value can never crash the settings UI or the IME.
 */
object AiPresets {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(AiPreset.serializer())

    /** Serialize the presets to a JSON array string suitable for DataStore storage. */
    fun encode(presets: List<AiPreset>): String = json.encodeToString(listSerializer, presets)

    /** Parse a JSON array string into presets. Returns an empty list on blank/invalid input (I1). */
    fun decode(raw: String): List<AiPreset> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString(listSerializer, raw)
        } catch (_: Exception) {
            // Corrupt or legacy value — degrade to empty rather than crash the UI (I1).
            emptyList()
        }
    }
}
