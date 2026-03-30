package io.superkeyboard.ime

enum class ShiftState {
    OFF,
    ON,       // Single shift — next char uppercase, then revert
    LOCKED    // Caps lock — all uppercase until toggled off
}

enum class LayoutPage {
    QWERTY,
    SYMBOLS_1,
    SYMBOLS_2
}

class KeyboardState {
    var shiftState: ShiftState = ShiftState.OFF
        private set
    var layoutPage: LayoutPage = LayoutPage.QWERTY
        private set
    var isEmojiMode: Boolean = false
        private set

    val isShifted: Boolean
        get() = shiftState != ShiftState.OFF

    fun toggleShift() {
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> ShiftState.LOCKED
            ShiftState.LOCKED -> ShiftState.OFF
        }
    }

    fun onCharacterTyped() {
        if (shiftState == ShiftState.ON) {
            shiftState = ShiftState.OFF
        }
    }

    fun setAutoShift(capitalize: Boolean) {
        if (shiftState != ShiftState.LOCKED) {
            shiftState = if (capitalize) ShiftState.ON else ShiftState.OFF
        }
    }

    fun toggleSymbols() {
        layoutPage = when (layoutPage) {
            LayoutPage.QWERTY -> LayoutPage.SYMBOLS_1
            LayoutPage.SYMBOLS_1 -> LayoutPage.QWERTY
            LayoutPage.SYMBOLS_2 -> LayoutPage.QWERTY
        }
    }

    fun toggleSymbolsPage2() {
        layoutPage = when (layoutPage) {
            LayoutPage.SYMBOLS_1 -> LayoutPage.SYMBOLS_2
            LayoutPage.SYMBOLS_2 -> LayoutPage.SYMBOLS_1
            else -> layoutPage
        }
    }

    fun switchToAlpha() {
        layoutPage = LayoutPage.QWERTY
    }

    fun toggleEmojiMode() {
        isEmojiMode = !isEmojiMode
    }

    fun setEmojiMode(enabled: Boolean) {
        isEmojiMode = enabled
    }
}
