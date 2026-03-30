package io.superkeyboard.clipboard

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    @Query("SELECT * FROM clipboard_entries ORDER BY isPinned DESC, timestamp DESC LIMIT 20")
    fun getAllEntries(): Flow<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_entries WHERE text LIKE '%' || :query || '%' ORDER BY isPinned DESC, timestamp DESC")
    fun searchEntries(query: String): Flow<List<ClipboardEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardEntry)

    @Delete
    suspend fun delete(entry: ClipboardEntry)

    @Query("DELETE FROM clipboard_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clipboard_entries WHERE expiresAt < :currentTime AND isPinned = 0")
    suspend fun deleteExpired(currentTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0")
    suspend fun deleteAllUnpinned()

    @Query("DELETE FROM clipboard_entries")
    suspend fun deleteAll()

    @Query("UPDATE clipboard_entries SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("SELECT COUNT(*) FROM clipboard_entries")
    suspend fun getCount(): Int

    @Query("SELECT * FROM clipboard_entries WHERE text = :text LIMIT 1")
    suspend fun findByText(text: String): ClipboardEntry?
}
