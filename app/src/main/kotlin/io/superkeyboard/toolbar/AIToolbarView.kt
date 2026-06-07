package io.superkeyboard.toolbar

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import io.superkeyboard.R
import io.superkeyboard.ime.KeyboardTheme

class AIToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onClipboardClick: (() -> Unit)? = null

    private val theme = KeyboardTheme(context)

    data class ToolbarAction(
        val icon: String,
        val label: String,
        val onClick: () -> Unit
    )

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(theme.colors.toolbarBackground)

        // Fill the full keyboard width as a single row of equal-width buttons. Because each
        // button is weighted (width = 0, weight = 1), the icons spread across the whole line
        // and automatically shrink to share the space as more actions are added — no
        // horizontal scrolling, no left-clustering.
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, theme.toolbarHeight.toInt())

        val actions = listOf(
            ToolbarAction("🌐", context.getString(R.string.toolbar_translate)) { /* Phase 2 */ },
            ToolbarAction("🔊", context.getString(R.string.toolbar_tts)) { /* Phase 3 */ },
            ToolbarAction("🎤", context.getString(R.string.toolbar_stt)) { /* Phase 3 */ },
            ToolbarAction("📋", context.getString(R.string.toolbar_clipboard)) { onClipboardClick?.invoke() },
            ToolbarAction("✏️", context.getString(R.string.toolbar_rewrite)) { /* Phase 2 */ },
            ToolbarAction("📝", context.getString(R.string.toolbar_summarize)) { /* Phase 2 */ },
            ToolbarAction("📖", context.getString(R.string.toolbar_expand)) { /* Phase 2 */ },
            ToolbarAction("⚡", context.getString(R.string.toolbar_presets)) { /* Phase 2 */ }
        )

        for (action in actions) {
            addView(createToolbarButton(action))
        }
    }

    private fun createToolbarButton(action: ToolbarAction): TextView {
        return TextView(context).apply {
            // Equal share of the row width; each new action shrinks all buttons evenly.
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            text = action.icon
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setOnClickListener { action.onClick() }

            // Ripple-like feedback
            isClickable = true
            isFocusable = true
            val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            val ta = context.obtainStyledAttributes(attrs)
            foreground = ta.getDrawable(0)
            ta.recycle()
        }
    }
}
