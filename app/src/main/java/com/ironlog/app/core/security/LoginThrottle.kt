package com.ironlog.app.core.security

import kotlin.math.min

/**
 * Slows a password guesser down.
 *
 * After a run of failures on one email the next attempt is refused for a
 * while, and the wait doubles with each further failure up to a ceiling. A
 * legitimate user who mistypes twice is never touched; someone trying a list
 * of passwords is held to a few attempts an hour. Pure logic with the clock
 * passed in, so the schedule can be tested without waiting for it.
 *
 * The state is stored by the caller, keyed by a hash of the email rather
 * than the email itself, so the store does not become a list of accounts.
 */
object LoginThrottle {

    /** How many failures are free before the first wait. */
    const val FREE_ATTEMPTS = 5

    private const val FIRST_WAIT_MS = 30_000L
    private const val MAX_WAIT_MS = 15 * 60_000L

    data class State(val failures: Int = 0, val lockedUntil: Long = 0L)

    /** Milliseconds still to wait, or zero if an attempt may be made now. */
    fun remainingLockMs(state: State, now: Long): Long = (state.lockedUntil - now).coerceAtLeast(0L)

    /** The state after one more failure at [now]. */
    fun afterFailure(state: State, now: Long): State {
        val failures = state.failures + 1
        if (failures < FREE_ATTEMPTS) return State(failures, 0L)
        val exponent = failures - FREE_ATTEMPTS
        val wait = min(FIRST_WAIT_MS shl exponent.coerceAtMost(10), MAX_WAIT_MS)
        return State(failures, now + wait)
    }

    /** A success clears the slate. */
    fun afterSuccess(): State = State()

    fun describe(remainingMs: Long): String {
        val seconds = (remainingMs + 999) / 1000
        return when {
            seconds >= 120 -> "Too many attempts. Try again in ${seconds / 60} minutes."
            seconds > 60 -> "Too many attempts. Try again in a minute."
            else -> "Too many attempts. Try again in $seconds seconds."
        }
    }
}
