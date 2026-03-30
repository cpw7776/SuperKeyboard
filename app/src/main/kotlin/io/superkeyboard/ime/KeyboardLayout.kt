package io.superkeyboard.ime

object KeyboardLayout {

    fun getLayout(page: LayoutPage): List<List<Key>> = when (page) {
        LayoutPage.QWERTY -> qwertyLayout()
        LayoutPage.SYMBOLS_1 -> symbolsPage1()
        LayoutPage.SYMBOLS_2 -> symbolsPage2()
    }

    private fun qwertyLayout(): List<List<Key>> = listOf(
        // Row 1: q w e r t y u i o p
        listOf(
            Key("q", 'q'.code, longPressKeys = listOf("1")),
            Key("w", 'w'.code, longPressKeys = listOf("2")),
            Key("e", 'e'.code, longPressKeys = listOf("3", "é", "è", "ê", "ë", "ē")),
            Key("r", 'r'.code, longPressKeys = listOf("4")),
            Key("t", 't'.code, longPressKeys = listOf("5")),
            Key("y", 'y'.code, longPressKeys = listOf("6", "ý", "ỳ", "ỹ")),
            Key("u", 'u'.code, longPressKeys = listOf("7", "ú", "ù", "û", "ü", "ū", "ư")),
            Key("i", 'i'.code, longPressKeys = listOf("8", "í", "ì", "î", "ï")),
            Key("o", 'o'.code, longPressKeys = listOf("9", "ó", "ò", "ô", "ö", "õ", "ø", "ơ")),
            Key("p", 'p'.code, longPressKeys = listOf("0"))
        ),
        // Row 2: a s d f g h j k l
        listOf(
            Key("a", 'a'.code, longPressKeys = listOf("á", "à", "â", "ä", "ã", "å", "ă")),
            Key("s", 's'.code, longPressKeys = listOf("ß", "š")),
            Key("d", 'd'.code, longPressKeys = listOf("đ")),
            Key("f", 'f'.code),
            Key("g", 'g'.code),
            Key("h", 'h'.code),
            Key("j", 'j'.code),
            Key("k", 'k'.code),
            Key("l", 'l'.code, longPressKeys = listOf("ł"))
        ),
        // Row 3: shift z x c v b n m backspace
        listOf(
            Key("⇧", KeyCodes.SHIFT, widthWeight = 1.5f, icon = KeyIcon.SHIFT),
            Key("z", 'z'.code, longPressKeys = listOf("ž")),
            Key("x", 'x'.code),
            Key("c", 'c'.code, longPressKeys = listOf("ç", "č")),
            Key("v", 'v'.code),
            Key("b", 'b'.code),
            Key("n", 'n'.code, longPressKeys = listOf("ñ", "ń")),
            Key("m", 'm'.code),
            Key("⌫", KeyCodes.BACKSPACE, widthWeight = 1.5f, isRepeatable = true, icon = KeyIcon.BACKSPACE)
        ),
        // Row 4: symbols emoji comma space period enter
        listOf(
            Key("?123", KeyCodes.SYMBOLS, widthWeight = 1.5f, icon = KeyIcon.SYMBOLS),
            Key("😊", KeyCodes.EMOJI, icon = KeyIcon.EMOJI),
            Key(",", KeyCodes.COMMA),
            Key(" ", KeyCodes.SPACE, widthWeight = 4f, icon = KeyIcon.SPACE),
            Key(".", KeyCodes.PERIOD, longPressKeys = listOf("!", "?", ",", ":", ";", "…")),
            Key("↵", KeyCodes.ENTER, widthWeight = 1.5f, icon = KeyIcon.ENTER)
        )
    )

    private fun symbolsPage1(): List<List<Key>> = listOf(
        listOf(
            Key("1", '1'.code), Key("2", '2'.code), Key("3", '3'.code),
            Key("4", '4'.code), Key("5", '5'.code), Key("6", '6'.code),
            Key("7", '7'.code), Key("8", '8'.code), Key("9", '9'.code),
            Key("0", '0'.code)
        ),
        listOf(
            Key("@", '@'.code), Key("#", '#'.code), Key("$", '$'.code),
            Key("%", '%'.code), Key("&", '&'.code), Key("-", '-'.code),
            Key("+", '+'.code), Key("(", '('.code), Key(")", ')'.code)
        ),
        listOf(
            Key("=\\<", KeyCodes.SYMBOLS_PAGE_2, widthWeight = 1.5f),
            Key("*", '*'.code), Key("\"", '"'.code), Key("'", '\''.code),
            Key(":", ':'.code), Key(";", ';'.code), Key("!", '!'.code),
            Key("?", '?'.code),
            Key("⌫", KeyCodes.BACKSPACE, widthWeight = 1.5f, isRepeatable = true, icon = KeyIcon.BACKSPACE)
        ),
        listOf(
            Key("ABC", KeyCodes.ALPHA, widthWeight = 1.5f),
            Key("😊", KeyCodes.EMOJI, icon = KeyIcon.EMOJI),
            Key(",", KeyCodes.COMMA),
            Key(" ", KeyCodes.SPACE, widthWeight = 4f, icon = KeyIcon.SPACE),
            Key(".", KeyCodes.PERIOD),
            Key("↵", KeyCodes.ENTER, widthWeight = 1.5f, icon = KeyIcon.ENTER)
        )
    )

    private fun symbolsPage2(): List<List<Key>> = listOf(
        listOf(
            Key("~", '~'.code), Key("`", '`'.code), Key("|", '|'.code),
            Key("•", '•'.code), Key("√", '√'.code), Key("π", 'π'.code),
            Key("÷", '÷'.code), Key("×", '×'.code), Key("¶", '¶'.code),
            Key("∆", '∆'.code)
        ),
        listOf(
            Key("£", '£'.code), Key("¥", '¥'.code), Key("€", '€'.code),
            Key("¢", '¢'.code), Key("^", '^'.code), Key("°", '°'.code),
            Key("=", '='.code), Key("{", '{'.code), Key("}", '}'.code)
        ),
        listOf(
            Key("?123", KeyCodes.SYMBOLS_PAGE_2, widthWeight = 1.5f),
            Key("\\", '\\'.code), Key("©", '©'.code), Key("®", '®'.code),
            Key("™", '™'.code), Key("℅", '℅'.code), Key("[", '['.code),
            Key("]", ']'.code),
            Key("⌫", KeyCodes.BACKSPACE, widthWeight = 1.5f, isRepeatable = true, icon = KeyIcon.BACKSPACE)
        ),
        listOf(
            Key("ABC", KeyCodes.ALPHA, widthWeight = 1.5f),
            Key("😊", KeyCodes.EMOJI, icon = KeyIcon.EMOJI),
            Key("<", '<'.code),
            Key(" ", KeyCodes.SPACE, widthWeight = 4f, icon = KeyIcon.SPACE),
            Key(">", '>'.code),
            Key("↵", KeyCodes.ENTER, widthWeight = 1.5f, icon = KeyIcon.ENTER)
        )
    )
}
