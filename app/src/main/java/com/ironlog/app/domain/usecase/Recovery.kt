package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.MuscleGroup
import kotlin.math.roundToInt

/**
 * How recovered each muscle is, from the training log alone.
 *
 * This is a training aid, not a physiological claim. It says how long it has
 * been since a muscle was worked and how hard that session was, which is what
 * a lifter actually uses when deciding what to train today.
 *
 * Pure functions with the clock passed in, so the curve can be tested at any
 * point in time rather than only at "now".
 */
object Recovery {

    /** One muscle's working volume inside one session. */
    data class MuscleSession(val muscle: MuscleGroup, val trainedAt: Long, val volumeKg: Double)

    data class MuscleState(
        val muscle: MuscleGroup,
        /** 0 at the moment training finished, 1 when the window has passed. */
        val fraction: Float,
        val windowHours: Int,
        val hoursRemaining: Int,
        val lastTrainedAt: Long
    ) {
        val isFresh: Boolean get() = fraction >= 1f
    }

    /**
     * The floor: nothing is treated as fully recovered sooner than this, even
     * after a token session.
     */
    const val MIN_WINDOW_HOURS = 36

    /** The ceiling, for a session far heavier than that muscle usually gets. */
    const val MAX_WINDOW_HOURS = 72

    private const val MILLIS_PER_HOUR = 3_600_000.0

    /**
     * Cardio is a mode of training rather than a muscle, so it is left out of
     * the readout instead of sitting there permanently fresh.
     */
    private val EXCLUDED = setOf(MuscleGroup.CARDIO)

    /**
     * Recovery for every muscle in [sessions], least recovered first.
     *
     * The window for a muscle is set by its most recent session, scaled by how
     * that session's volume compares with what that muscle usually gets. A
     * normal day lands near two days; a session twice the usual volume takes
     * the full three. The comparison is against the muscle's own average, so a
     * heavy day for someone's calves is judged on their calf training, not
     * against their squats.
     */
    fun states(sessions: List<MuscleSession>, now: Long): List<MuscleState> =
        sessions
            .filterNot { it.muscle in EXCLUDED }
            .groupBy { it.muscle }
            .mapNotNull { (muscle, forMuscle) ->
                val latest = forMuscle.maxByOrNull { it.trainedAt } ?: return@mapNotNull null
                val average = forMuscle.map { it.volumeKg }.average()
                val window = windowHours(latest.volumeKg, average)
                val elapsedHours = (now - latest.trainedAt) / MILLIS_PER_HOUR
                val fraction = (elapsedHours / window).coerceIn(0.0, 1.0).toFloat()
                MuscleState(
                    muscle = muscle,
                    fraction = fraction,
                    windowHours = window,
                    hoursRemaining = (window - elapsedHours).coerceAtLeast(0.0).roundToInt(),
                    lastTrainedAt = latest.trainedAt
                )
            }
            .sortedWith(compareBy({ it.fraction }, { it.muscle.label }))

    /**
     * Maps a session's volume to a recovery window.
     *
     * Half the usual volume or less sits at the 36-hour floor, the usual volume
     * lands at 48 hours, and twice the usual reaches the 72-hour ceiling. A
     * muscle with no history to compare against is treated as a normal day
     * rather than assumed easy.
     */
    fun windowHours(sessionVolumeKg: Double, averageVolumeKg: Double): Int {
        if (averageVolumeKg <= 0.0) return NORMAL_WINDOW_HOURS
        val ratio = sessionVolumeKg / averageVolumeKg
        val hours = MIN_WINDOW_HOURS + (ratio - 0.5) * HOURS_PER_RATIO
        return hours.coerceIn(MIN_WINDOW_HOURS.toDouble(), MAX_WINDOW_HOURS.toDouble()).roundToInt()
    }

    private const val NORMAL_WINDOW_HOURS = 48

    /** Slope that puts the usual volume at 48 hours and twice it at 72. */
    private const val HOURS_PER_RATIO = 24.0
}
