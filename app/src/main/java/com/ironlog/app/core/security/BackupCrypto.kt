package com.ironlog.app.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts a backup under a passphrase, and decrypts one.
 *
 * A backup is the one copy of the log that leaves the phone's sandbox, so it
 * is the one copy that has to protect itself. The passphrase is stretched
 * with PBKDF2 into an AES-256 key, and the file is sealed with GCM, which
 * authenticates as well as encrypts: a byte changed anywhere, or the wrong
 * passphrase, fails cleanly rather than producing garbage that looks like a
 * log.
 *
 * The file is a header, a fresh salt, a fresh nonce and the ciphertext. Salt
 * and nonce are random per file, so two backups of the same data under the
 * same passphrase share nothing an attacker could compare. Nothing here is
 * home-made: the primitives are the platform's, and this class only decides
 * how they are put together.
 */
@Singleton
class BackupCrypto @Inject constructor() {

    class WrongPassphraseException : Exception("The passphrase is wrong, or the file has been altered")
    class NotABackupException : Exception("That is not an IronLog backup")

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE) { "Passphrase too short" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
        // The header is authenticated too, so a file cannot be relabelled.
        cipher.updateAAD(MAGIC)
        val sealed = cipher.doFinal(plain)
        return MAGIC + salt + nonce + sealed
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): ByteArray {
        if (file.size < MAGIC.size + SALT_BYTES + NONCE_BYTES + TAG_BITS / 8) throw NotABackupException()
        if (!file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackupException()
        var at = MAGIC.size
        val salt = file.copyOfRange(at, at + SALT_BYTES); at += SALT_BYTES
        val nonce = file.copyOfRange(at, at + NONCE_BYTES); at += NONCE_BYTES
        val sealed = file.copyOfRange(at, file.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(MAGIC)
        return try {
            cipher.doFinal(sealed)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassphraseException()
        }
    }

    /** Whether a file even claims to be one of ours, before a passphrase is asked for. */
    fun looksLikeBackup(file: ByteArray): Boolean =
        file.size > MAGIC.size && file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        try {
            val raw = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES")
        } finally {
            // The spec keeps its own copy of the passphrase; clear it too.
            spec.clearPassword()
        }
    }

    private val random = SecureRandom()

    companion object {
        /** Eight bytes that name the format, and its version. */
        val MAGIC: ByteArray = "IRNLOGB1".toByteArray(Charsets.US_ASCII)
        const val MIN_PASSPHRASE = 8
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val KDF = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
    }
}
