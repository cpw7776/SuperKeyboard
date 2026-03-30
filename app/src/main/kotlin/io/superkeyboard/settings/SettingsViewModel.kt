package io.superkeyboard.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.superkeyboard.SuperKeyboardApp
import io.superkeyboard.clipboard.ClipboardRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val clipboardRepository = ClipboardRepository(
        (application as SuperKeyboardApp).clipboardDatabase.clipboardDao()
    )

    val themeMode: StateFlow<String> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val keyboardHeightFactor: StateFlow<Float> = settingsRepository.keyboardHeightFactor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val hapticEnabled: StateFlow<Boolean> = settingsRepository.hapticEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val soundEnabled: StateFlow<Boolean> = settingsRepository.soundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val clipboardEnabled: StateFlow<Boolean> = settingsRepository.clipboardEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val clipboardExpiryHours: StateFlow<Int> = settingsRepository.clipboardExpiryHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 24)

    fun setThemeMode(mode: String) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setKeyboardHeightFactor(factor: Float) = viewModelScope.launch { settingsRepository.setKeyboardHeightFactor(factor) }
    fun setHapticEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticEnabled(enabled) }
    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    fun setClipboardEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setClipboardEnabled(enabled) }
    fun setClipboardExpiryHours(hours: Int) = viewModelScope.launch { settingsRepository.setClipboardExpiryHours(hours) }

    fun clearClipboardHistory() = viewModelScope.launch { clipboardRepository.clearAll() }
}
