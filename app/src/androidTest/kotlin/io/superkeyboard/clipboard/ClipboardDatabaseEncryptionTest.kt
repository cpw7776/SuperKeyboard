package io.superkeyboard.clipboard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for [ClipboardDatabase] — the real SQLCipher + Android Keystore path.
 * This is the regression guard for the project's core privacy promise: clipboard text is
 * encrypted at rest. Runs on a device/emulator (Keystore + SQLCipher native lib required).
 *
 * Clean-slate strategy (D5): each test reflectively resets the production singleton and deletes
 * the DB + passphrase files, so every test gets a fresh, production-built encrypted database —
 * without adding a test-only seam to production code.
 */
@RunWith(AndroidJUnit4::class)
class ClipboardDatabaseEncryptionTest {

    // Mirror of ClipboardDatabase's private constants (kept in sync intentionally).
    private val dbName = "clipboard.db"
    private val passphraseFile = "clipboard_passphrase"

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadNativeLib() {
            // The app loads this in SuperKeyboardApp.onCreate(); load defensively so the test
            // is order-independent (idempotent — a no-op if already loaded). See plan R1.
            System.loadLibrary("sqlcipher")
        }
    }

    @Before
    fun cleanSlate() {
        resetSingleton()
        context.deleteDatabase(dbName) // removes clipboard.db + journal/wal/shm
        File(context.filesDir, passphraseFile).delete()
    }

    /** Reflectively close + null ClipboardDatabase's @Volatile companion `instance` (D5). */
    private fun resetSingleton() {
        val field = ClipboardDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        (field.get(null) as? ClipboardDatabase)?.close()
        field.set(null, null)
    }

    private fun dbSidecars(): List<File> {
        val db = context.getDatabasePath(dbName)
        return listOf(db, File(db.path + "-wal"), File(db.path + "-shm")).filter { it.exists() }
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (i in 0..(size - needle.size)) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    // --- The privacy promise: plaintext must never reach disk --------------

    @Test
    fun clipboardText_isEncryptedAtRest() {
        val sentinel = "SENTINEL_PLAINTEXT_9c1f2b7a_should_never_hit_disk"

        val db = ClipboardDatabase.create(context)
        runBlocking { db.clipboardDao().insert(ClipboardEntry(text = sentinel)) }
        // Confirm it was genuinely persisted (so the absence check below isn't vacuous).
        runBlocking { assertThat(db.clipboardDao().findByText(sentinel)).isNotNull() }
        db.close() // checkpoint WAL into the .db file so bytes are on disk

        val files = dbSidecars()
        assertThat(files).isNotEmpty()

        val utf8 = sentinel.toByteArray(Charsets.UTF_8)
        val utf16 = sentinel.toByteArray(Charsets.UTF_16LE)
        for (f in files) {
            val bytes = f.readBytes()
            assertThat(bytes).isNotEmpty()
            assertThat(bytes.containsSequence(utf8)).isFalse()
            assertThat(bytes.containsSequence(utf16)).isFalse()
        }
    }

    // --- The encrypted DB round-trips through the real Keystore passphrase --

    @Test
    fun encryptedDatabase_roundTripsInsertQueryDelete() {
        val db = ClipboardDatabase.create(context)
        val dao = db.clipboardDao()

        runBlocking {
            dao.insert(ClipboardEntry(text = "round-trip"))
            val found = dao.findByText("round-trip")
            assertThat(found).isNotNull()

            dao.delete(found!!)
            assertThat(dao.findByText("round-trip")).isNull()
        }
    }

    // --- Negative: a wrong passphrase cannot open the encrypted file (D7) ---

    @Test
    fun wrongPassphrase_cannotOpenDatabase() {
        // First create a real encrypted DB (correct Keystore passphrase) and close it.
        val real = ClipboardDatabase.create(context)
        runBlocking { real.clipboardDao().insert(ClipboardEntry(text = "secret")) }
        real.close()
        resetSingleton()

        // Now try to open the SAME file with a deliberately wrong passphrase.
        val wrong = Room.databaseBuilder(context, ClipboardDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory("definitely-the-wrong-key".toByteArray()))
            .build()

        var threw = false
        try {
            runBlocking { wrong.clipboardDao().getCount() } // forces the file open
        } catch (e: Exception) {
            threw = true
        } finally {
            wrong.close()
        }

        assertThat(threw).isTrue()
    }
}
