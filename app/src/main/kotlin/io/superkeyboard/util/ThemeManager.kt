package io.superkeyboard.util

import android.content.Context
import android.content.res.Configuration

object ThemeManager {

    fun isDarkTheme(context: Context): Boolean {
        return (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
