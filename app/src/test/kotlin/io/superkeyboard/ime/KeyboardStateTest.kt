package io.superkeyboard.ime

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * JVM unit tests for [KeyboardState] — a pure-Kotlin state machine with no Android deps.
 * Assertions target observable state (shiftState / layoutPage / isEmojiMode / isShifted),
 * not internals (anti-pattern A4). Boundary/no-op edges covered alongside happy paths (A3).
 */
class KeyboardStateTest {

    // --- Shift cycle -------------------------------------------------------

    @Test
    fun `toggleShift cycles OFF then ON then LOCKED then back to OFF`() {
        val state = KeyboardState()
        assertThat(state.shiftState).isEqualTo(ShiftState.OFF)

        state.toggleShift()
        assertThat(state.shiftState).isEqualTo(ShiftState.ON)

        state.toggleShift()
        assertThat(state.shiftState).isEqualTo(ShiftState.LOCKED)

        state.toggleShift()
        assertThat(state.shiftState).isEqualTo(ShiftState.OFF)
    }

    @Test
    fun `isShifted is true for ON and LOCKED, false for OFF`() {
        val state = KeyboardState()
        assertThat(state.isShifted).isFalse()

        state.toggleShift() // ON
        assertThat(state.isShifted).isTrue()

        state.toggleShift() // LOCKED
        assertThat(state.isShifted).isTrue()

        state.toggleShift() // OFF
        assertThat(state.isShifted).isFalse()
    }

    // --- onCharacterTyped --------------------------------------------------

    @Test
    fun `onCharacterTyped reverts ON to OFF`() {
        val state = KeyboardState()
        state.toggleShift() // ON

        state.onCharacterTyped()

        assertThat(state.shiftState).isEqualTo(ShiftState.OFF)
    }

    @Test
    fun `onCharacterTyped leaves LOCKED untouched`() {
        val state = KeyboardState()
        state.toggleShift() // ON
        state.toggleShift() // LOCKED

        state.onCharacterTyped()

        assertThat(state.shiftState).isEqualTo(ShiftState.LOCKED)
    }

    @Test
    fun `onCharacterTyped is a no-op when already OFF`() {
        val state = KeyboardState()

        state.onCharacterTyped()

        assertThat(state.shiftState).isEqualTo(ShiftState.OFF)
    }

    // --- setAutoShift ------------------------------------------------------

    @Test
    fun `setAutoShift true turns shift ON when not locked`() {
        val state = KeyboardState()

        state.setAutoShift(true)

        assertThat(state.shiftState).isEqualTo(ShiftState.ON)
    }

    @Test
    fun `setAutoShift false turns shift OFF when not locked`() {
        val state = KeyboardState()
        state.toggleShift() // ON

        state.setAutoShift(false)

        assertThat(state.shiftState).isEqualTo(ShiftState.OFF)
    }

    @Test
    fun `setAutoShift is ignored while LOCKED`() {
        val state = KeyboardState()
        state.toggleShift() // ON
        state.toggleShift() // LOCKED

        state.setAutoShift(false)
        assertThat(state.shiftState).isEqualTo(ShiftState.LOCKED)

        state.setAutoShift(true)
        assertThat(state.shiftState).isEqualTo(ShiftState.LOCKED)
    }

    // --- Symbols pages -----------------------------------------------------

    @Test
    fun `toggleSymbols flips between QWERTY and SYMBOLS_1`() {
        val state = KeyboardState()
        assertThat(state.layoutPage).isEqualTo(LayoutPage.QWERTY)

        state.toggleSymbols()
        assertThat(state.layoutPage).isEqualTo(LayoutPage.SYMBOLS_1)

        state.toggleSymbols()
        assertThat(state.layoutPage).isEqualTo(LayoutPage.QWERTY)
    }

    @Test
    fun `toggleSymbols from SYMBOLS_2 returns to QWERTY`() {
        val state = KeyboardState()
        state.toggleSymbols()       // SYMBOLS_1
        state.toggleSymbolsPage2()  // SYMBOLS_2

        state.toggleSymbols()

        assertThat(state.layoutPage).isEqualTo(LayoutPage.QWERTY)
    }

    @Test
    fun `toggleSymbolsPage2 flips between SYMBOLS_1 and SYMBOLS_2`() {
        val state = KeyboardState()
        state.toggleSymbols() // SYMBOLS_1

        state.toggleSymbolsPage2()
        assertThat(state.layoutPage).isEqualTo(LayoutPage.SYMBOLS_2)

        state.toggleSymbolsPage2()
        assertThat(state.layoutPage).isEqualTo(LayoutPage.SYMBOLS_1)
    }

    @Test
    fun `toggleSymbolsPage2 is a no-op from QWERTY`() {
        val state = KeyboardState()

        state.toggleSymbolsPage2()

        assertThat(state.layoutPage).isEqualTo(LayoutPage.QWERTY)
    }

    @Test
    fun `switchToAlpha forces QWERTY from any page`() {
        val state = KeyboardState()
        state.toggleSymbols()      // SYMBOLS_1
        state.switchToAlpha()
        assertThat(state.layoutPage).isEqualTo(LayoutPage.QWERTY)

        state.toggleSymbols()      // SYMBOLS_1
        state.toggleSymbolsPage2() // SYMBOLS_2
        state.switchToAlpha()
        assertThat(state.layoutPage).isEqualTo(LayoutPage.QWERTY)
    }

    // --- Emoji mode --------------------------------------------------------

    @Test
    fun `toggleEmojiMode flips the flag`() {
        val state = KeyboardState()
        assertThat(state.isEmojiMode).isFalse()

        state.toggleEmojiMode()
        assertThat(state.isEmojiMode).isTrue()

        state.toggleEmojiMode()
        assertThat(state.isEmojiMode).isFalse()
    }

    @Test
    fun `setEmojiMode sets the flag explicitly`() {
        val state = KeyboardState()

        state.setEmojiMode(true)
        assertThat(state.isEmojiMode).isTrue()

        state.setEmojiMode(false)
        assertThat(state.isEmojiMode).isFalse()
    }
}
