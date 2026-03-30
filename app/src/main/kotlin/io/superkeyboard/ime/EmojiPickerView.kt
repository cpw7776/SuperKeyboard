package io.superkeyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EmojiPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onEmojiSelected: ((String) -> Unit)? = null
    var onBackToKeyboard: (() -> Unit)? = null

    private val categories = EmojiData.categories
    private var currentCategory = 0
    private val recyclerView: RecyclerView
    private val categoryTabs: HorizontalScrollView
    private val tabContainer: LinearLayout

    init {
        orientation = VERTICAL
        val theme = KeyboardTheme(context)

        // Category tabs
        tabContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Back button
        val backButton = TextView(context).apply {
            text = "ABC"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.colors.keyText)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onBackToKeyboard?.invoke() }
        }
        tabContainer.addView(backButton)

        for ((index, category) in categories.withIndex()) {
            val tab = TextView(context).apply {
                text = category.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener {
                    currentCategory = index
                    updateEmojis()
                    updateTabHighlight()
                }
            }
            tabContainer.addView(tab)
        }

        categoryTabs = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(theme.colors.toolbarBackground)
            addView(tabContainer)
        }
        addView(categoryTabs, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Emoji grid
        recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 8)
            adapter = EmojiAdapter()
            setBackgroundColor(theme.colors.keyboardBackground)
        }
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, dp(200)))

        updateTabHighlight()
    }

    private fun updateEmojis() {
        (recyclerView.adapter as EmojiAdapter).updateEmojis(categories[currentCategory].emojis)
    }

    private fun updateTabHighlight() {
        // +1 offset for back button
        for (i in 0 until tabContainer.childCount) {
            val child = tabContainer.getChildAt(i)
            child.alpha = if (i == currentCategory + 1) 1f else 0.5f
        }
        updateEmojis()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    inner class EmojiAdapter : RecyclerView.Adapter<EmojiAdapter.ViewHolder>() {
        private var emojis: List<String> = categories.firstOrNull()?.emojis ?: emptyList()

        fun updateEmojis(newEmojis: List<String>) {
            emojis = newEmojis
            notifyDataSetChanged()
        }

        inner class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                gravity = Gravity.CENTER
                val size = dp(40)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    size
                )
                setOnClickListener {
                    val emoji = emojis.getOrNull(adapterPosition) ?: return@setOnClickListener
                    onEmojiSelected?.invoke(emoji)
                }
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = emojis[position]
        }

        override fun getItemCount(): Int = emojis.size
    }
}

data class EmojiCategory(val name: String, val icon: String, val emojis: List<String>)

object EmojiData {
    val categories = listOf(
        EmojiCategory("Smileys", "😀", listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "☺️", "😚", "😙", "🥲", "😋", "😛",
            "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
            "🫡", "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏",
            "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤",
            "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🥴", "😵",
            "🤯", "🥳", "🥸", "😎", "🤓", "🧐", "😕", "🫤"
        )),
        EmojiCategory("People", "👋", listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳",
            "🫴", "👌", "🤌", "🤏", "✌️", "🤞", "🫰", "🤟",
            "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️",
            "🫵", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏",
            "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
            "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻"
        )),
        EmojiCategory("Nature", "🐶", listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵",
            "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇",
            "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋",
            "🌸", "💮", "🏵️", "🌹", "🥀", "🌺", "🌻", "🌼",
            "🌷", "🌱", "🪴", "🌲", "🌳", "🌴", "🌵", "🌾"
        )),
        EmojiCategory("Food", "🍕", listOf(
            "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓",
            "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
            "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🫑",
            "🌽", "🥕", "🫒", "🧄", "🧅", "🥔", "🍠", "🫘",
            "🍕", "🍔", "🍟", "🌭", "🍿", "🧈", "🥚", "🍳",
            "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🌮", "🌯"
        )),
        EmojiCategory("Objects", "💡", listOf(
            "⌚", "📱", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️",
            "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹",
            "🎥", "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺",
            "📻", "🎙️", "🎚️", "🎛️", "🧭", "⏱️", "⏲️", "⏰",
            "🔋", "🔌", "💡", "🔦", "🕯️", "🧯", "🛢️", "💸",
            "💵", "💴", "💶", "💷", "🪙", "💰", "💳", "🧾"
        )),
        EmojiCategory("Symbols", "❤️", listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
            "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "💕", "💞", "💓", "💗",
            "💖", "💘", "💝", "✅", "❌", "⭕", "❗", "❓",
            "‼️", "⁉️", "💯", "🔥", "✨", "⭐", "🌟", "💫",
            "⚡", "💥", "💢", "💦", "💨", "🕊️", "🎵", "🎶",
            "➡️", "⬅️", "⬆️", "⬇️", "↗️", "↘️", "↙️", "↖️"
        ))
    )
}
