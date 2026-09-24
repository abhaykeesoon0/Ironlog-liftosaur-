package com.ironlog.app.domain

import com.ironlog.app.domain.usecase.Coach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {

    private val context = Coach.Context(
        displayName = "Abhay",
        workoutsThisWeek = 3,
        weeklyGoal = 4,
        streakWeeks = 2,
        recordsThisWeek = 1,
        volumeThisWeekKg = 12_000.0,
        recovering = emptyList(),
        topLifts = emptyList(),
        bodyweightKg = 80.0,
        bodyweightChangeKg = null
    )

    @Test
    fun `a word inside another word does not match`() {
        // "creatine" contains "eat"; that must not become a diet answer.
        val answer = Coach.answer("What is creatine?", context)
        assertTrue(answer.isFallback)
        assertFalse(answer.isReferral)
    }

    @Test
    fun `whole words still reach their topic`() {
        assertEquals("General nutrition guidance", Coach.answer("How much protein do I need?", context).source)
        assertFalse(Coach.answer("What should I train today?", context).isFallback)
        assertFalse(Coach.answer("How long should I rest between sets?", context).isFallback)
    }

    @Test
    fun `the topic with the most matching words wins`() {
        // "rest" alone could be recovery; "rest between sets" is rest times.
        val answer = Coach.answer("how long should i rest between sets", context)
        assertEquals("Rest guidance", answer.source)
    }

    @Test
    fun `medical questions are referred before anything else`() {
        val answer = Coach.answer("My knee hurts when I squat, what should I train today?", context)
        assertTrue(answer.isReferral)
    }
}
