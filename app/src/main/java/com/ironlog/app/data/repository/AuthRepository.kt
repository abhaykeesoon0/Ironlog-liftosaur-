package com.ironlog.app.data.repository

import com.ironlog.app.core.security.PasswordHasher
import com.ironlog.app.core.security.SecureTokenStore
import com.ironlog.app.core.security.TokenResult
import com.ironlog.app.core.security.TokenService
import com.ironlog.app.core.security.Validators
import com.ironlog.app.core.security.wipe
import com.ironlog.app.data.dao.UserDao
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.data.entity.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.ironlog.app.core.security.LoginThrottle
import java.security.MessageDigest

sealed interface AuthResult {
    data class Success(val userId: String) : AuthResult
    data class Failure(val message: String, val field: AuthField? = null) : AuthResult
}

enum class AuthField { EMAIL, PASSWORD, CONFIRM_PASSWORD, NAME }

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String, val requiresBiometricUnlock: Boolean) : SessionState
}

interface AuthRepository {
    val sessionState: StateFlow<SessionState>
    suspend fun register(name: String, email: String, password: CharArray, confirmPassword: CharArray): AuthResult
    suspend fun signIn(email: String, password: CharArray): AuthResult
    suspend fun restoreSession(): SessionState
    suspend fun refreshIfNeeded(): Boolean
    suspend fun signOut()
    suspend fun changePassword(userId: String, current: CharArray, new: CharArray): AuthResult

