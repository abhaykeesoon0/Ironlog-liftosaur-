package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.TrainingFocus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Predicts the next session on an exercise from the ones before it.
 *
 * Each past session is reduced to an estimated one-rep max (Epley), and a
 * straight line is fitted through the recent ones against time. Where the
 * line points is the strength expected at the next session; the user's
 * training focus turns that into a weight, a rep count and a set count.
 *
 * It is a forecast, not an instruction, so it is honest about how much it
 * is standing on: one session gives a low-confidence "about the same",
 * three a trend, five with a good fit a confident number. And it is
 * clamped: a real lifter gets a few percent stronger between sessions at
 * most, so the line is never allowed to promise more than that, whatever
 * a lucky day did to the slope. Everything here is pure and testable.
 */
object Predictor {

    data class Sample(val at: Long, val weightKg: Double, val reps: Int)

    enum class Confidence(val label: String) { LOW("Rough guess"), MEDIUM("Likely"), HIGH("Confident") }

    data class Forecast(
        val weightKg: Double,
        val reps: Int,
        val sets: Int,
        val oneRmKg: Double,
        /** Change in estimated 1RM per week along the fitted line, in kg. */
        val trendKgPerWeek: Double,
        val confidence: Confidence,
        val sessions: Int
    )

    /** How many recent sessions the line is fitted through. */
    const val WINDOW = 8

    /** The most the estimate may move between sessions, as a fraction; beyond this is a lucky day, not a trend. */
    const val MAX_STEP = 0.05

    private const val DAY_MS = 86_400_000.0
    private const val DEFAULT_GAP_DAYS = 7.0

    /** The next session's numbers, or null when there is nothing weighted to go on. */
    fun forecast(
        samples: List<Sample>,
        focus: TrainingFocus,
        incrementKg: Double = 2.5,
        now: Long = System.currentTimeMillis()
    ): Forecast? {
        val points = oneRmPoints(samples)
        if (points.isEmpty()) return null

        val last = points.last()
        val nextAt = nextSessionDay(points, now)
        val fit = fitLine(points)
        val projected = when {
            fit == null -> last.second
            else -> (fit.intercept + fit.slope * nextAt).coerceIn(last.second * (1 - MAX_STEP), last.second * (1 + MAX_STEP))
        }
        val oneRm = maxOf(projected, incrementKg)

        val reps = focus.reps
        val weight = TrainingMath.roundToIncrement(oneRm / (1 + reps / 30.0), incrementKg).coerceAtLeast(incrementKg)
        val confidence = when {
            points.size >= 5 && fit != null && fit.rSquared >= 0.5 -> Confidence.HIGH
            points.size >= 3 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        return Forecast(
            weightKg = weight,
            reps = reps,
            sets = focus.sets,
            oneRmKg = round1(oneRm),
            trendKgPerWeek = round1((fit?.slope ?: 0.0) * 7),
            confidence = confidence,
            sessions = points.size
        )
    }

    /**
     * Where the estimated 1RM is headed in [weeksAhead] weeks along the fitted
     * line, held to [MAX_STEP] per week so a two-session hot streak does not
     * become a promise. Null with fewer than two sessions: one point has no
     * direction.
     */
    fun projectOneRm(samples: List<Sample>, weeksAhead: Int, now: Long = System.currentTimeMillis()): Double? {
        val points = oneRmPoints(samples)
        if (points.size < 2) return null
        val fit = fitLine(points) ?: return null
        val last = points.last()
        val perWeek = fit.slope * 7
        val cap = last.second * MAX_STEP
        val step = perWeek.coerceIn(-cap, cap)
        return round1(maxOf(last.second + step * weeksAhead, 0.0))
    }

    /**
     * Weeks until a rising trend reaches [targetKg], or null when it is not
     * rising, is already there, or is too far to say (more than a year).
     */
    fun weeksToReach(series: List<Pair<Long, Double>>, targetKg: Double): Int? {
        if (series.size < 2) return null
        val points = series.sortedBy { it.first }.takeLast(WINDOW).map { (at, kg) -> at / DAY_MS to kg }
        val last = points.last().second
        if (last >= targetKg) return null
        val fit = fitLine(points) ?: return null
        val perWeek = fit.slope * 7
        if (perWeek <= 0.05) return null
        val cap = last * MAX_STEP
        val weeks = ((targetKg - last) / minOf(perWeek, cap)).let { kotlin.math.ceil(it).toInt() }
        return weeks.takeIf { it in 1..52 }
    }

    // ------------------------------------------------------------------ inner

    private data class Fit(val slope: Double, val intercept: Double, val rSquared: Double)

    /** (day, estimated 1RM) for each weighted session, oldest first, last [WINDOW] only. */
    private fun oneRmPoints(samples: List<Sample>): List<Pair<Double, Double>> =
        samples
            .filter { it.weightKg > 0 && it.reps > 0 }
            .sortedBy { it.at }
            .takeLast(WINDOW)
            .map { it.at / DAY_MS to TrainingMath.estimatedOneRepMax(it.weightKg, it.reps) }

    /** The day the next session is expected: the usual gap after the last one, or a week. */
    private fun nextSessionDay(points: List<Pair<Double, Double>>, now: Long): Double {
        val gaps = points.zipWithNext { a, b -> b.first - a.first }.filter { it > 0 }.sorted()
        val gap = if (gaps.isEmpty()) DEFAULT_GAP_DAYS else gaps[gaps.size / 2]
        return maxOf(points.last().first + gap, now / DAY_MS)
    }

    /** Ordinary least squares; null when there are too few points or no spread in time. */
    private fun fitLine(points: List<Pair<Double, Double>>): Fit? {
        if (points.size < 2) return null
        val n = points.size
        val meanX = points.sumOf { it.first } / n
        val meanY = points.sumOf { it.second } / n
        val sxx = points.sumOf { (it.first - meanX) * (it.first - meanX) }
        if (sxx < 1e-9) return null
        val sxy = points.sumOf { (it.first - meanX) * (it.second - meanY) }
        val slope = sxy / sxx
        val intercept = meanY - slope * meanX
        val ssTot = points.sumOf { (it.second - meanY) * (it.second - meanY) }
        val ssRes = points.sumOf { val p = intercept + slope * it.first; (it.second - p) * (it.second - p) }
        val r2 = if (ssTot < 1e-9) 1.0 else 1 - ssRes / ssTot
        return Fit(slope, intercept, r2)
    }

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

    /** A short, honest line about where a forecast came from. */
    fun describe(f: Forecast): String {
        val trend = when {
            abs(f.trendKgPerWeek) < 0.05 -> "holding steady"
            f.trendKgPerWeek > 0 -> "up ${f.trendKgPerWeek} kg a week"
            else -> "down ${abs(f.trendKgPerWeek)} kg a week"
        }
        val basis = if (f.sessions == 1) "your last session" else "your last ${f.sessions} sessions"
        return "${f.confidence.label} · from $basis, $trend"
    }
}
