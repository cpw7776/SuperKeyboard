package io.superkeyboard.ime

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import io.superkeyboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * IME overlay panel that previews an AI action result before it is applied to the field. Mirrors the
 * [io.superkeyboard.clipboard.ClipboardBottomSheet] template: a themed [LinearLayout] with its own
 * [CoroutineScope] cancelled in [destroy].
 *
 * Three states, driven by the public methods:
 *  - [showLoading]    spinner + "Processing…"
 *  - [showResult]     scrollable model text + Apply / Cancel
 *  - [showMessage]    an informational message (Disabled / Unconfigured / TooLong / Error) + Close
 *
 * [onApply] receives the result text to commit; [onClose] is invoked by Cancel and by message-close.
 */
class AiPreviewView(
    context: Context,
    private val theme: KeyboardTheme,
    private val onApply: (String) -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dp = resources.displayMetrics.density

    private val bodyText: TextView
    private val spinner: ProgressBar
    private val scrollView: ScrollView
    private val applyButton: Button
    private val cancelButton: Button
    private val closeButton: Button

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.colors.keyboardBackground)

        // Header (title + ✕) — same shape as ClipboardBottomSheet's header.
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            setBackgroundColor(theme.colors.toolbarBackground)
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.ai_preview_title)
            setTextColor(theme.colors.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)
        val headerClose = TextView(context).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(theme.colors.keyText)
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { onClose() }
        }
        header.addView(headerClose)
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Loading spinner.
        spinner = ProgressBar(context).apply {
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE
        }
        addView(spinner, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        // Body text (used for "Processing…", result, and messages), inside a bounded scroll area.
        bodyText = TextView(context).apply {
            setTextColor(theme.colors.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            setTextIsSelectable(true)
        }
        scrollView = ScrollView(context).apply {
            addView(bodyText)
        }
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, (180 * dp).toInt()))

        // Action row: Apply / Cancel (result state) or Close (message state).
        val buttonRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        }
        cancelButton = makeButton(context.getString(R.string.ai_preview_cancel)) { onClose() }
        closeButton = makeButton(context.getString(R.string.ai_preview_close)) { onClose() }
        applyButton = makeButton(context.getString(R.string.ai_preview_apply)) {
            onApply(bodyText.text.toString())
        }
        buttonRow.addView(closeButton)
        buttonRow.addView(cancelButton)
        buttonRow.addView(applyButton)
        addView(buttonRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        showLoading()
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            setTextColor(theme.colors.keyText)
            setOnClickListener { onClick() }
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = (8 * dp).toInt()
            }
        }

    /** Loading state: spinner + "Processing…", no action buttons. */
    fun showLoading() {
        spinner.visibility = View.VISIBLE
        bodyText.text = context.getString(R.string.ai_preview_processing)
        applyButton.visibility = View.GONE
        cancelButton.visibility = View.VISIBLE
        closeButton.visibility = View.GONE
    }

    /** Result state: the model [text] + Apply / Cancel. */
    fun showResult(text: String) {
        spinner.visibility = View.GONE
        bodyText.text = text
        scrollView.scrollTo(0, 0)
        applyButton.visibility = View.VISIBLE
        cancelButton.visibility = View.VISIBLE
        closeButton.visibility = View.GONE
    }

    /** Message state: an informational [text] (no Apply) + Close. */
    fun showMessage(text: String) {
        spinner.visibility = View.GONE
        bodyText.text = text
        scrollView.scrollTo(0, 0)
        applyButton.visibility = View.GONE
        cancelButton.visibility = View.GONE
        closeButton.visibility = View.VISIBLE
    }

    /** Cancel this panel's coroutine scope. Call when the panel is dismissed or the IME is destroyed. */
    fun destroy() {
        scope.cancel()
    }
}
