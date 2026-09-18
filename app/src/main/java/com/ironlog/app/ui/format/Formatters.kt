package com.ironlog.app.ui.format

import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.domain.usecase.TrainingMath
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Presentation-layer unit handling. Everything in the database is kilograms,
 * metres and centimetres; this file is the only place those become pounds, miles
 * and inches, and the only place typed values are turned back into metric.
 */

/** A number with trailing zeros removed: 60.0 -> "60", 62.5 -> "62.5", 61.25 -> "61.25". */
fun Double.trimmed(maxDecimals: Int = 2): String {
    val factor = Math.pow(10.0, maxDecimals.toDouble())
    val rounded = (this * factor).roundToLong() / factor
    return if (abs(rounded - rounded.toLong()) < 1e-9) rounded.toLong().toString()
    else "%.${maxDecimals}f".format(Locale.US, rounded).trimEnd('0').trimEnd('.')
}

/** Kilograms to the user's unit, as a bare number for an input field. */
fun UnitSystem.weightValue(kg: Double?): String {
    kg ?: return ""
    return if (this == UnitSystem.METRIC) kg.trimmed() else TrainingMath.kgToLb(kg).trimmed(1)
}

/** Kilograms to the user's unit with a suffix: "62.5 kg" or "137.8 lb". */
fun UnitSystem.formatWeight(kg: Double?): String =
    if (kg == null) "—" else "${weightValue(kg)} $weightSuffix"

/** Parses a typed weight in the user's unit, returning kilograms, or null if invalid. */
fun UnitSystem.parseWeightToKg(text: String): Double? {
    val value = Validators.parseWeight(text) ?: return null
    return if (this == UnitSystem.METRIC) value else TrainingMath.lbToKg(value)
}

fun UnitSystem.formatVolume(kg: Double): String {
    val value = if (this == UnitSystem.METRIC) kg else TrainingMath.kgToLb(kg)
    return when {
        value >= 1_000_000 -> "%.1fM %s".format(value / 1_000_000, weightSuffix)
        value >= 10_000 -> "%.1fk %s".format(value / 1_000, weightSuffix)
        else -> "%,.0f %s".format(value, weightSuffix)
    }
}

fun UnitSystem.formatDistance(meters: Double): String {
    val value = if (this == UnitSystem.METRIC) meters / 1000.0 else meters / 1609.344
    return "%.2f %s".format(value, distanceSuffix)
}

/** Pace in the user's unit: "5:12 /km" or "8:22 /mi". */
fun UnitSystem.formatPace(secondsPerKm: Int): String {
    if (secondsPerKm <= 0) return "—"
    val seconds = if (this == UnitSystem.METRIC) secondsPerKm else (secondsPerKm * 1.609344).toInt()
    return "%d:%02d /%s".format(seconds / 60, seconds % 60, distanceSuffix)
}

fun UnitSystem.lengthValue(cm: Double?): String {
    cm ?: return ""
    return if (this == UnitSystem.METRIC) cm.trimmed(1) else TrainingMath.cmToInches(cm).trimmed(1)
}

fun UnitSystem.formatLength(cm: Double?): String = if (cm == null) "—" else "${lengthValue(cm)} $lengthSuffix"

fun UnitSystem.parseLengthToCm(text: String): Double? {
    val value = Validators.parseMeasurement(text) ?: return null
    return if (this == UnitSystem.METRIC) value else TrainingMath.inchesToCm(value)
}

/** "0:45", "12:03", "1:02:10". */
fun formatClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun formatDuration(seconds: Int): String = TrainingMath.formatDuration(seconds)

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val fullFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val shortFormat = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** "Today", "Yesterday", "Mon 3 Mar". */
fun formatRelativeDay(epochMillis: Long): String {
    val date = epochMillis.toLocalDate()
    return when (ChronoUnit.DAYS.between(date, LocalDate.now())) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> dayFormat.format(date)
    }
}

fun formatFullDate(epochMillis: Long): String = fullFormat.format(epochMillis.toLocalDate())

fun formatShortDate(epochMillis: Long): String = shortFormat.format(epochMillis.toLocalDate())

fun formatTime(epochMillis: Long): String =
    timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
