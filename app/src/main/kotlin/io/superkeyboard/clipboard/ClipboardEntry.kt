package io.superkeyboard.clipboard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_entries")
data class ClipboardEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val expiresAt: Long = System.currentTimeMillis() + DEFAULT_EXPIRY_MS
) {
    companion object {
        const val DEFAULT_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
}
