package io.superkeyboard.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.superkeyboard.R
import io.superkeyboard.SuperKeyboardApp
import io.superkeyboard.ai.AiAction
import io.superkeyboard.ai.AiConfig
import io.superkeyboard.ai.AiEngine
import io.superkeyboard.ai.AiKeyStore
import io.superkeyboard.ai.AiPresets
import io.superkeyboard.ai.EngineResult
import io.superkeyboard.ai.OkHttpAiChatClient
import io.superkeyboard.clipboard.ClipboardBottomSheet
import io.superkeyboard.clipboard.ClipboardManagerService
import io.superkeyboard.clipboard.ClipboardRepository
import io.superkeyboard.settings.SettingsRepository
import io.superkeyboard.toolbar.AIToolbarView
import io.superkeyboard.util.HapticHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KeyboardService : InputMethodService(), KeyboardView.KeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var toolbarView: AIToolbarView
    private lateinit var emojiPickerView: EmojiPickerView
    private lateinit var rootLayout: LinearLayout
    private val keyboardState = KeyboardState()

    private lateinit var clipboardRepository: ClipboardRepository
    private lateinit var clipboardManagerService: ClipboardManagerService
    private var clipboardSheet: ClipboardBottomSheet? = null

    // AI Action Engine (E2). The engine is pure; the OkHttp client is the only network seam and is
    // built lazily, so when AI is disabled the engine short-circuits and no socket is opened.
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var aiKeyStore: AiKeyStore
    private val aiEngine = AiEngine(OkHttpAiChatClient())
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var aiPreview: AiPreviewView? = null

    /** Whether the current input field is a password/secure field — AI actions are blocked there (D4). */
    private var isPasswordField = false

    override fun onCreate() {
        super.onCreate()
        HapticHelper.init(this)

        val app = application as SuperKeyboardApp
        clipboardRepository = ClipboardRepository(app.clipboardDatabase.clipboardDao())
        clipboardManagerService = ClipboardManagerService(this, clipboardRepository)
        clipboardManagerService.startListening()

        settingsRepository = SettingsRepository(this)
        aiKeyStore = AiKeyStore(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManagerService.stopListening()
        clipboardSheet?.destroy()
        aiPreview?.destroy()
        aiScope.cancel()
    }

    override fun onCreateInputView(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // The padded area shows through below the keys, so paint it the keyboard colour.
            setBackgroundColor(KeyboardTheme(this@KeyboardService).colors.keyboardBackground)
        }
        applyBottomInset(rootLayout)

        toolbarView = AIToolbarView(this).apply {
            onClipboardClick = { showClipboardManager() }
            onAiAction = { action -> handleAiAction(action) }
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

    /**
     * Keeps the bottom keyboard row clear of the system navigation / gesture bar so the
     * space/enter row isn't cut off at the bottom edge. Uses the live navigation-bar +
     * gesture insets when the framework reports them, with a fixed floor for the (common)
     * case where an IME window receives a zero bottom inset.
     */
    private fun applyBottomInset(view: View) {
        val density = resources.displayMetrics.density
        val minPad = (16 * density).toInt()
        view.setPadding(0, 0, 0, minPad)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.systemGestures()
            ).bottom
            v.setPadding(0, 0, 0, maxOf(navBottom, minPad))
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        isPasswordField = isPasswordInputType(info?.inputType ?: 0)
        applyAutoCapitalize(info)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isPasswordField = isPasswordInputType(info?.inputType ?: 0)
        // Grey out the 5 AI buttons on password/secure fields (D4 — visible signal + UX).
        if (::toolbarView.isInitialized) toolbarView.setAiActionsEnabled(!isPasswordField)
        keyboardState.switchToAlpha()
        keyboardState.setEmojiMode(false)
        applyAutoCapitalize(info)
        keyboardView.keyboardState = keyboardState
        keyboardView.refreshTheme()
        showKeyboard()
    }

    /**
     * True for password/secure variations across text and number classes (D4). AI actions are
     * disabled on these fields and the engine never receives their contents.
     */
    private fun isPasswordInputType(inputType: Int): Boolean {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (cls) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
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

    // --- AI Action Engine wiring (E2 / D3) ---------------------------------

    /**
     * Handle a tap on one of the 5 AI toolbar buttons. Reads the target text (selection else whole
     * field), shows the preview in its loading state, then runs the engine off the [aiScope] and
     * renders the result. The engine's privacy gate (disabled/unconfigured) means no network work
     * happens unless the user has explicitly enabled and configured AI.
     */
    private fun handleAiAction(action: AiAction) {
        // (D4 safety net) Never read/send secure field contents, even if the button slipped through.
        if (isPasswordField) return
        val ic = currentInputConnection ?: return

        // Resolve the target text via the pure helper: selection if present, else the whole field.
        val selected = ic.getSelectedText(0)
        val before = ic.getTextBeforeCursor(MAX_FIELD_CHARS, 0) ?: ""
        val after = ic.getTextAfterCursor(MAX_FIELD_CHARS, 0) ?: ""
        val target = resolveTarget(selected, "$before$after")

        showAiPreviewLoading()

        aiScope.launch {
            val config = AiConfig(
                enabled = settingsRepository.aiEnabled.first(),
                endpointUrl = settingsRepository.aiEndpointUrl.first(),
                apiKey = aiKeyStore.getKey() ?: "",
                model = settingsRepository.aiModel.first(),
                targetLanguage = settingsRepository.aiTargetLang.first()
            )

            // PRESET: DEVIATION — for v1 simplicity we use the FIRST saved preset (no in-IME chooser).
            // If no presets exist, surface a "create a preset in Settings" message and do not call out.
            val presetPrompt: String? = if (action == AiAction.PRESET) {
                val presets = AiPresets.decode(settingsRepository.aiPresetsJson.first())
                if (presets.isEmpty()) {
                    aiPreview?.showMessage(getString(R.string.ai_msg_no_preset))
                    return@launch
                }
                presets.first().prompt
            } else {
                null
            }

            val result = aiEngine.run(action, target.text, config, presetPrompt)
            renderAiResult(result, target.hadSelection)
        }
    }

    private fun renderAiResult(result: EngineResult, hadSelection: Boolean) {
        // Remember whether Apply should replace the selection or the whole field.
        applyHadSelection = hadSelection
        when (result) {
            is EngineResult.Result -> aiPreview?.showResult(result.text)
            EngineResult.Disabled -> aiPreview?.showMessage(getString(R.string.ai_msg_disabled))
            EngineResult.Unconfigured -> aiPreview?.showMessage(getString(R.string.ai_msg_unconfigured))
            EngineResult.TooLong -> aiPreview?.showMessage(getString(R.string.ai_msg_too_long))
            is EngineResult.Error -> aiPreview?.showMessage(
                result.message.ifBlank { getString(R.string.ai_msg_error_generic) }
            )
        }
    }

    /** Whether the most recent AI request targeted a selection (vs. the whole field) — used on Apply. */
    private var applyHadSelection = false

    private fun showAiPreviewLoading() {
        aiPreview?.destroy()
        val theme = KeyboardTheme(this)
        aiPreview = AiPreviewView(
            context = this,
            theme = theme,
            onApply = { text -> applyAiResult(text) },
            onClose = { dismissAiPreview() }
        )
        rootLayout.removeAllViews()
        rootLayout.addView(toolbarView)
        rootLayout.addView(aiPreview)
    }

    /**
     * Apply the AI result to the field. If the request targeted a selection, [commitText] replaces it.
     * Otherwise we replace the whole field: select-all then commit, wrapped in a batch edit so it is
     * a single, atomic edit the host app sees.
     */
    private fun applyAiResult(text: String) {
        val ic = currentInputConnection
        if (ic != null) {
            if (applyHadSelection) {
                // A selection is active — commitText replaces exactly the selected range.
                ic.commitText(text, 1)
            } else {
                // No selection — replace the entire field atomically.
                ic.beginBatchEdit()
                ic.performContextMenuAction(android.R.id.selectAll)
                ic.commitText(text, 1)
                ic.endBatchEdit()
            }
        }
        dismissAiPreview()
    }

    private fun dismissAiPreview() {
        aiPreview?.destroy()
        aiPreview = null
        showKeyboard()
    }

    private companion object {
        // Upper bound on how much surrounding text we pull (per direction) when there is no
        // selection. Deliberately well above the engine's input cap (DEFAULT_MAX_INPUT_CHARS) so an
        // over-long field still exceeds the cap and is caught as TooLong, rather than being silently
        // truncated here to a length the engine would accept.
        const val MAX_FIELD_CHARS = 100_000
    }
}
