package io.superkeyboard.ai

/**
 * Pure-Kotlin AI orchestration core (ADR D3/D6/D7). NO Android types: it takes a plain [AiConfig]
 * and an [AiChatClient] seam, so the privacy egress guard is a runnable JVM unit test. The Android
 * wiring (reading SettingsRepository + AiKeyStore to build [AiConfig], constructing the OkHttp
 * client) lives in the IME layer (Batch D), not here.
 *
 * Gate order is privacy-critical (ADR D3): the disabled / unconfigured checks run BEFORE the client
 * is ever touched, so with AI off no network work happens.
 */
class AiEngine(
    private val client: AiChatClient,
    private val maxInputChars: Int = DEFAULT_MAX_INPUT_CHARS
) {

    /**
     * Run [action] over [inputText] using [config]. [presetPrompt] is the user's custom instruction,
     * required only for [AiAction.PRESET].
     *
     * Gates, in order:
     *  1. `!enabled`              -> [EngineResult.Disabled]      (client NEVER touched — invariant 1)
     *  2. endpoint/key/model blank -> [EngineResult.Unconfigured] (invariant 2: no hard-coded cloud)
     *  3. input blank              -> [EngineResult.Unconfigured]  (nothing to send)
     *     input > cap              -> [EngineResult.TooLong]       (bounded payload — I7/D7)
     *  4. else build [system, user] messages, call the client, map [AiResult] -> [EngineResult].
     */
    suspend fun run(
        action: AiAction,
        inputText: String,
        config: AiConfig,
        presetPrompt: String? = null
    ): EngineResult {
        // (1) Egress guard — return immediately, do not touch the client (ADR D3, privacy invariant 1).
        if (!config.enabled) return EngineResult.Disabled

        // (2) Unconfigured: user-owned endpoint only, no hard-coded cloud (privacy invariant 2).
        if (config.endpointUrl.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
            return EngineResult.Unconfigured
        }

        // (3) Input bounds (I2/I7). Blank => nothing to act on; over-cap => TooLong before any egress.
        if (inputText.isBlank()) return EngineResult.Unconfigured
        if (inputText.length > maxInputChars) return EngineResult.TooLong

        // (4) Live path: build the prompt and call the client.
        val systemPrompt = action.systemPrompt(config.targetLanguage, presetPrompt)
        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = inputText)
        )

        return when (val result = client.complete(
            endpointUrl = config.endpointUrl,
            apiKey = config.apiKey,
            model = config.model,
            messages = messages
        )) {
            is AiResult.Success -> EngineResult.Result(result.text)
            is AiResult.HttpError -> EngineResult.Error("HTTP ${result.code}: ${result.message}")
            is AiResult.NetworkError -> EngineResult.Error(result.message)
        }
    }

    /**
     * Test the user's [config] against their endpoint with a minimal request ("Test connection" in
     * AI Settings). Runs the SAME privacy gates as [run] in the same order (ADR D3):
     *  1. `!enabled`               -> [EngineResult.Disabled]      (client NEVER touched — invariant 1)
     *  2. endpoint/key/model blank -> [EngineResult.Unconfigured]  (invariant 2: no hard-coded cloud)
     *  3. else send a tiny system+user pair and map [AiResult] -> [EngineResult] exactly like [run].
     *
     * Because the probe is gated on `enabled` first, "AI off ⇒ no egress" still holds: testing the
     * connection requires AI to be ON. There is no path here that calls the network when disabled.
     */
    suspend fun probe(config: AiConfig): EngineResult {
        // (1) Egress guard — return immediately, do not touch the client (ADR D3, invariant 1).
        if (!config.enabled) return EngineResult.Disabled

        // (2) Unconfigured: user-owned endpoint only (invariant 2).
        if (config.endpointUrl.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
            return EngineResult.Unconfigured
        }

        // (3) Minimal probe payload — no user field contents are involved.
        val messages = listOf(
            ChatMessage(role = "system", content = PROBE_SYSTEM_PROMPT),
            ChatMessage(role = "user", content = PROBE_USER_MESSAGE)
        )

        return when (val result = client.complete(
            endpointUrl = config.endpointUrl,
            apiKey = config.apiKey,
            model = config.model,
            messages = messages
        )) {
            is AiResult.Success -> EngineResult.Result(result.text)
            is AiResult.HttpError -> EngineResult.Error("HTTP ${result.code}: ${result.message}")
            is AiResult.NetworkError -> EngineResult.Error(result.message)
        }
    }

    private companion object {
        const val DEFAULT_MAX_INPUT_CHARS = 8000
        const val PROBE_SYSTEM_PROMPT = "You are a connection test. Reply with the single word: OK"
        const val PROBE_USER_MESSAGE = "ping"
    }
}

/**
 * Plain, JVM-friendly snapshot of the AI configuration the engine needs. Built in the IME layer from
 * SettingsRepository (enabled/endpoint/model/language) + AiKeyStore (the decrypted key) — never
 * constructed with a hard-coded endpoint or key.
 */
data class AiConfig(
    val enabled: Boolean,
    val endpointUrl: String,
    val apiKey: String,
    val model: String,
    val targetLanguage: String
)

/** Outcome of [AiEngine.run]. The IME renders each variant in the preview panel. */
sealed interface EngineResult {
    /** AI master switch is off — show the "set up AI in Settings" hint; no network occurred. */
    data object Disabled : EngineResult

    /** AI is on but endpoint/key/model (or input) is missing — prompt the user to finish setup. */
    data object Unconfigured : EngineResult

    /** Input exceeded the character cap — ask the user to shorten the selection. */
    data object TooLong : EngineResult

    /** Success — [text] is the model's output, ready to preview and apply. */
    data class Result(val text: String) : EngineResult

    /** A surfaced HTTP or network failure (I1) — [message] is shown in the preview. */
    data class Error(val message: String) : EngineResult
}
