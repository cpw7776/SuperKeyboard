package io.superkeyboard.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM unit tests for [AiPresets] (de)serialization. Covers the round trip and the I1 invariant:
 * [AiPresets.decode] must NEVER throw — blank and malformed input degrade to an empty list so a
 * corrupt `ai_presets_json` preference can't crash the settings UI (A3: empty/invalid boundaries).
 */
class AiPresetsTest {

    @Test
    fun `encode then decode round-trips the presets`() {
        val presets = listOf(
            AiPreset(name = "Pirate", prompt = "Rewrite in pirate speak"),
            AiPreset(name = "Formal", prompt = "Make this sound professional")
        )

        val decoded = AiPresets.decode(AiPresets.encode(presets))

        assertThat(decoded).isEqualTo(presets)
    }

    @Test
    fun `encode then decode round-trips an empty list`() {
        val decoded = AiPresets.decode(AiPresets.encode(emptyList()))

        assertThat(decoded).isEmpty()
    }

    @Test
    fun `decode of empty string returns empty list`() {
        assertThat(AiPresets.decode("")).isEmpty()
    }

    @Test
    fun `decode of blank string returns empty list`() {
        assertThat(AiPresets.decode("   ")).isEmpty()
    }

    @Test
    fun `decode of garbage returns empty list and does not throw`() {
        assertThat(AiPresets.decode("garbage")).isEmpty()
        assertThat(AiPresets.decode("{not valid json")).isEmpty()
        assertThat(AiPresets.decode("{\"name\":\"x\"}")).isEmpty() // object, not the expected array
    }
}
