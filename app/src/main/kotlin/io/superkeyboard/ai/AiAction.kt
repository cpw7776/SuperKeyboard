package io.superkeyboard.ai

/**
 * The five AI toolbar actions, typed (not stringly — I5 / ADR D6). The toolbar passes one of these
 * enum values into [AiEngine]; the engine turns it into a system prompt via [systemPrompt].
 *
 * Pure Kotlin, no Android types — so the engine and its prompts are JVM-unit-testable (the privacy
 * egress guard must be a runnable JVM test, not instrumented).
 */
enum class AiAction {
    TRANSLATE,
    REWRITE,
    SUMMARIZE,
    EXPAND,
    PRESET
}

/**
 * Build the system prompt for this action.
 *
 * @param targetLanguage the language [TRANSLATE] should translate into (ignored by other actions).
 * @param presetPrompt the user's custom instruction; REQUIRED (non-blank) for [PRESET], ignored
 *   otherwise. A null/blank prompt for [PRESET] is a programming error and throws (I2 — validate at
 *   the boundary; the engine gates this case before calling, so this is a belt-and-braces check).
 */
fun AiAction.systemPrompt(targetLanguage: String, presetPrompt: String?): String = when (this) {
    AiAction.TRANSLATE ->
        "You are a translation engine. Translate the user's text into $targetLanguage. " +
            "Output only the translation, with no commentary, quotes, or explanation."

    AiAction.REWRITE ->
        "Rewrite the user's text to be clearer and more polished while preserving its meaning and " +
            "tone. Output only the rewritten text, with no commentary."

    AiAction.SUMMARIZE ->
        "Summarize the user's text concisely, keeping the key points. " +
            "Output only the summary, with no commentary."

    AiAction.EXPAND ->
        "Expand the user's text into a fuller, more detailed version while preserving its meaning " +
            "and tone. Output only the expanded text, with no commentary."

    AiAction.PRESET -> {
        require(!presetPrompt.isNullOrBlank()) { "PRESET action requires a non-blank preset prompt" }
        presetPrompt
    }
}
