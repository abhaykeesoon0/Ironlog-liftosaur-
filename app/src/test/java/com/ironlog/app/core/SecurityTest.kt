package com.ironlog.app.core

import com.ironlog.app.core.security.BackupCrypto
import com.ironlog.app.core.security.LoginThrottle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {

    private val crypto = BackupCrypto()
    private val payload = """{"format":"ironlog-backup","workouts":[1,2,3]}""".toByteArray()

    @Test
    fun `a backup round-trips under its passphrase`() {
        val sealed = crypto.encrypt(payload, "correct horse battery".toCharArray())
        assertTrue(crypto.looksLikeBackup(sealed))
        assertArrayEquals(payload, crypto.decrypt(sealed, "correct horse battery".toCharArray()))
    }

    @Test
    fun `the wrong passphrase fails cleanly rather than decrypting to rubbish`() {
        val sealed = crypto.encrypt(payload, "correct horse battery".toCharArray())
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            crypto.decrypt(sealed, "correct horse batter".toCharArray())
        }
    }

    @Test
    fun `a byte changed anywhere in the file is refused`() {
        val sealed = crypto.encrypt(payload, "correct horse battery".toCharArray())
        val tampered = sealed.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            crypto.decrypt(tampered, "correct horse battery".toCharArray())
        }
    }

    @Test
    fun `two backups of the same data share no bytes beyond the header`() {
        val a = crypto.encrypt(payload, "correct horse battery".toCharArray())
        val b = crypto.encrypt(payload, "correct horse battery".toCharArray())
        assertFalse(a.copyOfRange(8, a.size).contentEquals(b.copyOfRange(8, b.size)))
    }

    @Test
    fun `a file that is not a backup is named as such`() {
        assertFalse(crypto.looksLikeBackup("hello".toByteArray()))
        assertThrows(BackupCrypto.NotABackupException::class.java) {
            crypto.decrypt("definitely not a backup file".toByteArray(), "whatever".toCharArray())
        }
    }

    @Test
    fun `a couple of mistyped passwords cost nothing`() {
        var state = LoginThrottle.State()
        repeat(LoginThrottle.FREE_ATTEMPTS - 1) { state = LoginThrottle.afterFailure(state, now = 1_000L) }
        assertEquals(0L, LoginThrottle.remainingLockMs(state, now = 1_000L))
    }

    @Test
    fun `the fifth failure starts a wait that doubles and then stops growing`() {
        var state = LoginThrottle.State()
        repeat(LoginThrottle.FREE_ATTEMPTS) { state = LoginThrottle.afterFailure(state, now = 0L) }
        assertEquals(30_000L, LoginThrottle.remainingLockMs(state, now = 0L))
        state = LoginThrottle.afterFailure(state, now = 0L)
        assertEquals(60_000L, LoginThrottle.remainingLockMs(state, now = 0L))
        repeat(20) { state = LoginThrottle.afterFailure(state, now = 0L) }
        assertEquals(15 * 60_000L, LoginThrottle.remainingLockMs(state, now = 0L))
    }

    @Test
    fun `a success clears the slate and a wait runs down with the clock`() {
        var state = LoginThrottle.State()
        repeat(LoginThrottle.FREE_ATTEMPTS) { state = LoginThrottle.afterFailure(state, now = 0L) }
        assertEquals(10_000L, LoginThrottle.remainingLockMs(state, now = 20_000L))
        assertEquals(0L, LoginThrottle.remainingLockMs(LoginThrottle.afterSuccess(), now = 0L))
        assertTrue(LoginThrottle.describe(30_000L).contains("30 seconds"))
        assertTrue(LoginThrottle.describe(5 * 60_000L).contains("5 minutes"))
    }
}
