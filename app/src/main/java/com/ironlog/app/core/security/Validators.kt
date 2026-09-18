package com.ironlog.app.core.security

/**
 * Every value that reaches the database passes through here first.
 *
 * Two separate jobs, deliberately not conflated:
 *
 *  - **Validation** rejects input that is wrong, and returns a message the user
 *    can act on. It runs on the field as they type and again on submit.
 *  - **Sanitisation** normalises input that is acceptable but messy -- trimming,
 *    collapsing whitespace, stripping control characters that would corrupt a
 *    CSV export or let someone inject newlines into a text field.
 *
 * Room's DAOs use bound parameters throughout, so SQL injection is not reachable
 * through string concatenation; sanitisation here is about data integrity and
 * about what gets rendered back into the UI and into exported files.
 */
object Validators {

    // Deliberately conservative: matches the addresses people actually type,
    // rejects the pathological ones the RFC technically permits.
    private val EMAIL = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val CONTROL_CHARS = Regex("[\\p{Cntrl}&&[^\n\t]]")
    private val MULTI_SPACE = Regex("[ \\t]{2,}")

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_NAME_LENGTH = 60
    const val MAX_NOTE_LENGTH = 1000
    const val MAX_WEIGHT_KG = 1000.0
    const val MAX_REPS = 500

    // ---------------------------------------------------------------- sanitise

    /** Strips control characters, collapses runs of spaces, trims and caps length. */
    fun sanitiseText(raw: String, maxLength: Int = MAX_NOTE_LENGTH): String =
        raw.replace(CONTROL_CHARS, "")
            .replace(MULTI_SPACE, " ")
            .trim()
            .take(maxLength)

    /** Single-line fields additionally lose newlines. */
    fun sanitiseSingleLine(raw: String, maxLength: Int = MAX_NAME_LENGTH): String =
        sanitiseText(raw.replace('\n', ' ').replace('\t', ' '), maxLength)

    fun sanitiseEmail(raw: String): String = sanitiseSingleLine(raw, 254).lowercase()

    /**
     * Neutralises the leading characters spreadsheet software treats as the start
     * of a formula, so an exported note cannot execute when the CSV is opened.
     */
    fun sanitiseForCsv(value: String): String {
        val cleaned = sanitiseText(value).replace("\"", "\"\"")
        val guarded = if (cleaned.firstOrNull() in setOf('=', '+', '-', '@', '\t', '\r')) {
            "'$cleaned"
        } else {
            cleaned
        }
        return "\"$guarded\""
    }

    // ---------------------------------------------------------------- validate

    fun validateEmail(value: String): String? = when {
        value.isBlank() -> "Enter your email address"
        value.length > 254 -> "That email address is too long"
        !EMAIL.matches(value) -> "That doesn't look like a valid email address"
        else -> null
    }

    fun validatePassword(value: CharArray): String? = when {
        value.isEmpty() -> "Enter a password"
        value.size < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters"
        value.none { it.isDigit() } -> "Include at least one number"
        value.none { it.isLetter() } -> "Include at least one letter"
        else -> null
    }

    fun validatePasswordMatch(a: CharArray, b: CharArray): String? =
        if (!a.contentEquals(b)) "Passwords don't match" else null

    fun validateDisplayName(value: String): String? = when {
        value.isBlank() -> "Enter your name"
        value.length < 2 -> "That name is too short"
        value.length > MAX_NAME_LENGTH -> "Keep it under $MAX_NAME_LENGTH characters"
        else -> null
    }

    fun validateExerciseName(value: String): String? = when {
        value.isBlank() -> "Give the exercise a name"
        value.length < 2 -> "That name is too short"
        value.length > MAX_NAME_LENGTH -> "Keep it under $MAX_NAME_LENGTH characters"
        else -> null
    }

    fun validateRoutineName(value: String): String? = when {
        value.isBlank() -> "Give the routine a name"
        value.length > MAX_NAME_LENGTH -> "Keep it under $MAX_NAME_LENGTH characters"
        else -> null
    }

    /** Returns the parsed weight, or null if the text is not a usable number. */
    fun parseWeight(raw: String): Double? {
        val value = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (value.isFinite() && value >= 0.0 && value <= MAX_WEIGHT_KG) value else null
    }

    fun parseReps(raw: String): Int? {
        val value = raw.trim().toIntOrNull() ?: return null
        return if (value in 0..MAX_REPS) value else null
    }

    fun parseRpe(raw: String): Double? {
        val value = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        // RPE is a 6-10 scale recorded in half points.
        return if (value in 6.0..10.0 && (value * 2) % 1.0 == 0.0) value else null
    }

    fun parseMeasurement(raw: String, max: Double = 400.0): Double? {
        val value = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (value.isFinite() && value > 0.0 && value <= max) value else null
    }
}
