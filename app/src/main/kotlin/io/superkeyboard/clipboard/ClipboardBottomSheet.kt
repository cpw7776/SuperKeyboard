package io.superkeyboard.clipboard

import android.content.Context
import android.graphics.Color
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.superkeyboard.ime.KeyboardTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class ClipboardBottomSheet(
    context: Context,
    private val repository: ClipboardRepository,
    private val theme: KeyboardTheme,
    private val onPaste: (String) -> Unit
) : LinearLayout(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val adapter = ClipboardAdapter()
    private val searchInput: EditText

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.colors.keyboardBackground)
        val dp = resources.displayMetrics.density

        // Header
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            setBackgroundColor(theme.colors.toolbarBackground)
        }

        val title = TextView(context).apply {
            text = "Clipboard"
            setTextColor(theme.colors.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val closeButton = TextView(context).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(theme.colors.keyText)
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener { onClose?.invoke() }
        }
        header.addView(closeButton)
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Search
        searchInput = EditText(context).apply {
            hint = "Search clipboard…"
            setTextColor(theme.colors.keyText)
            setHintTextColor(Color.argb(128, Color.red(theme.colors.keyText), Color.green(theme.colors.keyText), Color.blue(theme.colors.keyText)))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(theme.colors.keyBackground)
        }
        addView(searchInput, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // List
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ClipboardBottomSheet.adapter
        }
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, (200 * dp).toInt()))

        loadEntries()
    }

    var onClose: (() -> Unit)? = null

    private fun loadEntries() {
        scope.launch {
            repository.getAllEntries().collectLatest { entries ->
                adapter.updateEntries(entries)
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    inner class ClipboardAdapter : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {
        private var entries: List<ClipboardEntry> = emptyList()

        fun updateEntries(newEntries: List<ClipboardEntry>) {
            entries = newEntries
            notifyDataSetChanged()
        }

        inner class ViewHolder(val layout: LinearLayout) : RecyclerView.ViewHolder(layout) {
            val textView: TextView = layout.getChildAt(0) as TextView
            val timeView: TextView = layout.getChildAt(1) as TextView
            val pinView: TextView = layout.getChildAt(2) as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.resources.displayMetrics.density
            val layout = LinearLayout(parent.context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                setBackgroundColor(theme.colors.keyBackground)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (1 * dp).toInt()
                }
            }

            val textView = TextView(parent.context).apply {
                setTextColor(theme.colors.keyText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 2
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            layout.addView(textView)

            val timeView = TextView(parent.context).apply {
                setTextColor(Color.GRAY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding((8 * dp).toInt(), 0, 0, 0)
            }
            layout.addView(timeView)

            val pinView = TextView(parent.context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding((8 * dp).toInt(), 0, 0, 0)
            }
            layout.addView(pinView)

            return ViewHolder(layout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.textView.text = entry.text
            holder.timeView.text = DateUtils.getRelativeTimeSpanString(
                entry.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            holder.pinView.text = if (entry.isPinned) "📌" else ""

            holder.layout.setOnClickListener {
                onPaste(entry.text)
            }
            holder.layout.setOnLongClickListener {
                scope.launch {
                    repository.togglePin(entry)
                }
                true
            }
        }

        override fun getItemCount(): Int = entries.size
    }
}
