package com.ironlog.app.data.repository

import com.ironlog.app.core.security.SecureTokenStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the signed-in user's id for repositories.
 *
 * The id comes from the encrypted session store rather than from a mutable
 * global, so there is exactly one source of truth: if the session is cleared,
 * every repository immediately stops being able to read or write user data.
 */
@Singleton
class CurrentUser @Inject constructor(
    private val tokenStore: SecureTokenStore
) {
    fun idOrNull(): String? = tokenStore.userId()

    fun require(): String = tokenStore.userId() ?: throw NotSignedInException()
}

class NotSignedInException : IllegalStateException("You've been signed out. Sign in again to continue.")

/**
 * Converts any failure into a message fit to show a user. Repository code wraps
 * its work in [runCatching]; ViewModels call this on the failure branch so no
 * raw exception text such as a SQLite constraint name reaches the screen.
 */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is NotSignedInException -> message ?: fallback
    is UserFacingException -> message ?: fallback
    else -> fallback
}

/** An exception whose message was written for the user and may be shown as-is. */
class UserFacingException(message: String) : Exception(message)
