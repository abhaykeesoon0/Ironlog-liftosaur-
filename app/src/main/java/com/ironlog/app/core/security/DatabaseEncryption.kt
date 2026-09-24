package com.ironlog.app.core.security

import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.security.SecureRandom

/**
 * The database is encrypted at rest with SQLCipher (AES-256-CBC per page,
 * key derived from a passphrase with PBKDF2).
 *
 * The passphrase is 32 random bytes made once on this device and kept in the
 * same Keystore-encrypted preferences as the session tokens. It never leaves
 * the phone and is never typed, so the log is unreadable to anything that
 * copies the file -- a rooted phone, a stolen backup, a debugger -- without
 * also having the key in the Keystore.
 *
 * Installs that predate encryption have a plain SQLite file. That file is
 * converted in place on the first launch: opened without a key, exported
 * page by page into an encrypted copy with SQLCipher's own export, and only
 * then swapped for the original. A crash midway leaves the plaintext file
 * untouched and the conversion is simply tried again.
 */
object DatabaseEncryption {

    private const val TAG = "IronLog"
    private const val PLAINTEXT_HEADER = "SQLite format 3\u0000"

    /** The passphrase bytes for this device, created on first use. */
    fun passphrase(store: SecureTokenStore): ByteArray {
        val existing = store.databaseKey()
        if (existing != null) return existing.toByteArray(Charsets.UTF_8)
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        store.saveDatabaseKey(hex)
        return hex.toByteArray(Charsets.UTF_8)
    }

    /** True when the file on disk is a plain, unencrypted SQLite database. */
    fun isPlaintext(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        return String(header, Charsets.ISO_8859_1) == PLAINTEXT_HEADER
    }

    /**
     * Converts a plaintext database at [name] into an encrypted one under the
     * same name, if it is not encrypted already. Returns whether a conversion
     * happened.
     */
    fun ensureEncrypted(context: Context, name: String, passphrase: ByteArray): Boolean {
        val file = context.getDatabasePath(name)
        if (!isPlaintext(file)) return false

        val encrypted = File(file.parentFile, "$name.encrypting")
        encrypted.delete()
        val key = String(passphrase, Charsets.UTF_8)

        // CREATE_IF_NECESSARY: the attached target is a new file, and an ATTACH
        // can only create one if the connection itself was opened with the right.
        val plain = SQLiteDatabase.openDatabase(
            file.absolutePath, "", null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null, null
        )
        try {
            plain.rawExecSQL("ATTACH DATABASE '${encrypted.absolutePath.replace("'", "''")}' AS encrypted KEY '${key}'")
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            // The schema version travels with the pages; the user_version does not.
            val version = plain.version
            plain.rawExecSQL("PRAGMA encrypted.user_version = $version")
            plain.rawExecSQL("DETACH DATABASE encrypted")
        } finally {
            plain.close()
        }

        // Swap: the old file and its journal go only once the new one is in place.
        val wal = File(file.parentFile, "$name-wal")
        val shm = File(file.parentFile, "$name-shm")
        val journal = File(file.parentFile, "$name-journal")
        if (!encrypted.renameTo(file)) {
            // A rename across the same directory should not fail; if it does,
            // leave the plaintext file alone rather than risk both.
            encrypted.delete()
            throw IllegalStateException("Could not replace the database with its encrypted copy")
        }
        wal.delete(); shm.delete(); journal.delete()
        Log.i(TAG, "Database encrypted at rest")
        return true
    }
}
