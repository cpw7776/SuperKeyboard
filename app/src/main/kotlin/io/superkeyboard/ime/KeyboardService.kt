package io.superkeyboard.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import io.superkeyboard.SuperKeyboardApp
import io.superkeyboard.clipboard.ClipboardBottomSheet
import io.superkeyboard.clipboard.ClipboardManagerService
import io.superkeyboard.clipboard.ClipboardRepository
import io.superkeyboard.toolbar.AIToolbarView
import io.superkeyboard.util.HapticHelper

class KeyboardService : InputMethodService(), KeyboardView.KeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var toolbarView: AIToolbarView
    private lateinit var emojiPickerView: EmojiPickerView
    private lateinit var rootLayout: LinearLayout
    private val keyboardState = KeyboardState()

    private lateinit var clipboardRepository: ClipboardRepository
    private lateinit var clipboardManagerService: ClipboardManagerService
    private var clipboardSheet: ClipboardBottomSheet? = null

    override fun onCreate() {
        super.onCreate()
        HapticHelper.init(this)

        val app = application as SuperKeyboardApp
        clipboardRepository = ClipboardRepository(app.clipboardDatabase.clipboardDao())
        clipboardManagerService = ClipboardManagerService(this, clipboardRepository)
        clipboardManagerService.startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManagerService.stopListening()
        clipboardSheet?.destroy()
    }

    override fun onCreateInputView(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        toolbarView = AIToolbarView(this).apply {
            onClipboardClick = { showClipboardManager() }
        }

        keyboardView = KeyboardView(this).apply {
            listener = this@KeyboardService
            keyboardState = this@KeyboardService.keyboardState
        }

        emojiPickerView = EmojiPickerView(this).apply {
            onEmojiSelected = { emoji ->
                currentInputConnection?.commitText(emoji, 1)
            }
            onBackToKeyboard = {
                keyboardState.setEmojiMode(false)
                showKeyboard()
            }
        }

        rootLayout.addView(toolbarView)
        rootLayout.addView(keyboardView)

        return rootLayout
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        applyAutoCapitalize(info)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardState.switchToAlpha()
        keyboardState.setEmojiMode(false)
        applyAutoCapitalize(info)
        keyboardView.keyboardState = keyboardState
        keyboardView.refreshTheme()
        showKeyboard()
    }

    private fun applyAutoCapitalize(info: EditorInfo?) {
        val inputType = info?.inputType ?: 0
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val shouldCapitalize = (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
                || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT
                || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        keyboardState.setAutoShift(shouldCapitalize)
    }

    override fun onKeyPressed(key: Key) {
        val ic = currentInputConnection ?: return

        when (key.code) {
            KeyCodes.SHIFT -> {
                keyboardState.toggleShift()
                keyboardView.keyboardState = keyboardState
            }
            KeyCodes.BACKSPACE -> {
                ic.deleteSurroundingText(1, 0)
            }
            KeyCodes.ENTER -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_UNSPECIFIED
                if (action != EditorInfo.IME_ACTION_UNSPECIFIED && action != EditorInfo.IME_ACTION_NONE) {
                    ic.performEditorAction(action)
                } else {
                    ic.commitText("\n", 1)
                }
            }
            KeyCodes.SYMBOLS -> {
                keyboardState.toggleSymbols()
                keyboardView.keyboardState = keyboardState
            }
            KeyCodes.SYMBOLS_PAGE_2 -> {
                keyboardState.toggleSymbolsPage2()
                keyboardView.keyboardState = keyboardState
            }
            KeyCodes.ALPHA -> {
                keyboardState.switchToAlpha()
                keyboardView.keyboardState = keyboardState
            }
            KeyCodes.EMOJI -> {
                keyboardState.setEmojiMode(true)
                showEmoji()
            }
            KeyCodes.SPACE -> {
                ic.commitText(" ", 1)
                checkAutoCapitalize()
            }
            KeyCodes.COMMA -> {
                ic.commitText(",", 1)
            }
            KeyCodes.PERIOD -> {
                ic.commitText(".", 1)
                checkAutoCapitalize()
            }
            else -> {
                var char = Char(key.code).toString()
                if (keyboardState.isShifted && keyboardState.layoutPage == LayoutPage.QWERTY) {
                    char = char.uppercase()
                }
                ic.commitText(char, 1)
                keyboardState.onCharacterTyped()
                keyboardView.keyboardState = keyboardState
                checkAutoCapitalize()
            }
        }
    }

    override fun onTextInput(text: String) {
        currentInputConnection?.commitText(text, 1)
        keyboardState.onCharacterTyped()
        keyboardView.keyboardState = keyboardState
    }

    private fun checkAutoCapitalize() {
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(2, 0)?.toString() ?: return

        val shouldCapitalize = textBefore.isEmpty() ||
                (textBefore.length >= 2 && textBefore[textBefore.length - 2] in ".!?" && textBefore.last() == ' ') ||
                (textBefore.length == 1 && textBefore[0] in ".!?")

        if (keyboardState.shiftState != ShiftState.LOCKED) {
            keyboardState.setAutoShift(shouldCapitalize)
            keyboardView.keyboardState = keyboardState
        }
    }

    private fun showKeyboard() {
        rootLayout.removeAllViews()
        rootLayout.addView(toolbarView)
        rootLayout.addView(keyboardView)
    }

    private fun showEmoji() {
        rootLayout.removeAllViews()
        rootLayout.addView(toolbarView)
        rootLayout.addView(emojiPickerView)
    }

    private fun showClipboardManager() {
        clipboardSheet?.destroy()
        val theme = KeyboardTheme(this)
        clipboardSheet = ClipboardBottomSheet(
            context = this,
            repository = clipboardRepository,
            theme = theme,
            onPaste = { text ->
                currentInputConnection?.commitText(text, 1)
                showKeyboard()
            }
        ).apply {
            onClose = { showKeyboard() }
        }

        rootLayout.removeAllViews()
        rootLayout.addView(toolbarView)
        rootLayout.addView(clipboardSheet)
    }
}
