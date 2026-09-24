package com.ironlog.app.domain

import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.domain.usecase.Progression
import com.ironlog.app.domain.usecase.Progression.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    private val target = Progression.Target(weightKg = 60.0, stepKg = 2.5, sets = 3, repsLow = 8, repsHigh = 12)

    private fun set(n: Int, weight: Double?, reps: Int?, type: SetType = SetType.NORMAL, done: Boolean = true) =
        WorkoutSetEntity(
            id = "s$n", workoutExerciseId = "we", setNumber = n,
            type = type, weightKg = weight, reps = reps, isCompleted = done
        )

    @Test
    fun `every prescribed set at the top of the range advances by one step`() {
        val verdict = Progression.next(target, listOf(set(1, 60.0, 12), set(2, 60.0, 12), set(3, 60.0, 13)))
        assertTrue(verdict is Verdict.Advance)
        assertEquals(62.5, verdict!!.toKg, 1e-9)
    }

    @Test
    fun `one set short of the top holds`() {
        assertNull(Progression.next(target, listOf(set(1, 60.0, 12), set(2, 60.0, 12), set(3, 60.0, 11))))
    }

    @Test
    fun `fewer sets than prescribed holds even when all reach the top`() {
        assertNull(Progression.next(target, listOf(set(1, 60.0, 12), set(2, 60.0, 12))))
    }

    @Test
    fun `sets under the bottom of the range deload a tenth rounded to a step`() {
        val verdict = Progression.next(target, listOf(set(1, 60.0, 6), set(2, 60.0, 5), set(3, 60.0, 4)))
        assertTrue(verdict is Verdict.Deload)
        // 54 rounds down to the 2.5 kg grid.
        assertEquals(52.5, verdict!!.toKg, 1e-9)
    }

    @Test
    fun `a mixed session holds`() {
        assertNull(Progression.next(target, listOf(set(1, 60.0, 10), set(2, 60.0, 8), set(3, 60.0, 6))))
    }

    @Test
    fun `warm-ups, unticked sets and lighter sets are not judged`() {
        val sets = listOf(
            set(1, 40.0, 12, type = SetType.WARMUP),
            set(2, 60.0, 12),
            set(3, 60.0, 12),
            set(4, 60.0, 12, done = false),
            set(5, 60.0, 12)
        )
        val verdict = Progression.next(target, sets)
        assertTrue(verdict is Verdict.Advance)
    }

    @Test
    fun `a session lifted at another weight says nothing about the plan`() {
        assertNull(Progression.next(target, listOf(set(1, 50.0, 12), set(2, 50.0, 12), set(3, 50.0, 12))))
    }

    @Test
    fun `heavier than prescribed still counts toward the prescription`() {
        val verdict = Progression.next(target, listOf(set(1, 62.5, 12), set(2, 62.5, 12), set(3, 62.5, 12)))
        assertTrue(verdict is Verdict.Advance)
    }

    @Test
    fun `no weight or no step means no progression`() {
        val sets = listOf(set(1, 60.0, 12), set(2, 60.0, 12), set(3, 60.0, 12))
        assertNull(Progression.next(target.copy(weightKg = null), sets))
        assertNull(Progression.next(target.copy(stepKg = 0.0), sets))
    }

    @Test
    fun `a deload never goes below one step`() {
        val light = target.copy(weightKg = 2.5)
        assertNull(Progression.next(light, listOf(set(1, 2.5, 3), set(2, 2.5, 3), set(3, 2.5, 3))))
    }

    @Test
    fun `pound steps stay on a clean kilogram grid`() {
        val lb = target.copy(weightKg = 45.359, stepKg = 2.268)
        val verdict = Progression.next(lb, listOf(set(1, 45.359, 12), set(2, 45.359, 12), set(3, 45.359, 12)))
        assertEquals(47.627, verdict!!.toKg, 1e-9)
    }
}
