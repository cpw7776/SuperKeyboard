package io.superkeyboard.clipboard

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipboardManagerService(context: Context, private val repository: ClipboardRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = systemClipboard.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: return@OnPrimaryClipChangedListener
            if (text.isNotBlank()) {
                scope.launch {
                    repository.addEntry(text)
                }
            }
        }
    }

    fun startListening() {
        systemClipboard.addPrimaryClipChangedListener(clipChangedListener)
        // Cleanup expired entries on start
        scope.launch {
            repository.cleanupExpired()
        }
    }

    fun stopListening() {
        systemClipboard.removePrimaryClipChangedListener(clipChangedListener)
    }
}
