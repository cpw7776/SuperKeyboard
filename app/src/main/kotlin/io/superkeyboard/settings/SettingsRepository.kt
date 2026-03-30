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
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "system" }
    val keyboardHeightFactor: Flow<Float> = context.dataStore.data.map { it[KEYBOARD_HEIGHT_FACTOR] ?: 1.0f }
    val hapticEnabled: Flow<Boolean> = context.dataStore.data.map { it[HAPTIC_ENABLED] ?: true }
    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { it[SOUND_ENABLED] ?: false }
    val clipboardEnabled: Flow<Boolean> = context.dataStore.data.map { it[CLIPBOARD_ENABLED] ?: true }
    val clipboardExpiryHours: Flow<Int> = context.dataStore.data.map { it[CLIPBOARD_EXPIRY_HOURS] ?: 24 }

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
}
