package io.superkeyboard.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generic Android-Keystore-backed secret box: AES-256-GCM encrypt/decrypt of a single UTF-8
 * string to an app-private file. Mirrors the inline crypto pattern in
 * [io.superkeyboard.clipboard.ClipboardDatabase] (on-disk layout `[1 byte ivLen][iv][ciphertext]`,
 * `AES/GCM/NoPadding`, GCM tag 128).
 *
 * This helper is intentionally generic — it knows nothing about the clipboard or the AI key.
 * The Keystore key is lazily generated under [alias] on first use if absent. Do NOT point two
 * different secrets at the same alias, and never reuse the clipboard's alias.
 *
 * @param alias the AndroidKeyStore alias for this box's AES-256 key.
 * @param file  the app-private file the ciphertext is written to.
 */
class KeystoreSecretBox(
    private val alias: String,
    private val file: File,
) {
    /** Encrypt [plaintext] (UTF-8) and write `[ivLen][iv][ciphertext]` to the file. */
    fun put(plaintext: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = byteArrayOf(iv.size.toByte()) + iv + ciphertext
        file.writeBytes(out)
    }

    /** Decrypt and return the stored string, or null if the file is absent. */
    fun get(): String? {
        if (!file.exists()) return null
        val data = file.readBytes()
        val ivLength = data[0].toInt()
        val iv = data.sliceArray(1..ivLength)
        val ciphertext = data.sliceArray((ivLength + 1) until data.size)
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /** Delete the ciphertext file. The Keystore key is left in place. */
    fun clear() {
        file.delete()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
        return keyStore.getKey(alias, null) as SecretKey
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
