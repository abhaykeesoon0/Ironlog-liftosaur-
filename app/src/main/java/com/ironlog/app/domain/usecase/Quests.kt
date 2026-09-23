package com.ironlog.app.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlin.math.roundToInt

/**
 * Quests: targets that pay coins, which are spent in the store.
 *
 * Progress is measured from the training log, never from a counter the app
 * increments, so a quest cannot be advanced by anything other than training
 * actually recorded. There is deliberately no "open the app" quest for the
 * same reason. Claiming is the only thing written down, and it is written once
 * per quest per period.
 *
 * Four horizons. Daily and weekly quests are the routine; monthly ones reward
 * a block of training held together; milestones are claimed once in a
 * lifetime and are the trophy case. A weekly streak adds a bonus to every
 * payout, so the reward for coming back is not only the quest itself.
 *
 * Pure functions with the clock passed in, so a period boundary can be tested
 * rather than waited for.
 */
object Quests {

    enum class Period(val label: String, val resets: String) {
        DAILY("Today", "Resets at midnight"),
        WEEKLY("This week", "Resets on Monday"),
        MONTHLY("This month", "Resets on the 1st"),
        ONCE("Milestones", "Claimed once, kept for good")
    }

    /** What the target is counted in, so the screen can print it properly. */
    enum class Unit { COUNT, KILOGRAMS, MINUTES, STEPS }

    data class Quest(
        val id: String,
        val title: String,
        val detail: String,
        val period: Period,
        /** Progress is counted towards this, in [unit]. */
        val target: Double,
        val coins: Int,
        val unit: Unit = Unit.COUNT
    )

    /**
     * What a quest is worth is roughly proportional to how much training it
     * asks for, so nothing in the list is obviously the cheap one to farm. A
     * milestone pays more because it is paid once.
     */
    val ALL = listOf(
        // ------------------------------------------------------------- daily
        Quest("daily_session", "Train today", "Finish one workout", Period.DAILY, 1.0, 20),
        Quest("daily_volume", "Move some weight", "5,000 kg of working volume today", Period.DAILY, 5_000.0, 25, Unit.KILOGRAMS),
        Quest("daily_full_body", "Cover the ground", "Work three different muscles today", Period.DAILY, 3.0, 25),
        Quest("daily_warmup", "Warm up properly", "Log two warm-up sets before the work", Period.DAILY, 2.0, 15),
        Quest("daily_bodyweight", "Step on the scale", "Log your bodyweight today", Period.DAILY, 1.0, 10),
        Quest("daily_steps", "Get moving", "8,000 steps today", Period.DAILY, 8_000.0, 20, Unit.STEPS),

        // ------------------------------------------------------------ weekly
        Quest("weekly_three", "Three sessions", "Train three times this week", Period.WEEKLY, 3.0, 60),
        Quest("weekly_five", "Five sessions", "Train five times this week", Period.WEEKLY, 5.0, 100),
        Quest("weekly_records", "Break records", "Set three personal records this week", Period.WEEKLY, 3.0, 50),
        Quest("weekly_muscles", "Spread the load", "Work four different muscles this week", Period.WEEKLY, 4.0, 40),
        Quest("weekly_long_session", "Put the time in", "Finish a session of 45 minutes or more", Period.WEEKLY, 45.0 * 60, 35, Unit.MINUTES),
        Quest("weekly_volume", "Twenty tonnes", "20,000 kg of working volume this week", Period.WEEKLY, 20_000.0, 70, Unit.KILOGRAMS),
        Quest("weekly_cardio", "Get outside", "Record one outdoor cardio session", Period.WEEKLY, 1.0, 40),
        Quest("weekly_steps", "Fifty thousand", "50,000 steps this week", Period.WEEKLY, 50_000.0, 60, Unit.STEPS),
        Quest("weekly_new_exercise", "Try something new", "Log an exercise you have never done", Period.WEEKLY, 1.0, 30),

        // ----------------------------------------------------------- monthly
        Quest("monthly_twelve", "A solid month", "Twelve sessions this month", Period.MONTHLY, 12.0, 200),
        Quest("monthly_records", "Record month", "Eight personal records this month", Period.MONTHLY, 8.0, 150),
        Quest("monthly_volume", "Eighty tonnes", "80,000 kg of working volume this month", Period.MONTHLY, 80_000.0, 180, Unit.KILOGRAMS),

        // -------------------------------------------------------- milestones
        Quest("once_first_workout", "First one done", "Finish your first workout", Period.ONCE, 1.0, 50),
        Quest("once_ten_workouts", "Ten in the book", "Finish ten workouts", Period.ONCE, 10.0, 150),
        Quest("once_fifty_workouts", "Fifty sessions", "Finish fifty workouts", Period.ONCE, 50.0, 500),
        Quest("once_hundred_workouts", "The hundred club", "Finish a hundred workouts", Period.ONCE, 100.0, 1_000),
        Quest("once_first_record", "First record", "Set your first personal record", Period.ONCE, 1.0, 40),
        Quest("once_twenty_records", "Record collector", "Set twenty personal records", Period.ONCE, 20.0, 300),
        Quest("once_hundred_tonnes", "A hundred tonnes", "100,000 kg lifted, all time", Period.ONCE, 100_000.0, 400, Unit.KILOGRAMS),
        Quest("once_twenty_exercises", "Well rounded", "Log twenty different exercises", Period.ONCE, 20.0, 120),
        Quest("once_four_week_streak", "A month of Mondays", "Train every week for four weeks running", Period.ONCE, 4.0, 250)
    )

