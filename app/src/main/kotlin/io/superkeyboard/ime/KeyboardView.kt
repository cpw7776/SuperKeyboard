package io.superkeyboard.ime

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import io.superkeyboard.util.HapticHelper

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var listener: KeyboardActionListener? = null
    var keyboardState: KeyboardState = KeyboardState()
        set(value) {
            field = value
            rebuildLayout()
            invalidate()
        }

    private var theme = KeyboardTheme(context)
    private var keys: List<List<Key>> = emptyList()
    private var flatKeys: List<Key> = emptyList()
    private var activePopup: PopupWindow? = null
    private var popupView: PopupKeyView? = null

    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(20, 0, 0, 0)
    }

    private val gestureHandler = GestureHandler(
        onKeyPress = { key ->
            key.isPressed = true
            HapticHelper.performKeyPress(this)
            invalidate()
        },
        onKeyLongPress = { key ->
            if (key.longPressKeys.isNotEmpty()) {
                HapticHelper.performLongPress(this)
                showPopup(key)
            }
        },
        onKeyRelease = { key ->
            key.isPressed = false
            dismissPopup()
            listener?.onKeyPressed(key)
            invalidate()
        },
        onKeyRepeat = { key ->
            listener?.onKeyPressed(key)
        }
    )

    init {
        rebuildLayout()
    }

    fun refreshTheme() {
        theme = KeyboardTheme(context)
        invalidate()
    }

    private fun rebuildLayout() {
        keys = KeyboardLayout.getLayout(keyboardState.layoutPage)
        flatKeys = keys.flatten()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = keys.size
        val keyHeight = width / 10f * 1.1f  // Proportional key height
        val totalHeight = (rowCount * keyHeight + theme.toolbarHeight).toInt()
        setMeasuredDimension(width, totalHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys(w.toFloat(), h.toFloat())
    }

    private fun layoutKeys(viewWidth: Float, viewHeight: Float) {
        val rowCount = keys.size
        if (rowCount == 0) return

        val keyHeight = (viewHeight - theme.toolbarHeight) / rowCount
        val padding = theme.keyPadding

        for (rowIndex in keys.indices) {
            val row = keys[rowIndex]
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat()
            val keyUnitWidth = viewWidth / totalWeight
            val y = theme.toolbarHeight + rowIndex * keyHeight

            var x = 0f
            for (key in row) {
                val keyWidth = key.widthWeight * keyUnitWidth
                key.bounds = RectF(
                    x + padding,
                    y + padding,
                    x + keyWidth - padding,
                    y + keyHeight - padding
                )
                x += keyWidth
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Background
        canvas.drawColor(theme.colors.keyboardBackground)

        // Draw toolbar area background
        canvas.drawRect(0f, 0f, width.toFloat(), theme.toolbarHeight, Paint().apply {
            color = theme.colors.toolbarBackground
        })

        // Draw keys
        for (key in flatKeys) {
            drawKey(canvas, key)
        }
    }

    private fun drawKey(canvas: Canvas, key: Key) {
        val bounds = key.bounds
        if (bounds.isEmpty) return

        val isSpecial = key.code < 0 && key.code != KeyCodes.SPACE
        val bgColor = when {
            key.isPressed -> theme.colors.keyPressed
            isSpecial -> theme.colors.specialKeyBackground
            else -> theme.colors.keyBackground
        }

        keyBgPaint.color = bgColor
        canvas.drawRoundRect(bounds, theme.keyCornerRadius, theme.keyCornerRadius, keyBgPaint)

        when (key.icon) {
            KeyIcon.BACKSPACE -> drawBackspaceIcon(canvas, bounds)
            KeyIcon.SHIFT -> drawShiftIcon(canvas, bounds, keyboardState.shiftState)
            KeyIcon.SHIFT_LOCKED -> drawShiftIcon(canvas, bounds, ShiftState.LOCKED)
            KeyIcon.ENTER -> drawEnterIcon(canvas, bounds)
            KeyIcon.SPACE -> {} // Space bar is blank or has a subtle label
            KeyIcon.EMOJI -> drawEmojiLabel(canvas, bounds, key.label)
            KeyIcon.SYMBOLS, KeyIcon.LANGUAGE -> drawKeyLabel(canvas, bounds, key.label)
            null -> drawKeyLabel(canvas, bounds, getDisplayLabel(key))
        }
    }

    private fun getDisplayLabel(key: Key): String {
        if (key.code in 32..127 && keyboardState.isShifted && keyboardState.layoutPage == LayoutPage.QWERTY) {
            return key.label.uppercase()
        }
        return key.label
    }

    private fun drawKeyLabel(canvas: Canvas, bounds: RectF, label: String) {
        val paint = if (label.length > 1) theme.keyLabelPaint else theme.keyTextPaint
        paint.color = theme.colors.keyText
        val x = bounds.centerX()
        val y = bounds.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(label, x, y, paint)
    }

    private fun drawEmojiLabel(canvas: Canvas, bounds: RectF, emoji: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = theme.keyTextSize
            textAlign = Paint.Align.CENTER
        }
        val x = bounds.centerX()
        val y = bounds.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(emoji, x, y, paint)
    }

    private fun drawBackspaceIcon(canvas: Canvas, bounds: RectF) {
        val paint = theme.keyIconPaint.apply { color = theme.colors.keyText }
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val size = bounds.height() * 0.25f

        val path = Path().apply {
            moveTo(cx - size, cy)
            lineTo(cx - size * 0.4f, cy - size * 0.7f)
            lineTo(cx + size, cy - size * 0.7f)
            lineTo(cx + size, cy + size * 0.7f)
            lineTo(cx - size * 0.4f, cy + size * 0.7f)
            close()
        }
        paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint)

        // X inside
        val xSize = size * 0.25f
        canvas.drawLine(cx + xSize, cy - xSize, cx - xSize + size * 0.3f, cy + xSize, paint)
        canvas.drawLine(cx - xSize + size * 0.3f, cy - xSize, cx + xSize, cy + xSize, paint)
    }

    private fun drawShiftIcon(canvas: Canvas, bounds: RectF, state: ShiftState) {
        val paint = theme.keyIconPaint.apply { color = theme.colors.keyText }
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val size = bounds.height() * 0.22f

        val path = Path().apply {
            moveTo(cx, cy - size)
            lineTo(cx + size, cy)
            lineTo(cx + size * 0.5f, cy)
            lineTo(cx + size * 0.5f, cy + size * 0.6f)
            lineTo(cx - size * 0.5f, cy + size * 0.6f)
            lineTo(cx - size * 0.5f, cy)
            lineTo(cx - size, cy)
            close()
        }

        when (state) {
            ShiftState.OFF -> {
                paint.style = Paint.Style.STROKE
                canvas.drawPath(path, paint)
            }
            ShiftState.ON -> {
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)
            }
            ShiftState.LOCKED -> {
                paint.style = Paint.Style.FILL
                paint.color = theme.colors.accent
                canvas.drawPath(path, paint)
                // Draw underline for locked
                canvas.drawLine(
                    cx - size * 0.6f, cy + size * 0.9f,
                    cx + size * 0.6f, cy + size * 0.9f,
                    paint
                )
            }
        }
    }

    private fun drawEnterIcon(canvas: Canvas, bounds: RectF) {
        val paint = theme.keyIconPaint.apply { color = theme.colors.keyText }
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val size = bounds.height() * 0.2f

        // Arrow pointing left with a hook from top-right
        canvas.drawLine(cx + size * 0.5f, cy - size * 0.5f, cx + size * 0.5f, cy, paint)
        canvas.drawLine(cx + size * 0.5f, cy, cx - size * 0.7f, cy, paint)
        canvas.drawLine(cx - size * 0.7f, cy, cx - size * 0.2f, cy - size * 0.4f, paint)
        canvas.drawLine(cx - size * 0.7f, cy, cx - size * 0.2f, cy + size * 0.4f, paint)
    }

    private fun showPopup(key: Key) {
        dismissPopup()
        val location = IntArray(2)
        getLocationInWindow(location)

        activePopup = PopupKeyView.show(
            anchor = this,
            anchorX = (location[0] + key.bounds.centerX()).toInt(),
            anchorY = (location[1] + key.bounds.top).toInt(),
            keys = key.longPressKeys,
            theme = theme,
            onKeySelected = { selectedChar ->
                listener?.onTextInput(selectedChar)
            }
        )
        popupView = (activePopup?.contentView as? PopupKeyView)
    }

    private fun dismissPopup() {
        activePopup?.dismiss()
        activePopup = null
        popupView = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle popup interaction during long press
        if (activePopup != null && popupView != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val location = IntArray(2)
                    (activePopup?.contentView)?.getLocationInWindow(location)
                    popupView?.updateSelection(event.rawX - location[0])
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val selected = popupView?.confirmSelection()
                    dismissPopup()
                    flatKeys.forEach { it.isPressed = false }
                    invalidate()
                    if (selected != null) {
                        listener?.onTextInput(selected)
                    }
                    return true
                }
            }
        }

        // Offset Y to account for toolbar
        return gestureHandler.onTouchEvent(event) { x, y ->
            flatKeys.find { it.contains(x, y) }
        }
    }

    interface KeyboardActionListener {
        fun onKeyPressed(key: Key)
        fun onTextInput(text: String)
    }
}
