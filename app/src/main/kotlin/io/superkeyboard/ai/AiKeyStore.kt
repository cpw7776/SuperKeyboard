package io.superkeyboard.ai

import android.content.Context
import io.superkeyboard.util.KeystoreSecretBox
import java.io.File

/**
 * Stores the user's AI API key, encrypted at rest via [KeystoreSecretBox] under a dedicated
 * Keystore alias [ALIAS] (NOT the clipboard's alias) and app-private file [FILE_NAME].
 *
 * Privacy invariant (ADR D2/D3): the API key is a credential — it is Keystore-encrypted, never
 * stored in DataStore, and NEVER logged. Do not add the key to any log statement or crash report.
 */
class AiKeyStore(context: Context) {

    private val box = KeystoreSecretBox(ALIAS, File(context.filesDir, FILE_NAME))

    /** Encrypt and persist the API key. */
    fun setKey(key: String) = box.put(key)

    /** Return the decrypted API key, or null if none has been set. */
    fun getKey(): String? = box.get()

    /** Delete the stored API key. */
    fun clear() = box.clear()

    private companion object {
        // Distinct from superkeyboard_clipboard_key — never reuse the clipboard alias (ADR D2).
        const val ALIAS = "superkeyboard_ai_key"
        const val FILE_NAME = "ai_api_key.enc"
    }
}
