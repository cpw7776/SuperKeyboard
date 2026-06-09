package io.superkeyboard.clipboard

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [ClipboardRepository] over a hand-written [FakeClipboardDao] (anti-pattern A2:
 * fake at the DAO seam, not the repo's own methods). Assertions observe stored state the fake could
 * not have faked alone (A4). Suspend functions run under [runTest] so assertions are actually
 * awaited (A7). Covers dedup, expiry boundaries, pin, and clear/cleanup delegation (A3).
 *
 * Expiry uses a range assertion (D3) because the production code calls System.currentTimeMillis()
 * directly and has no injectable clock — asserting an exact timestamp would be flaky.
 */
class ClipboardRepositoryTest {

    private lateinit var dao: FakeClipboardDao
    private lateinit var repo: ClipboardRepository

    @Before
    fun setUp() {
        dao = FakeClipboardDao()
        repo = ClipboardRepository(dao)
    }

    @Test
    fun `addEntry inserts a new row for novel text`() = runTest {
        repo.addEntry("hello")

        assertThat(dao.getCount()).isEqualTo(1)
        assertThat(dao.findByText("hello")).isNotNull()
    }

    @Test
    fun `addEntry sets expiresAt to now plus expiryMs for positive expiry`() = runTest {
        val expiryMs = 60_000L

        val before = System.currentTimeMillis()
        repo.addEntry("ephemeral", expiryMs)
        val after = System.currentTimeMillis()

        val stored = dao.findByText("ephemeral")!!
        assertThat(stored.expiresAt).isAtLeast(before + expiryMs)
        assertThat(stored.expiresAt).isAtMost(after + expiryMs)
    }

    @Test
    fun `addEntry with zero expiry never expires`() = runTest {
        repo.addEntry("forever", 0)

        assertThat(dao.findByText("forever")!!.expiresAt).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `addEntry with negative expiry never expires`() = runTest {
        repo.addEntry("also-forever", -5)

        assertThat(dao.findByText("also-forever")!!.expiresAt).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `addEntry dedups same text to a single row`() = runTest {
        repo.addEntry("dup")
        repo.addEntry("dup")

        assertThat(dao.getCount()).isEqualTo(1)
        assertThat(dao.entries.single().text).isEqualTo("dup")
    }

    @Test
    fun `togglePin flips the stored pinned state`() = runTest {
        repo.addEntry("pin-me")
        val entry = dao.entries.single()
        assertThat(entry.isPinned).isFalse()

        repo.togglePin(entry)
        assertThat(dao.entries.single().isPinned).isTrue()

        repo.togglePin(dao.entries.single())
        assertThat(dao.entries.single().isPinned).isFalse()
    }

    @Test
    fun `clearAll removes every entry`() = runTest {
        repo.addEntry("a")
        repo.addEntry("b")

        repo.clearAll()

        assertThat(dao.getCount()).isEqualTo(0)
    }

    @Test
    fun `clearUnpinned removes only unpinned entries`() = runTest {
        repo.addEntry("keep")
        repo.togglePin(dao.entries.single { it.text == "keep" }) // pin "keep"
        repo.addEntry("drop")                                     // unpinned

        repo.clearUnpinned()

        assertThat(dao.entries.map { it.text }).containsExactly("keep")
    }

    @Test
    fun `cleanupExpired removes expired unpinned entries but keeps pinned and fresh ones`() = runTest {
        val now = System.currentTimeMillis()
        // Seed directly via the dao to control timestamps/pin state precisely.
        dao.insert(ClipboardEntry(text = "expired-unpinned", expiresAt = now - 1_000, isPinned = false))
        dao.insert(ClipboardEntry(text = "expired-pinned", expiresAt = now - 1_000, isPinned = true))
        dao.insert(ClipboardEntry(text = "fresh", expiresAt = now + 60_000, isPinned = false))

        repo.cleanupExpired()

        assertThat(dao.entries.map { it.text })
            .containsExactly("expired-pinned", "fresh")
    }
}
