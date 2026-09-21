package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.PlateSet
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.data.entity.WorkoutSetEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The maths behind the progress screens. Pure functions, no Android dependencies,
 * which is what makes them straightforward to unit test.
 */
object TrainingMath {

    /**
     * Estimated one-rep max using the Epley formula: 1RM = w x (1 + reps/30).
     *
     * Epley is used rather than Brzycki because Brzycki's denominator collapses
     * as reps approach 37, producing absurd numbers on high-rep sets. Epley stays
     * monotonic across the whole range.
     *
     * Accuracy drops sharply past about 12 reps -- the formula was fitted to
     * low-rep strength work -- so estimates above [MAX_RELIABLE_REPS] are flagged
     * in the UI rather than presented as fact.
     */
    fun estimatedOneRepMax(weightKg: Double, reps: Int): Double = when {
        weightKg <= 0 || reps <= 0 -> 0.0
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }

    const val MAX_RELIABLE_REPS = 12

    fun isReliableEstimate(reps: Int): Boolean = reps in 1..MAX_RELIABLE_REPS

    /** Working volume for a single set. Warm-ups contribute nothing. */
    fun setVolume(set: WorkoutSetEntity): Double {
        if (!set.isCompleted || !set.type.countsTowardVolume) return 0.0
        val weight = set.weightKg ?: return 0.0
        val reps = set.reps ?: return 0.0
        return weight * reps
    }

    fun totalVolume(sets: List<WorkoutSetEntity>): Double = sets.sumOf { setVolume(it) }

    fun totalReps(sets: List<WorkoutSetEntity>): Int =
        sets.filter { it.isCompleted && it.type.countsTowardVolume }.sumOf { it.reps ?: 0 }

    fun workingSetCount(sets: List<WorkoutSetEntity>): Int =
        sets.count { it.isCompleted && it.type.countsTowardVolume }

    /**
     * Suggests the load for the next session on an exercise.
     *
     * The rule is double progression: hit the top of the target rep range on
     * every working set, and the weight goes up; fall below the bottom of the
     * range, and it comes down. Anything in between holds the weight and the user
     * chases another rep. The increment is the smallest jump the equipment allows,
     * because a 2.5 kg jump on a 40 kg lift is a 6% increase and rarely sticks.
     */
    fun suggestNextLoad(
        lastSets: List<WorkoutSetEntity>,
        targetRepsLow: Int,
        targetRepsHigh: Int,
        smallestIncrementKg: Double
    ): LoadSuggestion? {
        val working = lastSets.filter { it.isCompleted && it.type.countsTowardVolume }
        if (working.isEmpty()) return null

        val weight = working.mapNotNull { it.weightKg }.maxOrNull() ?: return null
        val repsAtWeight = working.filter { it.weightKg == weight }.mapNotNull { it.reps }
        if (repsAtWeight.isEmpty()) return null

        return when {
            repsAtWeight.all { it >= targetRepsHigh } -> LoadSuggestion(
                weightKg = weight + smallestIncrementKg,
                reason = "You hit $targetRepsHigh on every set last time"
            )
            repsAtWeight.any { it < targetRepsLow } -> LoadSuggestion(
                weightKg = (weight - smallestIncrementKg).coerceAtLeast(0.0),
                reason = "You dropped below $targetRepsLow last time"
            )
            else -> LoadSuggestion(
                weightKg = weight,
                reason = "Same weight — aim for one more rep"
            )
        }
    }

    /**
     * Works out what to put on each side of the bar.
     *
     * Greedy from the heaviest plate down, which is optimal here because gym
     * plate denominations are near-multiples of each other. Returns the leftover
     * so the UI can say "closest is 82.5 kg" instead of silently lying about the
     * load.
     */
    fun platesPerSide(
        targetTotalKg: Double,
        barWeightKg: Double,
        available: List<Double> = DEFAULT_PLATES_KG
    ): PlateLoading {
        val perSide = (targetTotalKg - barWeightKg) / 2.0
        if (perSide <= 0) {
            return PlateLoading(emptyList(), achievableTotalKg = barWeightKg, remainderKg = 0.0)
        }

        val plates = mutableListOf<Double>()
        var remaining = perSide
        available.sortedDescending().forEach { plate ->
            while (remaining >= plate - 1e-9) {
                plates += plate
                remaining -= plate
            }
        }

        val loadedPerSide = plates.sum()
        return PlateLoading(
            plates = plates,
            achievableTotalKg = barWeightKg + loadedPerSide * 2,
            remainderKg = if (abs(remaining) < EXACT_TOLERANCE_KG) 0.0 else remaining
        )
    }

    val DEFAULT_PLATES_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    /**
     * How close counts as exact.
     *
     * Ten grams, rather than a floating-point epsilon: pound plates are held as
     * converted kilograms, so a load that is exact on the rack can come out a
     * fraction of a gram short in arithmetic. Reporting that as "closest is"
     * next to a figure identical to the target reads as a bug to the user, and
     * no plate in any gym is finer than this.
     */
    private const val EXACT_TOLERANCE_KG = 0.01