    /**
     * Sets a new password for an account whose password has been forgotten.
     *
     * There is no "email it back": the app stores a salted hash and cannot
     * recover a password, and sending one by email would be handing the
     * credential to a third party in plaintext. It is also offline by design,
     * so there is nothing to send with.
     *
     * The device's own lock stands in for the reset link instead. That is a
     * sound boundary here because the training data never leaves the handset:
     * anyone who can satisfy the screen lock can already read the database, so
     * requiring it to reset a password grants nothing new, while a stranger who
     * picks the phone up has neither.
     *
     * Callers must complete a biometric or device-credential prompt first.
     */
    suspend fun resetPasswordAfterDeviceAuth(email: String, new: CharArray): AuthResult
    fun observeUser(userId: String): Flow<UserEntity?>
    suspend fun currentUser(): UserEntity?
    fun markBiometricUnlocked()
    fun lock()
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val hasher: PasswordHasher,
    private val tokens: TokenService,
    private val tokenStore: SecureTokenStore
) : AuthRepository {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    override suspend fun register(
        name: String,
        email: String,
        password: CharArray,
        confirmPassword: CharArray
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val cleanName = Validators.sanitiseSingleLine(name)
            val cleanEmail = Validators.sanitiseEmail(email)

            Validators.validateDisplayName(cleanName)?.let {
                return@withContext AuthResult.Failure(it, AuthField.NAME)
            }
            Validators.validateEmail(cleanEmail)?.let {
                return@withContext AuthResult.Failure(it, AuthField.EMAIL)
            }
            Validators.validatePassword(password)?.let {
                return@withContext AuthResult.Failure(it, AuthField.PASSWORD)
            }
            Validators.validatePasswordMatch(password, confirmPassword)?.let {
                return@withContext AuthResult.Failure(it, AuthField.CONFIRM_PASSWORD)
            }

            if (userDao.countByEmail(cleanEmail) > 0) {
                return@withContext AuthResult.Failure(
                    "An account already exists for that email",
                    AuthField.EMAIL
                )
            }

            val salt = hasher.newSalt()
            val user = UserEntity(
                id = UUID.randomUUID().toString(),
                email = cleanEmail,
                displayName = cleanName,
                passwordHash = hasher.hash(password, salt),
                passwordSalt = salt,
                createdAt = System.currentTimeMillis(),
                unitSystem = UnitSystem.METRIC
            )
            userDao.insert(user)

            issueSession(user)
            AuthResult.Success(user.id)
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Could not create the account")
        } finally {
            password.wipe()
            confirmPassword.wipe()
        }
    }

    override suspend fun signIn(email: String, password: CharArray): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val cleanEmail = Validators.sanitiseEmail(email)
                Validators.validateEmail(cleanEmail)?.let {
                    return@withContext AuthResult.Failure(it, AuthField.EMAIL)
                }
                if (password.isEmpty()) {
                    return@withContext AuthResult.Failure("Enter your password", AuthField.PASSWORD)
                }

                // Held back after a run of failures on this address. Checked
                // before the password is even looked at, so a locked account
                // costs an attacker the wait rather than a hash comparison.
                val throttleKey = throttleKey(cleanEmail)
                val now = System.currentTimeMillis()
                val (failures, lockedUntil) = tokenStore.loginThrottle(throttleKey)
                val throttle = LoginThrottle.State(failures, lockedUntil)
                val wait = LoginThrottle.remainingLockMs(throttle, now)
                if (wait > 0) return@withContext AuthResult.Failure(LoginThrottle.describe(wait))

                val user = userDao.findByEmail(cleanEmail)

                // A fixed delay on every attempt, and one message for both a
                // missing account and a wrong password. Saying "no account with
                // that email" would let anyone enumerate which addresses are
                // registered, and returning instantly on a missing account would
                // leak the same thing through timing.
                delay(FAILED_ATTEMPT_DELAY_MS)

                if (user == null || !hasher.verify(password, user.passwordSalt, user.passwordHash)) {
                    val next = LoginThrottle.afterFailure(throttle, now)
                    tokenStore.saveLoginThrottle(throttleKey, next.failures, next.lockedUntil)
                    val remaining = LoginThrottle.remainingLockMs(next, now)
                    return@withContext AuthResult.Failure(
                        if (remaining > 0) LoginThrottle.describe(remaining) else "Email or password is incorrect"
                    )
                }

                tokenStore.saveLoginThrottle(throttleKey, 0, 0L)
                issueSession(user)
                AuthResult.Success(user.id)
            } catch (e: Exception) {
                AuthResult.Failure(e.message ?: "Could not sign in")
            } finally {
                password.wipe()
            }
        }

    /** A hash of the address, so the throttle store is not a list of accounts. */
    private fun throttleKey(email: String): String =
        MessageDigest.getInstance("SHA-256").digest(email.toByteArray()).take(12)
            .joinToString("") { "%02x".format(it) }

    /**
     * Cold-start path. Verifies the stored access token, silently refreshes it if
     * it has expired but the refresh token is still good, and only falls back to
     * the sign-in screen when neither is usable.
     */
    override suspend fun restoreSession(): SessionState = withContext(Dispatchers.IO) {
        val userId = tokenStore.userId()
        val accessToken = tokenStore.accessToken()

        if (userId == null || accessToken == null) {
            _sessionState.value = SessionState.SignedOut
            return@withContext SessionState.SignedOut
        }

        val state = when (val result = tokens.verify(accessToken)) {
            is TokenResult.Valid -> {
                val user = userDao.findById(result.claims.subject)
                if (user == null) {
                    // Token references an account that no longer exists.
                    tokenStore.clearSession()
                    SessionState.SignedOut
                } else {
                    SessionState.SignedIn(user.id, user.biometricLockEnabled)
                }
            }

            TokenResult.Expired -> {
                val refreshed = tokenStore.refreshToken()?.let { tokens.refresh(it) }
                val user = refreshed?.let { userDao.findById(userId) }
                if (refreshed != null && user != null) {
                    tokenStore.saveSession(user.id, refreshed)
                    SessionState.SignedIn(user.id, user.biometricLockEnabled)
                } else {
                    tokenStore.clearSession()
                    SessionState.SignedOut
                }
            }

            // A tampered or corrupt token is treated as hostile: the session is
            // destroyed rather than repaired.
            TokenResult.Malformed, TokenResult.SignatureMismatch -> {
                tokenStore.clearSession()
                SessionState.SignedOut
            }
        }

        _sessionState.value = state
        state
    }

    /** Called before sensitive work; renews the access token as it nears expiry. */
    override suspend fun refreshIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val expiry = tokenStore.accessExpiry()
        if (expiry == 0L) return@withContext false
        if (System.currentTimeMillis() < expiry - TokenService.REFRESH_THRESHOLD_MS) {
            return@withContext true
        }

        val userId = tokenStore.userId() ?: return@withContext false
        val refreshed = tokenStore.refreshToken()?.let { tokens.refresh(it) }
            ?: run {
                signOut()
                return@withContext false
            }
        tokenStore.saveSession(userId, refreshed)
        true
    }

    override suspend fun signOut() {
        tokenStore.clearSession()
        _sessionState.value = SessionState.SignedOut
    }

    override suspend fun resetPasswordAfterDeviceAuth(email: String, new: CharArray): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val cleanEmail = Validators.sanitiseEmail(email)
                val user = userDao.findByEmail(cleanEmail)
                    ?: return@withContext AuthResult.Failure(
                        "No account on this phone uses that email",
                        AuthField.EMAIL
                    )
                Validators.validatePassword(new)?.let {
                    return@withContext AuthResult.Failure(it, AuthField.PASSWORD)
                }

                val salt = hasher.newSalt()
                userDao.update(user.copy(passwordHash = hasher.hash(new, salt), passwordSalt = salt))

                // Signs in as part of the reset: the user has just proved they
                // hold the device and set the password, so a second trip
                // through the sign-in form proves nothing further.
                issueSession(user)
                AuthResult.Success(user.id)
            } finally {
                new.wipe()
            }
        }

    override suspend fun changePassword(
        userId: String,
        current: CharArray,
        new: CharArray
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val user = userDao.findById(userId)
                ?: return@withContext AuthResult.Failure("Account not found")

            if (!hasher.verify(current, user.passwordSalt, user.passwordHash)) {
                return@withContext AuthResult.Failure(
                    "Current password is incorrect",
                    AuthField.PASSWORD
                )
            }
            Validators.validatePassword(new)?.let {
                return@withContext AuthResult.Failure(it, AuthField.PASSWORD)
            }

            // New salt as well as a new hash: reusing the salt would let anyone
            // holding both hashes see that the passwords were derived the same way.
            val salt = hasher.newSalt()
            userDao.update(
                user.copy(passwordHash = hasher.hash(new, salt), passwordSalt = salt)
            )

            // Every existing token is invalidated by forcing a fresh session.
            issueSession(user)
            AuthResult.Success(user.id)
        } catch (e: Exception) {
            AuthResult.Failure(e.message ?: "Could not change the password")
        } finally {
            current.wipe()
            new.wipe()
        }
    }

    override fun observeUser(userId: String): Flow<UserEntity?> = userDao.observeById(userId)

    override suspend fun currentUser(): UserEntity? = withContext(Dispatchers.IO) {
        tokenStore.userId()?.let { userDao.findById(it) }
    }

    override fun markBiometricUnlocked() {
        val state = _sessionState.value
        if (state is SessionState.SignedIn) {
            _sessionState.value = state.copy(requiresBiometricUnlock = false)
        }
    }

    override fun lock() {
        val state = _sessionState.value
        if (state is SessionState.SignedIn) {
            _sessionState.value = state.copy(requiresBiometricUnlock = true)
        }
    }

    private fun issueSession(user: UserEntity) {
        val pair = tokens.issue(user.id, user.email)
        tokenStore.saveSession(user.id, pair)
        _sessionState.value = SessionState.SignedIn(user.id, requiresBiometricUnlock = false)
    }

    private companion object {
        /**
         * Constant cost per sign-in attempt. Combined with PBKDF2's own ~200 ms,
         * this makes an on-device brute force impractically slow.
         */
        const val FAILED_ATTEMPT_DELAY_MS = 350L
    }
}