    fun byId(id: String): Quest? = ALL.firstOrNull { it.id == id }

    /** Everything the quest list is measured against, read from the log. */
    data class Inputs(
        val workoutsToday: Int = 0,
        val volumeTodayKg: Double = 0.0,
        val musclesToday: Int = 0,
        val warmupSetsToday: Int = 0,
        val bodyweightLoggedToday: Boolean = false,
        val stepsToday: Int = 0,
        val workoutsThisWeek: Int = 0,
        val recordsThisWeek: Int = 0,
        val musclesThisWeek: Int = 0,
        val longestSessionSecondsThisWeek: Int = 0,
        val volumeThisWeekKg: Double = 0.0,
        val cardioSessionsThisWeek: Int = 0,
        val stepsThisWeek: Int = 0,
        val newExercisesThisWeek: Int = 0,
        val workoutsThisMonth: Int = 0,
        val recordsThisMonth: Int = 0,
        val volumeThisMonthKg: Double = 0.0,
        val lifetimeWorkouts: Int = 0,
        val lifetimeRecords: Int = 0,
        val lifetimeVolumeKg: Double = 0.0,
        val distinctExercisesEver: Int = 0,
        val streakWeeks: Int = 0
    )

    data class Progress(
        val quest: Quest,
        val current: Double,
        val isClaimed: Boolean,
        /** What claiming pays right now, streak bonus included. */
        val payout: Int
    ) {
        val fraction: Float get() = (current / quest.target).coerceIn(0.0, 1.0).toFloat()
        val isComplete: Boolean get() = current >= quest.target
        val isClaimable: Boolean get() = isComplete && !isClaimed
        val bonus: Int get() = payout - quest.coins
    }

    /** Extra paid per week of streak beyond the first, and the most it can add. */
    const val STREAK_BONUS_PER_WEEK = 0.10
    const val STREAK_BONUS_CAP = 0.50

    /**
     * The streak bonus: ten percent on every payout per week of the streak
     * beyond the first, to a ceiling of half again.
     *
     * The bonus is on the payout rather than a quest of its own, so a lifter
     * on a long streak is paid more for everything they do, which is the
     * feeling a streak is meant to give. Capped so the ceiling is reached in a
     * month or so and the reward for the sixth week is not a treadmill.
     */
    fun streakBonus(streakWeeks: Int): Double =
        ((streakWeeks - 1).coerceAtLeast(0) * STREAK_BONUS_PER_WEEK).coerceAtMost(STREAK_BONUS_CAP)

