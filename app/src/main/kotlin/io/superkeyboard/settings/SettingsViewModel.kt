package io.superkeyboard.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.superkeyboard.R
import io.superkeyboard.SuperKeyboardApp
import io.superkeyboard.ai.AiConfig
import io.superkeyboard.ai.AiEngine
import io.superkeyboard.ai.AiKeyStore
import io.superkeyboard.ai.AiPreset
import io.superkeyboard.ai.AiPresets
import io.superkeyboard.ai.EngineResult
import io.superkeyboard.ai.OkHttpAiChatClient
import io.superkeyboard.clipboard.ClipboardRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val clipboardRepository = ClipboardRepository(
        (application as SuperKeyboardApp).clipboardDatabase.clipboardDao()
    )

    // The API key lives only in the Keystore-encrypted AiKeyStore, never in DataStore or UI state.
    private val aiKeyStore = AiKeyStore(application)

    // Test-connection engine. The OkHttp client is built lazily inside OkHttpAiChatClient, so simply
    // constructing this opens no socket — the engine's egress gates run first (ADR D3, invariant 1).
    private val aiEngine = AiEngine(OkHttpAiChatClient())

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

    // --- AI Action Engine settings (E2) ---

    val aiEnabled: StateFlow<Boolean> = settingsRepository.aiEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val aiEndpointUrl: StateFlow<String> = settingsRepository.aiEndpointUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val aiModel: StateFlow<String> = settingsRepository.aiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val aiTargetLang: StateFlow<String> = settingsRepository.aiTargetLang
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "English")

    val aiPresets: StateFlow<List<AiPreset>> = settingsRepository.aiPresetsJson
        .map { AiPresets.decode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Write-only key surface: the UI sees only whether a key is set, never the key itself (ADR D2).
    private val _hasApiKey = MutableStateFlow(aiKeyStore.getKey()?.isNotBlank() == true)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    // Test-connection status string for the AI Settings screen. null = idle (nothing shown yet).
    private val _testConnectionStatus = MutableStateFlow<String?>(null)
    val testConnectionStatus: StateFlow<String?> = _testConnectionStatus.asStateFlow()

    // True while a probe is in flight, so the UI can disable the button.
    private val _testConnectionInFlight = MutableStateFlow(false)
    val testConnectionInFlight: StateFlow<Boolean> = _testConnectionInFlight.asStateFlow()

    fun setThemeMode(mode: String) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setKeyboardHeightFactor(factor: Float) = viewModelScope.launch { settingsRepository.setKeyboardHeightFactor(factor) }
    fun setHapticEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setHapticEnabled(enabled) }
    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    fun setClipboardEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setClipboardEnabled(enabled) }
    fun setClipboardExpiryHours(hours: Int) = viewModelScope.launch { settingsRepository.setClipboardExpiryHours(hours) }

    fun clearClipboardHistory() = viewModelScope.launch { clipboardRepository.clearAll() }

    // --- AI setters ---

    fun setAiEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAiEnabled(enabled); _testConnectionStatus.value = null
    }
    fun setAiEndpointUrl(url: String) = viewModelScope.launch {
        settingsRepository.setAiEndpointUrl(url); _testConnectionStatus.value = null
    }
    fun setAiModel(model: String) = viewModelScope.launch {
        settingsRepository.setAiModel(model); _testConnectionStatus.value = null
    }
    fun setAiTargetLang(lang: String) = viewModelScope.launch { settingsRepository.setAiTargetLang(lang) }

    /**
     * Probe the configured endpoint with a minimal request and publish a human-readable result to
     * [testConnectionStatus]. Builds [AiConfig] from the current settings flows + the decrypted key;
     * the egress gates inside [AiEngine.probe] still hold (AI must be ON), so this opens no socket
     * when AI is disabled. Errors are now diagnosable (carry HTTP code + body snippet).
     */
    fun testConnection() = viewModelScope.launch {
        _testConnectionInFlight.value = true
        _testConnectionStatus.value = getApplication<Application>().getString(R.string.settings_ai_test_testing)
        val config = AiConfig(
            enabled = aiEnabled.first(),
            endpointUrl = aiEndpointUrl.first(),
            apiKey = aiKeyStore.getKey() ?: "",
            model = aiModel.first(),
            targetLanguage = aiTargetLang.first()
        )
        val app = getApplication<Application>()
        _testConnectionStatus.value = when (val result = aiEngine.probe(config)) {
            is EngineResult.Result -> app.getString(R.string.settings_ai_test_connected)
            EngineResult.Disabled -> app.getString(R.string.settings_ai_test_disabled)
            EngineResult.Unconfigured -> app.getString(R.string.settings_ai_test_unconfigured)
            is EngineResult.Error -> result.message
            EngineResult.TooLong -> app.getString(R.string.settings_ai_test_unconfigured) // n/a for a fixed probe
        }
        _testConnectionInFlight.value = false
    }

    /** Encrypt and persist a new API key (write-only). Blank input is treated as a clear. */
    fun setApiKey(key: String) {
        if (key.isBlank()) {
            clearApiKey()
            return
        }
        aiKeyStore.setKey(key)
        _hasApiKey.value = true
    }

    /** Delete the stored API key. */
    fun clearApiKey() {
        aiKeyStore.clear()
        _hasApiKey.value = false
    }

    // --- Preset operations: decode -> mutate -> re-encode -> persist ---

    fun addPreset(name: String, prompt: String) = updatePresets { it + AiPreset(name.trim(), prompt.trim()) }

    fun updatePreset(index: Int, name: String, prompt: String) = updatePresets { current ->
        if (index !in current.indices) return@updatePresets current
        current.toMutableList().also { it[index] = AiPreset(name.trim(), prompt.trim()) }
    }

    fun deletePreset(index: Int) = updatePresets { current ->
        if (index !in current.indices) return@updatePresets current
        current.toMutableList().also { it.removeAt(index) }
    }

    private fun updatePresets(transform: (List<AiPreset>) -> List<AiPreset>) = viewModelScope.launch {
        val current = AiPresets.decode(settingsRepository.aiPresetsJson.first())
        settingsRepository.setAiPresetsJson(AiPresets.encode(transform(current)))
    }
}
