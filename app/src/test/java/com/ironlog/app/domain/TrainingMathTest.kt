package com.ironlog.app.domain

import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.domain.usecase.TrainingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TrainingMathTest {

    private fun set(weight: Double?, reps: Int?, type: SetType = SetType.NORMAL, done: Boolean = true) =
        WorkoutSetEntity(
            id = "s", workoutExerciseId = "we", setNumber = 1,
            type = type, weightKg = weight, reps = reps, isCompleted = done
        )

    @Test
    fun `epley estimate matches the formula and a single is the weight itself`() {
        assertEquals(100.0, TrainingMath.estimatedOneRepMax(100.0, 1), 1e-9)
        assertEquals(116.667, TrainingMath.estimatedOneRepMax(100.0, 5), 1e-3)
        assertEquals(0.0, TrainingMath.estimatedOneRepMax(0.0, 5), 1e-9)
        assertEquals(0.0, TrainingMath.estimatedOneRepMax(100.0, 0), 1e-9)
    }

    @Test
    fun `warm-up and incomplete sets add no volume`() {
        val sets = listOf(
            set(60.0, 10, SetType.WARMUP),
            set(100.0, 5),
            set(100.0, 5, done = false),
            set(80.0, 8, SetType.DROP)
        )
        assertEquals(500.0 + 640.0, TrainingMath.totalVolume(sets), 1e-9)
        assertEquals(13, TrainingMath.totalReps(sets))
        assertEquals(2, TrainingMath.workingSetCount(sets))
    }

    @Test
    fun `double progression raises, holds and lowers the load`() {
        val allTop = listOf(set(60.0, 12), set(60.0, 12))
        assertEquals(62.5, TrainingMath.suggestNextLoad(allTop, 8, 12, 2.5)!!.weightKg, 1e-9)

        val inRange = listOf(set(60.0, 10), set(60.0, 9))
        assertEquals(60.0, TrainingMath.suggestNextLoad(inRange, 8, 12, 2.5)!!.weightKg, 1e-9)

        val missed = listOf(set(60.0, 8), set(60.0, 6))
        assertEquals(57.5, TrainingMath.suggestNextLoad(missed, 8, 12, 2.5)!!.weightKg, 1e-9)

        assertNull(TrainingMath.suggestNextLoad(emptyList(), 8, 12, 2.5))
    }

    @Test
    fun `plate loading is exact when possible and reports the remainder otherwise`() {
        val exact = TrainingMath.platesPerSide(142.5, 20.0)
        assertEquals(listOf(25.0, 25.0, 10.0, 1.25), exact.plates)
        assertTrue(exact.isExact)
        assertEquals(142.5, exact.achievableTotalKg, 1e-9)

        val inexact = TrainingMath.platesPerSide(101.0, 20.0)
        assertFalse(inexact.isExact)
        assertEquals(100.0, inexact.achievableTotalKg, 1e-9)

        val barOnly = TrainingMath.platesPerSide(15.0, 20.0)
        assertTrue(barOnly.plates.isEmpty())
    }

    @Test
    fun `weekly streak counts back from the current or previous week`() {
        val zone = ZoneId.systemDefault()
        fun millis(date: LocalDate) = date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        val today = LocalDate.now(zone)

        assertEquals(0, TrainingMath.weeklyStreak(emptyList(), zone))
        assertEquals(3, TrainingMath.weeklyStreak(
            listOf(millis(today), millis(today.minusWeeks(1)), millis(today.minusWeeks(2))), zone
        ))
        // Nothing yet this week does not break a streak built up to last week.
        assertEquals(2, TrainingMath.weeklyStreak(
            listOf(millis(today.minusWeeks(1)), millis(today.minusWeeks(2))), zone
        ))
        // A gap week ends it.
        assertEquals(1, TrainingMath.weeklyStreak(
            listOf(millis(today), millis(today.minusWeeks(2))), zone
        ))
    }

    @Test
    fun `unit conversions round trip`() {
        assertEquals(100.0, TrainingMath.lbToKg(TrainingMath.kgToLb(100.0)), 1e-9)
        assertEquals(180.0, TrainingMath.inchesToCm(TrainingMath.cmToInches(180.0)), 1e-9)
        assertEquals(47.5, TrainingMath.roundToIncrement(47.3), 1e-9)
    }
}
