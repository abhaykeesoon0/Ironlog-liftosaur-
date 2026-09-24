package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.WorkoutSetEntity
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/**
 * Double progression: a routine prescribes a weight and a rep range, and the
 * weight moves only when every prescribed set reaches the top of the range.
 *
 * It is the oldest programme there is, and the one a beginner can run without
 * a spreadsheet. Two rules, both judged on the working sets lifted at the
 * prescribed weight (a session done heavier or lighter than the plan says
 * nothing about the plan):
 *
 *  - Every prescribed set at or above the top of the range: the target goes
 *    up by one step.
 *  - Every set under the bottom of the range: the target comes back by a
 *    tenth, rounded down to a whole step, so the lifter returns to a weight
 *    they can build from rather than grinding at one they cannot.
 *
 * Anything in between holds. A target with no weight, or a step of zero, is
 * a routine without progression, and is left alone.
 */
object Progression {

    data class Target(
        val weightKg: Double?,
        val stepKg: Double,
        val sets: Int,
        val repsLow: Int,
        val repsHigh: Int
    )

    sealed interface Verdict {
        val toKg: Double

        data class Advance(override val toKg: Double) : Verdict
        data class Deload(override val toKg: Double) : Verdict
    }

    const val DELOAD_FRACTION = 0.9
    private const val EPS = 1e-6

    /** The next target weight, or null when the routine holds where it is. */
    fun next(target: Target, sets: List<WorkoutSetEntity>): Verdict? {
        val weight = target.weightKg ?: return null
        if (target.stepKg <= 0.0 || target.sets <= 0) return null

        val atTarget = sets
            .filter { it.isCompleted && it.type.countsTowardVolume && it.reps != null && it.weightKg != null }
            .filter { it.weightKg!! >= weight - EPS }
            .sortedBy { it.setNumber }
        if (atTarget.isEmpty()) return null

        val prescribed = atTarget.take(target.sets)
        if (prescribed.size >= target.sets && prescribed.all { it.reps!! >= target.repsHigh }) {
            return Verdict.Advance(roundKg(weight + target.stepKg))
        }
        if (atTarget.all { it.reps!! < target.repsLow }) {
            val stepped = floor(weight * DELOAD_FRACTION / target.stepKg) * target.stepKg
            val to = roundKg(max(stepped, target.stepKg))
            if (to < weight - EPS) return Verdict.Deload(to)
        }
        return null
    }

    /** Kilograms to three decimals, so lb-stepped targets do not accrue float dust. */
    fun roundKg(kg: Double): Double = round(kg * 1000.0) / 1000.0
}
