package com.ironlog.app.domain

import com.ironlog.app.data.seed.ExerciseSeed
import com.ironlog.app.domain.usecase.RoutineGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineGeneratorTest {

    private val library = ExerciseSeed.all

    private fun plan(
        goal: RoutineGenerator.Goal = RoutineGenerator.Goal.MUSCLE,
        days: Int = 3,
        minutes: Int = 60
    ) = RoutineGenerator.generate(RoutineGenerator.Request(goal, days, minutes), library)

    @Test
    fun `the number of days asked for is the number of days produced`() {
        (2..6).forEach { days ->
            assertEquals(days, plan(days = days).days.size)
        }
    }

    @Test
    fun `an unreasonable number of days is clamped rather than producing nonsense`() {
        assertEquals(2, plan(days = 1).days.size)
        assertEquals(6, plan(days = 9).days.size)
    }

    @Test
    fun `every day has exercises and none is empty`() {
        val generated = plan(days = 5)
        assertTrue(generated.days.all { it.exercises.isNotEmpty() })
    }

    @Test
    fun `no exercise is prescribed twice in a week`() {
        val ids = plan(days = 6, minutes = 90).days.flatMap { day -> day.exercises.map { it.exercise.id } }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `a longer session earns more exercises, and a short one stays short`() {
        val quick = plan(minutes = 30).days.first().exercises.size
        val long = plan(minutes = 90).days.first().exercises.size
        assertTrue("$quick should be fewer than $long", quick < long)
    }

    @Test
    fun `strength is heavy and low, general fitness is lighter and quicker`() {
        val strength = plan(goal = RoutineGenerator.Goal.STRENGTH).days.first().exercises.first()
        val general = plan(goal = RoutineGenerator.Goal.GENERAL).days.first().exercises.first()

        assertTrue(strength.repsHigh <= 5)
        assertTrue(general.repsLow >= 8)
        // Heavy work needs longer between sets, which is most of why it takes
        // longer to do the same number of exercises.
        assertTrue(strength.restSeconds > general.restSeconds)
    }

    @Test
    fun `a day covers the muscles its name promises`() {
        val legs = plan(days = 3).days.single { it.name == "Legs" }
        val muscles = legs.exercises.map { it.exercise.primaryMuscle.name }
        assertTrue(
            "legs day trained $muscles",
            muscles.any { it in setOf("QUADS", "HAMSTRINGS", "GLUTES", "CALVES") }
        )
        // And nothing from the opposite end of the body.
        assertTrue(muscles.none { it in setOf("CHEST", "BICEPS", "TRICEPS") })
    }

    @Test
    fun `cardio and timed work are left out of a lifting routine`() {
        val categories = plan(days = 6).days
            .flatMap { day -> day.exercises.map { it.exercise.category.name } }
        assertTrue(categories.none { it == "CARDIO" || it == "DURATION" })
    }

    @Test
    fun `every prescription is something a person could actually perform`() {
        plan(days = 6, minutes = 120).days.flatMap { it.exercises }.forEach { slot ->
            assertTrue(slot.sets in 1..8)
            assertTrue(slot.repsLow in 1..20)
            assertTrue(slot.repsHigh >= slot.repsLow)
            assertTrue(slot.restSeconds in 30..300)
        }
    }
}
