package io.superkeyboard.clipboard

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Database(entities = [ClipboardEntry::class], version = 1, exportSchema = false)
abstract class ClipboardDatabase : RoomDatabase() {

    abstract fun clipboardDao(): ClipboardDao

    companion object {
        private const val DB_NAME = "clipboard.db"
        private const val KEYSTORE_ALIAS = "superkeyboard_clipboard_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PASSPHRASE_FILE = "clipboard_passphrase"

        @Volatile
        private var instance: ClipboardDatabase? = null

        fun create(context: Context): ClipboardDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): ClipboardDatabase {
            val passphrase = getOrCreatePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                ClipboardDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }

        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }

            val file = java.io.File(context.filesDir, PASSPHRASE_FILE)

            return if (file.exists()) {
                decryptPassphrase(keyStore, file.readBytes())
            } else {
                val passphrase = generateRandomPassphrase()
                val encrypted = encryptPassphrase(keyStore, passphrase)
                file.writeBytes(encrypted)
                passphrase
            }
        }

        private fun generateRandomPassphrase(): ByteArray {
            val random = java.security.SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return bytes
        }

        private fun encryptPassphrase(keyStore: KeyStore, passphrase: ByteArray): ByteArray {
            val key = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(passphrase)
            return byteArrayOf(iv.size.toByte()) + iv + encrypted
        }

        private fun decryptPassphrase(keyStore: KeyStore, data: ByteArray): ByteArray {
            val ivLength = data[0].toInt()
            val iv = data.sliceArray(1..ivLength)
            val encrypted = data.sliceArray((ivLength + 1) until data.size)
            val key = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return cipher.doFinal(encrypted)
        }
    }
}
