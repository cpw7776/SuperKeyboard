package io.superkeyboard.clipboard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Hand-written in-memory fake of [ClipboardDao] for JVM unit tests (anti-pattern A2: fake at the
 * real seam the repository talks to, no mocking framework). It genuinely *stores* rows so behaviour
 * like dedup is observable end-to-end (A4) rather than asserting a canned stub return.
 *
 * Mimics Room's autoGenerate PK: an inserted entry with id == 0 gets the next id; a non-zero id is
 * treated as REPLACE (matches @Insert(onConflict = REPLACE) on a known PK).
 */
class FakeClipboardDao : ClipboardDao {

    private val store = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    private var nextId = 1L

    /** Read-only snapshot for test assertions. */
    val entries: List<ClipboardEntry> get() = store.value

    private fun sortedLimited(list: List<ClipboardEntry>): List<ClipboardEntry> =
        list.sortedWith(
            compareByDescending<ClipboardEntry> { it.isPinned }.thenByDescending { it.timestamp }
        ).take(20)

    override fun getAllEntries(): Flow<List<ClipboardEntry>> =
        store.map { sortedLimited(it) }

    override fun searchEntries(query: String): Flow<List<ClipboardEntry>> =
        store.map { list -> sortedLimited(list.filter { it.text.contains(query) }) }

    override suspend fun insert(entry: ClipboardEntry) {
        store.value = if (entry.id == 0L) {
            store.value + entry.copy(id = nextId++)
        } else {
            store.value.filterNot { it.id == entry.id } + entry
        }
    }

    override suspend fun delete(entry: ClipboardEntry) {
        store.value = store.value.filterNot { it.id == entry.id }
    }

    override suspend fun deleteById(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }

    override suspend fun deleteExpired(currentTime: Long) {
        store.value = store.value.filterNot { it.expiresAt < currentTime && !it.isPinned }
    }

    override suspend fun deleteAllUnpinned() {
        store.value = store.value.filter { it.isPinned }
    }

    override suspend fun deleteAll() {
        store.value = emptyList()
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        store.value = store.value.map { if (it.id == id) it.copy(isPinned = pinned) else it }
    }

    override suspend fun getCount(): Int = store.value.size

    override suspend fun findByText(text: String): ClipboardEntry? =
        store.value.firstOrNull { it.text == text }
}
