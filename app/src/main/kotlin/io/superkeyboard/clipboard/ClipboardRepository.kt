package io.superkeyboard.clipboard

import kotlinx.coroutines.flow.Flow

class ClipboardRepository(private val dao: ClipboardDao) {

    fun getAllEntries(): Flow<List<ClipboardEntry>> = dao.getAllEntries()

    fun searchEntries(query: String): Flow<List<ClipboardEntry>> = dao.searchEntries(query)

    suspend fun addEntry(text: String, expiryMs: Long = ClipboardEntry.DEFAULT_EXPIRY_MS) {
        // Avoid duplicates: if same text exists, update timestamp
        val existing = dao.findByText(text)
        if (existing != null) {
            dao.delete(existing)
        }

        val entry = ClipboardEntry(
            text = text,
            expiresAt = if (expiryMs > 0) System.currentTimeMillis() + expiryMs else Long.MAX_VALUE
        )
        dao.insert(entry)
    }

    suspend fun deleteEntry(entry: ClipboardEntry) {
        dao.delete(entry)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun togglePin(entry: ClipboardEntry) {
        dao.setPinned(entry.id, !entry.isPinned)
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    suspend fun clearUnpinned() {
        dao.deleteAllUnpinned()
    }

    suspend fun cleanupExpired() {
        dao.deleteExpired()
    }
}
