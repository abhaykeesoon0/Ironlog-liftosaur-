package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.WorkoutEntity
import kotlin.math.roundToInt

/**
 * Progression: experience, levels, milestones and per-session achievements.
 *
 * Everything here is derived from finished workouts that are already in the
 * database, so there is no new table, no migration and nothing to keep in sync.
 * The trade-off is that the figures are only as complete as the history the
 * caller passes in; screens hand over the same list they are already showing.
 *
 * Pure functions, no Android dependencies, so the curve can be unit tested
 * rather than eyeballed on a device.
 */
object Gamification {

    // ------------------------------------------------------------ experience

    /** Flat award for finishing at all, so a light session still counts. */
    private const val XP_PER_WORKOUT = 20

    /** Working sets are the unit of work a lifter actually feels. */
    private const val XP_PER_SET = 10

    /**
     * Volume is divided down because it dwarfs every other term otherwise: a
     * squat session can clear 10,000 kg, which would drown out sets and records.
     */
    private const val KG_PER_XP = 50

    private const val XP_PER_RECORD = 25

    fun workoutXp(workout: WorkoutEntity): Int =
        XP_PER_WORKOUT +
            workout.totalSets * XP_PER_SET +
            (workout.totalVolumeKg / KG_PER_XP).roundToInt() +
            workout.prCount * XP_PER_RECORD

    fun totalXp(workouts: List<WorkoutEntity>): Int = workouts.sumOf { workoutXp(it) }

    /** XP needed to clear [level] and reach the next one. */
    private fun xpToClear(level: Int): Int = 400 + (level - 1) * 200

    /**
     * Where [totalXp] sits on the curve.
     *
     * The cost of a level rises linearly, so early levels arrive quickly and
     * later ones take real work -- the usual shape, and it keeps a beginner
     * seeing movement in their first week.
     */
    fun levelFor(totalXp: Int): LevelProgress {
        var level = 1
        var remaining = totalXp.coerceAtLeast(0)
        while (remaining >= xpToClear(level)) {
            remaining -= xpToClear(level)
            level++
        }
        return LevelProgress(
            level = level,
            title = titleFor(level),
            xpIntoLevel = remaining,
            xpForLevel = xpToClear(level),
            totalXp = totalXp.coerceAtLeast(0)
        )
    }

    private fun titleFor(level: Int): String = when {
        level <= 2 -> "Novice"
        level <= 5 -> "Beginner"
        level <= 9 -> "Intermediate"
        level <= 14 -> "Advanced"
        level <= 19 -> "Elite"
        else -> "Legend"
    }

    data class LevelProgress(
        val level: Int,
        val title: String,
        val xpIntoLevel: Int,
        val xpForLevel: Int,
        val totalXp: Int
    ) {
        val fraction: Float get() = (xpIntoLevel.toFloat() / xpForLevel).coerceIn(0f, 1f)
        val xpToNext: Int get() = (xpForLevel - xpIntoLevel).coerceAtLeast(0)
    }

    // ------------------------------------------------------------ milestones

    data class Milestone(val count: Int, val name: String)

    /**
     * Session-count milestones. The early ones are close together because the
     * habit is most fragile in the first fortnight; they spread out after that.
     */
    val MILESTONES = listOf(
        Milestone(1, "First session"),
        Milestone(5, "Five sessions"),
        Milestone(10, "Ten sessions"),
        Milestone(25, "Twenty-five sessions"),
        Milestone(50, "Fifty sessions"),
        Milestone(100, "Century club"),
        Milestone(200, "Two hundred sessions"),
        Milestone(365, "Three hundred and sixty-five")
    )

    /** The milestone that [workoutNumber] lands on, if it lands on one. */
    fun milestoneAt(workoutNumber: Int): Milestone? = MILESTONES.firstOrNull { it.count == workoutNumber }

    /** The next milestone still ahead of [finishedCount]. */
    fun nextMilestone(finishedCount: Int): Milestone? = MILESTONES.firstOrNull { it.count > finishedCount }

    // ---------------------------------------------------------- achievements

    enum class Tone {
        /** Records. Gold, matching the PR badge used elsewhere. */
        GOLD,

        /** Everything else: quiet, outlined, no colour spent on decoration. */
        NEUTRAL
    }

    data class Achievement(val label: String, val tone: Tone)

    /** A gain smaller than this is noise, not progress worth a chip. */
    private const val MIN_NOTEWORTHY_GAIN = 0.05

    /**
     * What [workout] earned, judged against the sessions that came before it.
     *
     * [earlier] must hold only workouts that finished before this one, oldest
     * first -- a "heaviest yet" chip is meaningless if later sessions are in
     * the comparison. Callers slice their history list rather than filtering by
     * timestamp here, because they already have it in order.
     */
    fun achievementsFor(workout: WorkoutEntity, earlier: List<WorkoutEntity>): List<Achievement> {
        val earned = mutableListOf<Achievement>()

        if (workout.prCount > 0) {
            val label = if (workout.prCount == 1) "1 record" else "${workout.prCount} records"
            earned += Achievement(label, Tone.GOLD)
        }

        milestoneAt(earlier.size + 1)?.let { earned += Achievement(it.name, Tone.GOLD) }

        // "Yet" claims need something to beat, so the first session makes none.
        if (earlier.isNotEmpty()) {
            val heaviestBefore = earlier.maxOf { it.totalVolumeKg }
            if (workout.totalVolumeKg > heaviestBefore && workout.totalVolumeKg > 0) {
                earned += Achievement("Heaviest session yet", Tone.NEUTRAL)
            }

            val longestBefore = earlier.maxOf { it.durationSeconds }
            if (workout.durationSeconds > longestBefore && workout.durationSeconds > 0) {
                earned += Achievement("Longest session yet", Tone.NEUTRAL)
            }

            val previous = earlier.last().totalVolumeKg
            if (previous > 0 && workout.totalVolumeKg > 0) {
                val ratio = workout.totalVolumeKg / previous
                val gain = ratio - 1
                // Past a doubling, a percentage stops being readable -- "+2420%"
                // means nothing at a glance, where "25.2x" lands immediately.
                if (ratio >= 2.0) {
                    earned += Achievement("${trimZero(round(ratio, 1))}× volume", Tone.NEUTRAL)
                } else if (gain >= MIN_NOTEWORTHY_GAIN) {
                    earned += Achievement("+${(gain * 100).roundToInt()}% volume", Tone.NEUTRAL)
                }
            }
        }

        return earned
    }

    private fun round(value: Double, decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        return (value * factor).roundToInt() / factor
    }

    /** "25.0" reads as false precision next to "25". */
    private fun trimZero(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    /** As many chips as fit a card without the card becoming a chip rack. */
    const val MAX_CHIPS_PER_CARD = 3
}
