package io.superkeyboard.ime

import android.graphics.RectF

data class Key(
    val label: String,
    val code: Int,
    val widthWeight: Float = 1f,
    val isRepeatable: Boolean = false,
    val longPressKeys: List<String> = emptyList(),
    val icon: KeyIcon? = null
) {
    var bounds: RectF = RectF()
    var isPressed: Boolean = false

    fun contains(x: Float, y: Float): Boolean = bounds.contains(x, y)
}

enum class KeyIcon {
    BACKSPACE,
    SHIFT,
    SHIFT_LOCKED,
    ENTER,
    EMOJI,
    LANGUAGE,
    SPACE,
    SYMBOLS
}

object KeyCodes {
    const val SHIFT = -1
    const val BACKSPACE = -2
    const val SYMBOLS = -3
    const val EMOJI = -4
    const val ENTER = -5
    const val SPACE = 32
    const val COMMA = 44
    const val PERIOD = 46
    const val SYMBOLS_PAGE_2 = -6
    const val ALPHA = -7
}
