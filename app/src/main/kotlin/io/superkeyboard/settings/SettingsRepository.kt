package io.superkeyboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "system", "light", "dark"
        val KEYBOARD_HEIGHT_FACTOR = floatPreferencesKey("keyboard_height_factor") // 0.8 - 1.4
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val CLIPBOARD_ENABLED = booleanPreferencesKey("clipboard_enabled")
        val CLIPBOARD_EXPIRY_HOURS = intPreferencesKey("clipboard_expiry_hours") // 1, 6, 12, 24, 48, 0=never

        // AI Action Engine (E2). The API *key* is NOT here — it is Keystore-encrypted via AiKeyStore
        // (ADR D2/D3, privacy invariant). Default OFF so no egress can occur without explicit opt-in.
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_ENDPOINT_URL = stringPreferencesKey("ai_endpoint_url")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_TARGET_LANG = stringPreferencesKey("ai_target_lang")
        val AI_PRESETS_JSON = stringPreferencesKey("ai_presets_json") // serialized List<AiPreset>
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "system" }
    val keyboardHeightFactor: Flow<Float> = context.dataStore.data.map { it[KEYBOARD_HEIGHT_FACTOR] ?: 1.0f }
    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { it[HAPTIC_ENABLED] ?: true }
    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { it[SOUND_ENABLED] ?: false }
    val clipboardEnabled: Flow<Boolean> = context.dataStore.data.map { it[CLIPBOARD_ENABLED] ?: true }
    val clipboardExpiryHours: Flow<Int> = context.dataStore.data.map { it[CLIPBOARD_EXPIRY_HOURS] ?: 24 }

    val aiEnabled: Flow<Boolean> = context.dataStore.data.map { it[AI_ENABLED] ?: false }
    val aiEndpointUrl: Flow<String> = context.dataStore.data.map { it[AI_ENDPOINT_URL] ?: "" }
    val aiModel: Flow<String> = context.dataStore.data.map { it[AI_MODEL] ?: "" }
    val aiTargetLang: Flow<String> = context.dataStore.data.map { it[AI_TARGET_LANG] ?: "English" }
    val aiPresetsJson: Flow<String> = context.dataStore.data.map { it[AI_PRESETS_JSON] ?: "[]" }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setKeyboardHeightFactor(factor: Float) {
        context.dataStore.edit { it[KEYBOARD_HEIGHT_FACTOR] = factor }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[HAPTIC_ENABLED] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SOUND_ENABLED] = enabled }
    }

    suspend fun setClipboardEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CLIPBOARD_ENABLED] = enabled }
    }

    suspend fun setClipboardExpiryHours(hours: Int) {
        context.dataStore.edit { it[CLIPBOARD_EXPIRY_HOURS] = hours }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AI_ENABLED] = enabled }
    }

    suspend fun setAiEndpointUrl(url: String) {
        context.dataStore.edit { it[AI_ENDPOINT_URL] = url }
    }

    suspend fun setAiModel(model: String) {
        context.dataStore.edit { it[AI_MODEL] = model }
    }

    suspend fun setAiTargetLang(lang: String) {
        context.dataStore.edit { it[AI_TARGET_LANG] = lang }
    }

    suspend fun setAiPresetsJson(json: String) {
        context.dataStore.edit { it[AI_PRESETS_JSON] = json }
    }
}
