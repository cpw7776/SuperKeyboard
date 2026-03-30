package io.superkeyboard.ime

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent

class GestureHandler(
    private val onKeyPress: (Key) -> Unit,
    private val onKeyLongPress: (Key) -> Unit,
    private val onKeyRelease: (Key) -> Unit,
    private val onKeyRepeat: (Key) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var activeKey: Key? = null
    private var repeatRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false

    companion object {
        private const val LONG_PRESS_DELAY_MS = 300L
        private const val REPEAT_DELAY_MS = 50L
        private const val REPEAT_INITIAL_DELAY_MS = 400L
    }

    fun onTouchEvent(event: MotionEvent, findKey: (Float, Float) -> Key?): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKey(event.x, event.y) ?: return false
                handleKeyDown(key)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val key = findKey(event.x, event.y)
                if (key != activeKey) {
                    cancelPendingCallbacks()
                    activeKey?.let { onKeyRelease(it) }
                    if (key != null) {
                        handleKeyDown(key)
                    } else {
                        activeKey = null
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPendingCallbacks()
                activeKey?.let {
                    if (!isLongPressTriggered) {
                        onKeyRelease(it)
                    }
                }
                activeKey = null
                isLongPressTriggered = false
                return true
            }
        }
        return false
    }

    private fun handleKeyDown(key: Key) {
        activeKey = key
        isLongPressTriggered = false
        onKeyPress(key)

        if (key.longPressKeys.isNotEmpty()) {
            longPressRunnable = Runnable {
                isLongPressTriggered = true
                onKeyLongPress(key)
            }
            handler.postDelayed(longPressRunnable!!, LONG_PRESS_DELAY_MS)
        }

        if (key.isRepeatable) {
            repeatRunnable = object : Runnable {
                override fun run() {
                    if (activeKey == key) {
                        onKeyRepeat(key)
                        handler.postDelayed(this, REPEAT_DELAY_MS)
                    }
                }
            }
            handler.postDelayed(repeatRunnable!!, REPEAT_INITIAL_DELAY_MS)
        }
    }

    private fun cancelPendingCallbacks() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
        repeatRunnable = null
    }
}
