package com.ironlog.app.domain

import com.ironlog.app.domain.usecase.Quests
import com.ironlog.app.domain.usecase.StoreCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class QuestsTest {

    private val zone = ZoneId.of("UTC")

    private fun millis(date: LocalDateTime) = date.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `progress is measured against the quest's own target`() {
        val inputs = Quests.Inputs(workoutsToday = 1, workoutsThisWeek = 2)
        val list = Quests.progress(inputs, claimedQuestIds = emptySet())

        val daily = list.single { it.quest.id == "daily_session" }
        assertTrue(daily.isComplete)
        assertTrue(daily.isClaimable)

        val weekly = list.single { it.quest.id == "weekly_three" }
        assertEquals(2.0 / 3, weekly.fraction.toDouble(), 1e-6)
        assertFalse(weekly.isComplete)
    }

    @Test
    fun `a claimed quest is complete but no longer claimable`() {
        val list = Quests.progress(Quests.Inputs(workoutsToday = 1), setOf("daily_session"))
        val daily = list.single { it.quest.id == "daily_session" }
        assertTrue(daily.isComplete)
        assertTrue(daily.isClaimed)
        assertFalse(daily.isClaimable)
    }

    @Test
    fun `claimable quests are listed first and claimed ones last`() {
        val inputs = Quests.Inputs(workoutsToday = 1, workoutsThisWeek = 3, musclesThisWeek = 2)
        val list = Quests.progress(inputs, claimedQuestIds = setOf("weekly_three"))

        assertEquals("daily_session", list.first().quest.id)
        assertEquals("weekly_three", list.last().quest.id)
    }

    @Test
    fun `progress cannot exceed the target however much was trained`() {
        val list = Quests.progress(Quests.Inputs(volumeTodayKg = 50_000.0), emptySet())
        assertEquals(1f, list.single { it.quest.id == "daily_volume" }.fraction, 1e-6f)
    }

    @Test
    fun `a daily key changes with the day and a weekly key does not`() {
        val monday = millis(LocalDateTime.of(2026, 9, 21, 10, 0))
        val tuesday = millis(LocalDateTime.of(2026, 9, 22, 10, 0))

        assertEquals("2026-09-21", Quests.periodKey(Quests.Period.DAILY, monday, zone))
        assertEquals("2026-09-22", Quests.periodKey(Quests.Period.DAILY, tuesday, zone))
        assertEquals(
            Quests.periodKey(Quests.Period.WEEKLY, monday, zone),
            Quests.periodKey(Quests.Period.WEEKLY, tuesday, zone)
        )
    }

    @Test
    fun `a week key rolls over on Monday, not mid-week`() {
        val sunday = millis(LocalDateTime.of(2026, 9, 20, 23, 0))
        val monday = millis(LocalDateTime.of(2026, 9, 21, 1, 0))
        assertTrue(
            Quests.periodKey(Quests.Period.WEEKLY, sunday, zone) !=
                Quests.periodKey(Quests.Period.WEEKLY, monday, zone)
        )
    }

    @Test
    fun `new year week numbering follows ISO rather than restarting mid-week`() {
        // 31 December 2026 is a Thursday, which ISO puts in week 53 of 2026.
        val newYearsEve = millis(LocalDateTime.of(2026, 12, 31, 12, 0))
        val newYearsDay = millis(LocalDateTime.of(2027, 1, 1, 12, 0))
        assertEquals(
            Quests.periodKey(Quests.Period.WEEKLY, newYearsEve, zone),
            Quests.periodKey(Quests.Period.WEEKLY, newYearsDay, zone)
        )
    }

    @Test
    fun `every quest is reachable by the progress function`() {
        // A quest whose id is not handled would sit at zero forever, so this
        // pins the list and the lookup together.
        val generous = Quests.Inputs(
            workoutsToday = 99,
            volumeTodayKg = 999_999.0,
            workoutsThisWeek = 99,
            recordsThisWeek = 99,
            musclesThisWeek = 99,
            longestSessionSecondsThisWeek = 99_999
        )
        assertTrue(Quests.progress(generous, emptySet()).all { it.isComplete })
    }

    @Test
    fun `quest ids are unique and every quest pays something`() {
        assertEquals(Quests.ALL.size, Quests.ALL.map { it.id }.distinct().size)
        assertTrue(Quests.ALL.all { it.coins > 0 })
        assertNotNull(Quests.byId("daily_session"))
    }

    @Test
    fun `store ids are unique and only the default background is free`() {
        assertEquals(StoreCatalogue.ITEMS.size, StoreCatalogue.ITEMS.map { it.id }.distinct().size)
        val free = StoreCatalogue.ITEMS.filter { it.price == 0 }.map { it.id }
        assertEquals(listOf(StoreCatalogue.DEFAULT_BACKGROUND), free)
    }

    @Test
    fun `the current keys cover both periods`() {
        val keys = Quests.currentKeys(millis(LocalDate.of(2026, 9, 21).atStartOfDay()), zone)
        assertEquals(setOf(Quests.Period.DAILY, Quests.Period.WEEKLY), keys.keys)
    }
}
