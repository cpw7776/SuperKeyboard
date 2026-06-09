package io.superkeyboard.ime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM unit tests for [resolveTarget] — the pure text-targeting rule behind AI actions (D3 STOP).
 * Asserts observable behavior (which text + hadSelection), covering the no-selection and
 * empty-everything edges (anti-patterns A1, A3).
 */
class TextTargetingTest {

    @Test
    fun `non-empty selection is returned with hadSelection true`() {
        val target = resolveTarget(selectedText = "picked", fullText = "the whole field text")

        assertThat(target.text).isEqualTo("picked")
        assertThat(target.hadSelection).isTrue()
    }

    @Test
    fun `null selection falls back to full text with hadSelection false`() {
        val target = resolveTarget(selectedText = null, fullText = "the whole field text")

        assertThat(target.text).isEqualTo("the whole field text")
        assertThat(target.hadSelection).isFalse()
    }

    @Test
    fun `empty selection falls back to full text with hadSelection false`() {
        val target = resolveTarget(selectedText = "", fullText = "the whole field text")

        assertThat(target.text).isEqualTo("the whole field text")
        assertThat(target.hadSelection).isFalse()
    }

    @Test
    fun `empty everything yields empty text and no selection`() {
        val target = resolveTarget(selectedText = null, fullText = "")

        assertThat(target.text).isEmpty()
        assertThat(target.hadSelection).isFalse()
    }
}