    /**
     * Pound plates, held in kilograms because the loading maths works in
     * kilograms throughout. These are the denominations on a typical American
     * rack; the fractional plates a few gyms own are left out rather than
     * promising a load most lifters cannot actually make.
     */
    val PLATES_LB = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    fun platesFor(set: PlateSet): List<Double> = when (set) {
        PlateSet.KILOGRAMS -> DEFAULT_PLATES_KG
        PlateSet.POUNDS -> PLATES_LB.map { lbToKg(it) }
    }

    /** The bar that comes with the set: 20 kg, or 45 lb on a pound rack. */
    fun defaultBarKg(set: PlateSet): Double = when (set) {
        PlateSet.KILOGRAMS -> 20.0
        PlateSet.POUNDS -> lbToKg(45.0)
    }

    /**
     * A plate written in its own unit. A 45 lb plate reads as "45 lb" even for
     * someone whose totals are shown in kilograms, because that is the number
     * stamped on the plate they are about to pick up.
     */
    fun plateLabel(set: PlateSet, plateKg: Double): String = when (set) {
        PlateSet.KILOGRAMS -> "${plateKg.stripTrailingZeroes()} kg"
        PlateSet.POUNDS -> "${kgToLb(plateKg).roundToNearestHalf().stripTrailingZeroes()} lb"
    }

    private fun Double.roundToNearestHalf(): Double = (this * 2).roundToInt() / 2.0

    private fun Double.stripTrailingZeroes(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString()

    /**
     * Consecutive weeks containing at least one workout, counting back from now.
     *
     * Weeks rather than days: nobody trains daily, and a day-based streak would
     * punish the rest days that training actually requires.
     */
    fun weeklyStreak(workoutTimestamps: List<Long>, zone: ZoneId = ZoneId.systemDefault()): Int {
        if (workoutTimestamps.isEmpty()) return 0

        val trainedWeeks = workoutTimestamps
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .map { it.minusDays((it.dayOfWeek.value - 1).toLong()) }  // Monday of that week
            .toSet()

        val thisMonday = LocalDate.now(zone).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }

        // The current week is not over yet, so an empty current week does not end
        // the streak -- counting starts from last week in that case.
        var cursor = if (trainedWeeks.contains(thisMonday)) thisMonday else thisMonday.minusWeeks(1)
        var streak = 0
        while (trainedWeeks.contains(cursor)) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    /**
     * Totals across a block of finished workouts, for the Home summary.
     *
     * [averageDurationSeconds] divides by the number of sessions actually in the
     * block, not by the window asked for: with four workouts logged, the average
     * is over those four, otherwise a new user's average would read as a quarter
     * of what they really trained.
     */
    data class BlockSummary(
        val workouts: Int = 0,
        val totalVolumeKg: Double = 0.0,
        val totalDurationSeconds: Int = 0,
        val averageDurationSeconds: Int = 0,
        val recordsBroken: Int = 0
    )

    fun summarise(workouts: List<WorkoutEntity>): BlockSummary {
        if (workouts.isEmpty()) return BlockSummary()
        val totalDuration = workouts.sumOf { it.durationSeconds }
        return BlockSummary(
            workouts = workouts.size,
            totalVolumeKg = workouts.sumOf { it.totalVolumeKg },
            totalDurationSeconds = totalDuration,
            averageDurationSeconds = totalDuration / workouts.size,
            recordsBroken = workouts.sumOf { it.prCount }
        )
    }

    /** Rounds to the nearest achievable increment so the UI never shows 47.3 kg. */
    fun roundToIncrement(weightKg: Double, incrementKg: Double = 2.5): Double =
        if (incrementKg <= 0) weightKg else (weightKg / incrementKg).roundToInt() * incrementKg

    fun formatVolume(volumeKg: Double): String = when {
        volumeKg >= 1_000_000 -> "%.1fM kg".format(volumeKg / 1_000_000)
        volumeKg >= 1_000 -> "%.1fk kg".format(volumeKg / 1_000)
        else -> "%.0f kg".format(volumeKg)
    }

    fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun kgToLb(kg: Double): Double = kg * 2.20462
    fun lbToKg(lb: Double): Double = lb / 2.20462
    fun cmToInches(cm: Double): Double = cm / 2.54
    fun inchesToCm(inches: Double): Double = inches * 2.54
}

data class LoadSuggestion(val weightKg: Double, val reason: String)

data class PlateLoading(
    val plates: List<Double>,
    val achievableTotalKg: Double,
    /** Weight per side that could not be made with the available plates. */
    val remainderKg: Double
) {
    val isExact: Boolean get() = remainderKg == 0.0
}
