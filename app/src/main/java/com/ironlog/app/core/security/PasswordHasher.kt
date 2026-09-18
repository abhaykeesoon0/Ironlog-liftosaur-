package com.ironlog.app.core.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passwords are never stored, logged or transmitted in plaintext.
 *
 * Each account gets a 16-byte random salt from [SecureRandom]; the stored value
 * is a PBKDF2-HMAC-SHA256 derivation over that salt. The iteration count is high
 * enough to make offline brute forcing expensive if the database file were ever
 * extracted from a rooted device, while staying under ~250 ms on mid-range
 * hardware so sign-in does not feel slow.
 *
 * Verification uses [MessageDigest.isEqual], which compares in constant time and
 * therefore does not leak how many leading bytes of a guess were correct.
 */
@Singleton
class PasswordHasher @Inject constructor() {

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(password: CharArray, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val derived = factory.generateSecret(spec).encoded
            Base64.encodeToString(derived, Base64.NO_WRAP)
        } finally {
            // Clear the copy the spec holds, then the caller's array is cleared
            // by the caller. Keeping a password in a String would leave it in
            // the heap until GC with no way to wipe it, which is why every entry
            // point here takes a CharArray.
            spec.clearPassword()
        }
    }

    fun verify(password: CharArray, saltBase64: String, expectedHashBase64: String): Boolean {
        val candidate = Base64.decode(hash(password, saltBase64), Base64.NO_WRAP)
        val expected = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
        return MessageDigest.isEqual(candidate, expected)
    }

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_BYTES = 16
    }
}

/** Overwrites a password buffer so it does not linger in memory. */
fun CharArray.wipe() {
    fill('\u0000')
}
