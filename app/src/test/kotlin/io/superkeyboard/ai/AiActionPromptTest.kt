package io.superkeyboard.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM unit tests for [AiAction.systemPrompt]. Asserts the produced system prompt carries the right
 * instruction per action (A1: behaviour, not the exact template wording is over-asserted — we check
 * the distinguishing intent words) and that the two parameterised actions thread their parameter:
 * TRANSLATE must name the target language; PRESET must use the user's custom prompt. PRESET with a
 * null/blank prompt is rejected (A3: boundary/invalid input).
 */
class AiActionPromptTest {

    @Test
    fun `TRANSLATE prompt includes the target language`() {
        val prompt = AiAction.TRANSLATE.systemPrompt(targetLanguage = "French", presetPrompt = null)

        assertThat(prompt).ignoringCase().contains("translate")
        assertThat(prompt).contains("French")
    }

    @Test
    fun `TRANSLATE prompt reflects a different target language`() {
        val prompt = AiAction.TRANSLATE.systemPrompt(targetLanguage = "Japanese", presetPrompt = null)

        assertThat(prompt).contains("Japanese")
        assertThat(prompt).doesNotContain("French")
    }

    @Test
    fun `REWRITE prompt instructs a rewrite`() {
        val prompt = AiAction.REWRITE.systemPrompt(targetLanguage = "English", presetPrompt = null)

        assertThat(prompt).ignoringCase().contains("rewrite")
    }

    @Test
    fun `SUMMARIZE prompt instructs a summary`() {
        val prompt = AiAction.SUMMARIZE.systemPrompt(targetLanguage = "English", presetPrompt = null)

        assertThat(prompt).ignoringCase().contains("summar")
    }

    @Test
    fun `EXPAND prompt instructs an expansion`() {
        val prompt = AiAction.EXPAND.systemPrompt(targetLanguage = "English", presetPrompt = null)

        assertThat(prompt).ignoringCase().contains("expand")
    }

    @Test
    fun `PRESET prompt uses the user-supplied prompt verbatim`() {
        val custom = "Translate to pirate speak and add an emoji"

        val prompt = AiAction.PRESET.systemPrompt(targetLanguage = "English", presetPrompt = custom)

        assertThat(prompt).contains(custom)
    }

    @Test
    fun `PRESET with null prompt is rejected`() {
        try {
            AiAction.PRESET.systemPrompt(targetLanguage = "English", presetPrompt = null)
            throw AssertionError("expected an exception for a null preset prompt")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `PRESET with blank prompt is rejected`() {
        try {
            AiAction.PRESET.systemPrompt(targetLanguage = "English", presetPrompt = "   ")
            throw AssertionError("expected an exception for a blank preset prompt")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
