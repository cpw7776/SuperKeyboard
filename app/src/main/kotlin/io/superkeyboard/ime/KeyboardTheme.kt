package io.superkeyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

data class KeyboardColors(
    val keyboardBackground: Int,
    val keyBackground: Int,
    val keyText: Int,
    val keyPressed: Int,
    val specialKeyBackground: Int,
    val toolbarBackground: Int,
    val accent: Int
)

class KeyboardTheme(context: Context) {

    val colors: KeyboardColors
    val keyTextPaint: Paint
    val keyLabelPaint: Paint
    val keyIconPaint: Paint

    // Dimensions (in dp, converted to px)
    private val density = context.resources.displayMetrics.density
    val keyCornerRadius = 8f * density
    val keyPadding = 3f * density
    val keyTextSize = 20f * density
    val keyLabelSize = 12f * density
    val popupTextSize = 18f * density
    val toolbarHeight = 40f * density
    val keyShadowRadius = 1f * density

    init {
        val isDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        colors = if (isDark) {
            KeyboardColors(
                keyboardBackground = Color.parseColor("#1B1B1F"),
                keyBackground = Color.parseColor("#2C2C30"),
                keyText = Color.parseColor("#E0E0E0"),
                keyPressed = Color.parseColor("#404044"),
                specialKeyBackground = Color.parseColor("#3C3C40"),
                toolbarBackground = Color.parseColor("#252528"),
                accent = Color.parseColor("#7BB3E8")
            )
        } else {
            KeyboardColors(
                keyboardBackground = Color.parseColor("#ECEFF1"),
                keyBackground = Color.parseColor("#FFFFFF"),
                keyText = Color.parseColor("#212121"),
                keyPressed = Color.parseColor("#BDBDBD"),
                specialKeyBackground = Color.parseColor("#B0BEC5"),
                toolbarBackground = Color.parseColor("#E0E0E0"),
                accent = Color.parseColor("#4A90D9")
            )
        }

        keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.keyText
            textSize = keyTextSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }

        keyLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.keyText
            textSize = keyLabelSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }

        keyIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.keyText
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
}
