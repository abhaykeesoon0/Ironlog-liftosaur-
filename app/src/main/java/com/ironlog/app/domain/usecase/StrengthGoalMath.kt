package com.ironlog.app.domain.usecase

import com.ironlog.app.data.dao.HistoricSet
import com.ironlog.app.data.entity.GoalMetric

/**
 * Where a strength goal stands, worked out from the sets behind it.
 *
 * Nothing here is stored. A goal keeps only its target, so progress is
 * recomputed from the log every time it is shown and can never drift away from
 * the training that produced it.
 */
object StrengthGoalMath {

    /** One session's best value on the goal's metric. */
    data class SessionValue(val startedAt: Long, val valueKg: Double)

    /**
     * The best value a single set is worth on [metric].
     *
     * Warm-ups are excluded, as everywhere else: a warm-up single is not a
     * strength result, and counting it would let a goal advance on a set the
     * lifter did not consider work.
     */
    fun setValue(set: HistoricSet, metric: GoalMetric): Double {
        val s = set.set
        if (!s.isCompleted || !s.type.countsTowardVolume) return 0.0
        val weight = s.weightKg ?: return 0.0
        val reps = s.reps ?: return 0.0
        if (weight <= 0.0 || reps <= 0) return 0.0
        return when (metric) {
            GoalMetric.TOP_WEIGHT -> weight
            // An estimate is only offered where the formula holds; a set of 20
            // would otherwise produce a one-rep max nobody could lift.
            GoalMetric.ESTIMATED_1RM ->
                if (TrainingMath.isReliableEstimate(reps)) TrainingMath.estimatedOneRepMax(weight, reps) else 0.0
        }
    }

    /**
     * One point per session, oldest first, for the goal's chart.
     *
     * Sessions where nothing counted -- all warm-ups, or all sets too high-rep
     * to estimate from -- are dropped rather than plotted as zero, which would
     * draw a cliff into the chart that never happened.
     */
    fun series(sets: List<HistoricSet>, metric: GoalMetric): List<SessionValue> =
        sets
            .groupBy { it.workoutId }
            .mapNotNull { (_, forWorkout) ->
                val best = forWorkout.maxOfOrNull { setValue(it, metric) } ?: 0.0
                if (best <= 0.0) null
                else SessionValue(forWorkout.first().startedAt, best)
            }
            .sortedBy { it.startedAt }

    /** The best the lifter has managed so far, which is what the goal measures. */
    fun best(sets: List<HistoricSet>, metric: GoalMetric): Double =
        sets.maxOfOrNull { setValue(it, metric) } ?: 0.0

    /**
     * How far along the goal is, from 0 to 1.
     *
     * Measured from zero rather than from where the lifter started, because a
     * bar that begins part-filled invites the question of what the rest of it
     * means, and the honest answer is the fraction of the target lifted.
     */
    fun progress(currentKg: Double, targetKg: Double): Float {
        if (targetKg <= 0.0) return 0f
        return (currentKg / targetKg).coerceIn(0.0, 1.0).toFloat()
    }

    fun isAchieved(currentKg: Double, targetKg: Double): Boolean =
        targetKg > 0.0 && currentKg >= targetKg

    /** What is left to lift. Zero once the target is met. */
    fun remainingKg(currentKg: Double, targetKg: Double): Double =
        (targetKg - currentKg).coerceAtLeast(0.0)
}
