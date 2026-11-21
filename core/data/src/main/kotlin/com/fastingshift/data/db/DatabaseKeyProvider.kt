package com.fastingshift.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates a random 32-byte database passphrase once, encrypts it with an AES-GCM key held
 * in the Android Keystore, and stores only the encrypted form in app-private preferences.
 *
 * Auto Backup is disabled for the app (the Keystore key cannot be backed up, so a restored
 * database would be unreadable). User backups go through export/restore (PRD F8).
 */
class DatabaseKeyProvider internal constructor(private val context: Context) {

    enum class Status {
        /** First launch: no database and no key yet. */
        NEW,

        /** Key and database present and the key unwraps. */
        OK,

        /** A database exists but its key is gone or unusable; data can't be decrypted. */
        LOST,
    }

    /** Checks the key without opening the database. */
    fun status(dbName: String): Status {
        val dbExists = context.getDatabasePath(dbName).exists()
        val stored = prefs().getString(KEY_WRAPPED, null)
        if (stored == null) return if (dbExists) Status.LOST else Status.NEW
        if (!hasKeystoreKey()) return Status.LOST
        return try {
            unwrap(Base64.decode(stored, Base64.NO_WRAP))
            if (dbExists) Status.OK else Status.NEW
        } catch (e: Exception) {
            Status.LOST
        }
    }

    /**
     * Returns the passphrase, creating one on first launch. Never silently replaces a lost
     * key, which would make the existing database unreadable: throws [DatabaseKeyException].
     */
    fun getOrCreatePassphrase(): ByteArray {
        val prefs = prefs()
        val stored = prefs.getString(KEY_WRAPPED, null)
        if (stored != null) {
            if (!hasKeystoreKey()) throw DatabaseKeyException("Keystore key is missing")
            return try {
                unwrap(Base64.decode(stored, Base64.NO_WRAP))
            } catch (e: Exception) {
                throw DatabaseKeyException("Database key can't be unwrapped", e)
            }
        }
        if (context.getDatabasePath(FastingDatabase.NAME).exists()) throw DatabaseKeyException("Database exists without a key")

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = wrap(passphrase)
        check(prefs.edit().putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit()) {
            "could not persist database key"
        }
        return passphrase
    }

    /** Removes the stored key and the Keystore entry (used with deleting the database). */
    fun reset() {
        prefs().edit().remove(KEY_WRAPPED).commit()
        runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hasKeystoreKey(): Boolean =
        runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.containsAlias(ALIAS) }.getOrDefault(false)

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val ivLength = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLength)
        val body = blob.copyOfRange(1 + ivLength, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {

        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "fasting_shift_db_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS = "fasting_shift_db"
        const val KEY_WRAPPED = "wrapped_passphrase"
    }
}

/** The database key is lost (e.g. Keystore cleared); the app must offer recovery, not crash. */
class DatabaseKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)
