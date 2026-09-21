package com.ironlog.app.domain

import com.ironlog.app.data.entity.WorkoutEntity
import com.ironlog.app.domain.usecase.Gamification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamificationTest {

    private fun workout(
        volume: Double = 0.0,
        sets: Int = 0,
        prs: Int = 0,
        duration: Int = 0
    ) = WorkoutEntity(
        id = "w", userId = "u", name = "Session", startedAt = 0L, finishedAt = 1L,
        totalVolumeKg = volume, totalSets = sets, prCount = prs, durationSeconds = duration
    )

    @Test
    fun `workout xp adds the flat award, sets, volume and records`() {
        // 20 flat + 4 sets x 10 + 1580/50 rounded + 2 records x 25
        assertEquals(20 + 40 + 32 + 50, Gamification.workoutXp(workout(1580.0, 4, prs = 2)))
    }

    @Test
    fun `an empty session still earns the flat award`() {
        assertEquals(20, Gamification.workoutXp(workout()))
    }

    @Test
    fun `level one starts empty and the bar fills within the level`() {
        val start = Gamification.levelFor(0)
        assertEquals(1, start.level)
        assertEquals(0f, start.fraction, 1e-6f)
        assertEquals(400, start.xpToNext)

        val part = Gamification.levelFor(200)
        assertEquals(1, part.level)
        assertEquals(0.5f, part.fraction, 1e-6f)
    }

    @Test
    fun `levels cost more as they go up`() {
        // 400 clears level 1, then 600 clears level 2, then 800 clears level 3.
        assertEquals(2, Gamification.levelFor(400).level)
        assertEquals(2, Gamification.levelFor(999).level)
        assertEquals(3, Gamification.levelFor(1000).level)
        assertEquals(4, Gamification.levelFor(1800).level)
    }

    @Test
    fun `negative xp cannot push the level below one`() {
        val floor = Gamification.levelFor(-500)
        assertEquals(1, floor.level)
        assertEquals(0, floor.totalXp)
    }

    @Test
    fun `titles climb with the level`() {
        assertEquals("Novice", Gamification.levelFor(0).title)
        assertEquals("Legend", Gamification.levelFor(1_000_000).title)
    }

    @Test
    fun `milestones land on their own session number only`() {
        assertEquals("First session", Gamification.milestoneAt(1)?.name)
        assertEquals("Ten sessions", Gamification.milestoneAt(10)?.name)
        assertNull(Gamification.milestoneAt(11))
        assertEquals(25, Gamification.nextMilestone(10)?.count)
        assertNull(Gamification.nextMilestone(400))
    }

    @Test
    fun `the first session earns a milestone but claims nothing it cannot beat`() {
        val earned = Gamification.achievementsFor(workout(1000.0, 5, duration = 3600), emptyList())
        assertEquals(listOf("First session"), earned.map { it.label })
    }

    @Test
    fun `records, heaviest, longest and a volume gain are all recognised`() {
        val earlier = listOf(workout(1000.0, 5, duration = 1800), workout(900.0, 5, duration = 1500))
        val earned = Gamification.achievementsFor(
            workout(1200.0, 6, prs = 3, duration = 2400),
            earlier
        ).map { it.label }

        assertTrue(earned.contains("3 records"))
        assertTrue(earned.contains("Heaviest session yet"))
        assertTrue(earned.contains("Longest session yet"))
        // Measured against the session immediately before: 900 -> 1200.
        assertTrue(earned.contains("+33% volume"))
    }

    @Test
    fun `a single record reads in the singular`() {
        val earned = Gamification.achievementsFor(workout(100.0, 1, prs = 1), listOf(workout(100.0, 1)))
        assertEquals("1 record", earned.first().label)
    }

    @Test
    fun `a gain past a doubling reads as a multiplier, not a runaway percentage`() {
        val earned = Gamification.achievementsFor(workout(2500.0, 5), listOf(workout(100.0, 1)))
        assertTrue(earned.map { it.label }.contains("25× volume"))

        val half = Gamification.achievementsFor(workout(250.0, 5), listOf(workout(100.0, 1)))
        assertTrue(half.map { it.label }.contains("2.5× volume"))
    }

    @Test
    fun `a marginal gain is not worth a chip`() {
        val earned = Gamification.achievementsFor(workout(1020.0, 5), listOf(workout(1000.0, 5)))
        assertTrue(earned.none { it.label.contains("volume") })
    }

    @Test
    fun `a lighter session earns nothing rather than a negative chip`() {
        val earned = Gamification.achievementsFor(workout(500.0, 3), listOf(workout(1000.0, 5)))
        assertTrue(earned.isEmpty())
    }
}
