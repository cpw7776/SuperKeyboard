package io.superkeyboard.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

/**
 * Instrumented tests for [KeystoreSecretBox] — the real Android Keystore AES-256-GCM path.
 * Runs on a device/emulator (Keystore is unavailable on the JVM, so this cannot live in
 * src/test). Mirrors the strategy in [io.superkeyboard.clipboard.ClipboardDatabaseEncryptionTest].
 *
 * Verifies the privacy-relevant contract: round-trip fidelity (incl. empty + long inputs,
 * anti-pattern A3), that the on-disk ciphertext does NOT contain the plaintext, and that
 * get() is null after clear(). Asserts behaviour, not implementation (A1).
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretBoxInstrumentedTest {

    private val testAlias = "superkeyboard_test_secretbox_key"
    private val testFileName = "secretbox_test.enc"

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun testFile(): File = File(context.filesDir, testFileName)

    private fun newBox(): KeystoreSecretBox = KeystoreSecretBox(testAlias, testFile())

    @Before
    fun cleanSlate() {
        testFile().delete()
        deleteKeystoreAlias()
    }

    @After
    fun tearDown() {
        testFile().delete()
        deleteKeystoreAlias()
    }

    private fun deleteKeystoreAlias() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(testAlias)) keyStore.deleteEntry(testAlias)
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (i in 0..(size - needle.size)) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    @Test
    fun put_then_get_roundTripsOriginalValue() = runBlocking {
        val box = newBox()
        val secret = "sk-test-1234567890_AÉ你好"
        box.put(secret)
        assertThat(box.get()).isEqualTo(secret)
    }

    @Test
    fun put_then_get_roundTripsEmptyString() = runBlocking {
        val box = newBox()
        box.put("")
        assertThat(box.get()).isEqualTo("")
    }

    @Test
    fun put_then_get_roundTripsLongInput() = runBlocking {
        val box = newBox()
        val long = "x".repeat(20_000) + "_END"
        box.put(long)
        assertThat(box.get()).isEqualTo(long)
    }

    @Test
    fun onDiskBytes_doNotContainPlaintext() = runBlocking {
        val box = newBox()
        val sentinel = "SENTINEL_API_KEY_should_never_hit_disk_in_clear"
        box.put(sentinel)

        val bytes = testFile().readBytes()
        assertThat(bytes).isNotEmpty()
        assertThat(bytes.containsSequence(sentinel.toByteArray(Charsets.UTF_8))).isFalse()
        assertThat(bytes.containsSequence(sentinel.toByteArray(Charsets.UTF_16LE))).isFalse()
    }

    @Test
    fun get_isNull_whenFileAbsent() = runBlocking {
        val box = newBox()
        assertThat(box.get()).isNull()
    }

    @Test
    fun get_isNull_afterClear() = runBlocking {
        val box = newBox()
        box.put("to-be-cleared")
        box.clear()
        assertThat(box.get()).isNull()
        assertThat(testFile().exists()).isFalse()
    }
}
