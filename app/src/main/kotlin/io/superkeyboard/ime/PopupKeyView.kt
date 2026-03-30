package io.superkeyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.PopupWindow

class PopupKeyView(
    context: Context,
    private val keys: List<String>,
    private val theme: KeyboardTheme,
    private val onKeySelected: (String) -> Unit
) : View(context) {

    private val keyWidth = 48f * resources.displayMetrics.density
    private val keyHeight = 56f * resources.displayMetrics.density
    private val cornerRadius = theme.keyCornerRadius
    private val keyRects = mutableListOf<RectF>()
    private var selectedIndex = -1

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.colors.keyBackground
        setShadowLayer(4f * resources.displayMetrics.density, 0f, 2f * resources.displayMetrics.density, Color.argb(60, 0, 0, 0))
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.colors.accent
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = theme.colors.keyText
        textSize = theme.popupTextSize
        textAlign = Paint.Align.CENTER
    }
    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = theme.popupTextSize
        textAlign = Paint.Align.CENTER
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        for (i in keys.indices) {
            keyRects.add(RectF(i * keyWidth, 0f, (i + 1) * keyWidth, keyHeight))
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((keys.size * keyWidth).toInt(), keyHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val fullRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(fullRect, cornerRadius, cornerRadius, bgPaint)

        for (i in keys.indices) {
            val rect = keyRects[i]
            if (i == selectedIndex) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectedPaint)
                drawCenteredText(canvas, keys[i], rect, selectedTextPaint)
            } else {
                drawCenteredText(canvas, keys[i], rect, textPaint)
            }
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF, paint: Paint) {
        val x = rect.centerX()
        val y = rect.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, x, y, paint)
    }

    fun updateSelection(touchX: Float): String? {
        val localX = touchX
        selectedIndex = (localX / keyWidth).toInt().coerceIn(0, keys.size - 1)
        invalidate()
        return keys.getOrNull(selectedIndex)
    }

    fun confirmSelection(): String? {
        return keys.getOrNull(selectedIndex)
    }

    companion object {
        fun show(
            anchor: View,
            anchorX: Int,
            anchorY: Int,
            keys: List<String>,
            theme: KeyboardTheme,
            onKeySelected: (String) -> Unit
        ): PopupWindow {
            val popupView = PopupKeyView(anchor.context, keys, theme, onKeySelected)
            val popup = PopupWindow(
                popupView,
                (keys.size * 48f * anchor.resources.displayMetrics.density).toInt(),
                (56f * anchor.resources.displayMetrics.density).toInt(),
                false
            ).apply {
                isTouchable = false
                isOutsideTouchable = false
                setBackgroundDrawable(null)
            }

            val offsetX = anchorX - (popup.width / 2)
            val offsetY = anchorY - popup.height - (8 * anchor.resources.displayMetrics.density).toInt()

            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, offsetX.coerceAtLeast(0), offsetY.coerceAtLeast(0))
            return popup
        }
    }
}
