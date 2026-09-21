package com.ironlog.app.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * Quests: small targets that pay coins, which are spent in the store.
 *
 * Progress is measured from the training log, never from a counter the app
 * increments, so a quest cannot be advanced by anything other than training
 * actually recorded. Claiming is the only thing written down, and it is written
 * once per quest per period.
 *
 * Pure functions with the clock passed in, so a period boundary can be tested
 * rather than waited for.
 */
object Quests {

    enum class Period { DAILY, WEEKLY }

    data class Quest(
        val id: String,
        val title: String,
        val detail: String,
        val period: Period,
        /** Progress is counted towards this, in whatever unit the quest measures. */
        val target: Double,
        val coins: Int
    )

    /**
     * What a quest is worth is roughly proportional to how much training it
     * asks for, so nothing in the list is obviously the cheap one to farm.
     */
    val ALL = listOf(
        Quest(
            id = "daily_session",
            title = "Train today",
            detail = "Finish one workout",
            period = Period.DAILY,
            target = 1.0,
            coins = 20
        ),
        Quest(
            id = "daily_volume",
            title = "Move some weight",
            detail = "5,000 kg of working volume today",
            period = Period.DAILY,
            target = 5_000.0,
            coins = 25
        ),
        Quest(
            id = "weekly_three",
            title = "Three sessions",
            detail = "Train three times this week",
            period = Period.WEEKLY,
            target = 3.0,
            coins = 60
        ),
        Quest(
            id = "weekly_records",
            title = "Break records",
            detail = "Set three personal records this week",
            period = Period.WEEKLY,
            target = 3.0,
            coins = 50
        ),
        Quest(
            id = "weekly_muscles",
            title = "Spread the load",
            detail = "Work four different muscles this week",
            period = Period.WEEKLY,
            target = 4.0,
            coins = 40
        ),
        Quest(
            id = "weekly_long_session",
            title = "Put the time in",
            detail = "Finish a session of 45 minutes or more",
            period = Period.WEEKLY,
            target = 45.0 * 60,
            coins = 35
        )
    )

    fun byId(id: String): Quest? = ALL.firstOrNull { it.id == id }

    /** Everything the quest list is measured against, read from the log. */
    data class Inputs(
        val workoutsToday: Int = 0,
        val volumeTodayKg: Double = 0.0,
        val workoutsThisWeek: Int = 0,
        val recordsThisWeek: Int = 0,
        val musclesThisWeek: Int = 0,
        val longestSessionSecondsThisWeek: Int = 0
    )

    data class Progress(
        val quest: Quest,
        val current: Double,
        val isClaimed: Boolean
    ) {
        val fraction: Float get() = (current / quest.target).coerceIn(0.0, 1.0).toFloat()
        val isComplete: Boolean get() = current >= quest.target
        val isClaimable: Boolean get() = isComplete && !isClaimed
    }

    private fun currentFor(quest: Quest, inputs: Inputs): Double = when (quest.id) {
        "daily_session" -> inputs.workoutsToday.toDouble()
        "daily_volume" -> inputs.volumeTodayKg
        "weekly_three" -> inputs.workoutsThisWeek.toDouble()
        "weekly_records" -> inputs.recordsThisWeek.toDouble()
        "weekly_muscles" -> inputs.musclesThisWeek.toDouble()
        "weekly_long_session" -> inputs.longestSessionSecondsThisWeek.toDouble()
        else -> 0.0
    }

    /**
     * The quest list, claimable ones first so the reward is the first thing
     * seen, then the ones still in progress, and claimed ones last.
     */
    fun progress(inputs: Inputs, claimedQuestIds: Set<String>): List<Progress> =
        ALL.map { quest -> Progress(quest, currentFor(quest, inputs), quest.id in claimedQuestIds) }
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
     */
    fun periodKey(period: Period, at: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return when (period) {
            Period.DAILY -> date.toString()
            Period.WEEKLY -> weekKey(date)
        }
    }

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
