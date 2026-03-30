package io.superkeyboard.toolbar

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.superkeyboard.R
import io.superkeyboard.ime.KeyboardTheme

class AIToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    var onClipboardClick: (() -> Unit)? = null

    private val theme = KeyboardTheme(context)
    private val container: LinearLayout

    data class ToolbarAction(
        val icon: String,
        val label: String,
        val onClick: () -> Unit
    )

    init {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(theme.colors.toolbarBackground)

        val height = theme.toolbarHeight.toInt()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }

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
            val button = createToolbarButton(action)
            container.addView(button)
        }

        addView(container)
    }

    private fun createToolbarButton(action: ToolbarAction): TextView {
        return TextView(context).apply {
            text = action.icon
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