    fun payout(quest: Quest, streakWeeks: Int): Int =
        (quest.coins * (1.0 + streakBonus(streakWeeks))).roundToInt()

    private fun currentFor(quest: Quest, inputs: Inputs): Double = when (quest.id) {
        "daily_session" -> inputs.workoutsToday.toDouble()
        "daily_volume" -> inputs.volumeTodayKg
        "daily_full_body" -> inputs.musclesToday.toDouble()
        "daily_warmup" -> inputs.warmupSetsToday.toDouble()
        "daily_bodyweight" -> if (inputs.bodyweightLoggedToday) 1.0 else 0.0
        "daily_steps" -> inputs.stepsToday.toDouble()
        "weekly_three", "weekly_five" -> inputs.workoutsThisWeek.toDouble()
        "weekly_records" -> inputs.recordsThisWeek.toDouble()
        "weekly_muscles" -> inputs.musclesThisWeek.toDouble()
        "weekly_long_session" -> inputs.longestSessionSecondsThisWeek.toDouble()
        "weekly_volume" -> inputs.volumeThisWeekKg
        "weekly_cardio" -> inputs.cardioSessionsThisWeek.toDouble()
        "weekly_steps" -> inputs.stepsThisWeek.toDouble()
        "weekly_new_exercise" -> inputs.newExercisesThisWeek.toDouble()
        "monthly_twelve" -> inputs.workoutsThisMonth.toDouble()
        "monthly_records" -> inputs.recordsThisMonth.toDouble()
        "monthly_volume" -> inputs.volumeThisMonthKg
        "once_first_workout", "once_ten_workouts", "once_fifty_workouts", "once_hundred_workouts" ->
            inputs.lifetimeWorkouts.toDouble()
        "once_first_record", "once_twenty_records" -> inputs.lifetimeRecords.toDouble()
        "once_hundred_tonnes" -> inputs.lifetimeVolumeKg
        "once_twenty_exercises" -> inputs.distinctExercisesEver.toDouble()
        "once_four_week_streak" -> inputs.streakWeeks.toDouble()
        else -> 0.0
    }

    /**
     * The quest list, claimable ones first so the reward is the first thing
     * seen, then the ones still in progress, and claimed ones last.
     */
    fun progress(inputs: Inputs, claimedQuestIds: Set<String>): List<Progress> =
        ALL.map { quest ->
            Progress(
                quest = quest,
                current = currentFor(quest, inputs),
                isClaimed = quest.id in claimedQuestIds,
                payout = payout(quest, inputs.streakWeeks)
            )
        }
            .sortedWith(
                compareByDescending<Progress> { it.isClaimable }
                    .thenBy { it.isClaimed }
                    .thenByDescending { it.fraction }
            )

    /**
     * The period a claim belongs to.
     *
     * A claim is stored against this key, which is what stops a daily quest
     * being claimed twice on the same day and lets it come back tomorrow. Weeks
     * run Monday to Sunday, matching the streak and the weekly goal elsewhere.
     * A milestone's key never changes, which is what makes it once.
     */
    fun periodKey(period: Period, at: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return when (period) {
            Period.DAILY -> date.toString()
            Period.WEEKLY -> weekKey(date)
            Period.MONTHLY -> "%d-%02d".format(date.year, date.monthValue)
            Period.ONCE -> ONCE_KEY
        }
    }

    private const val ONCE_KEY = "all-time"

    private fun weekKey(date: LocalDate): String {
        // ISO weeks, so the last days of December land in the right week rather
        // than in a week 1 that belongs to the year before.
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        val year = date.get(WeekFields.ISO.weekBasedYear())
        return "%d-W%02d".format(year, week)
    }

    /** The keys currently open for claiming, one per period. */
    fun currentKeys(at: Long, zone: ZoneId = ZoneId.systemDefault()): Map<Period, String> =
        Period.entries.associateWith { periodKey(it, at, zone) }
}
