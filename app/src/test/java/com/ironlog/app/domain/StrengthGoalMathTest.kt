package com.ironlog.app.domain

import com.ironlog.app.data.dao.HistoricSet
import com.ironlog.app.data.entity.GoalMetric
import com.ironlog.app.data.entity.SetType
import com.ironlog.app.data.entity.WorkoutSetEntity
import com.ironlog.app.domain.usecase.StrengthGoalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrengthGoalMathTest {

    private fun historic(
        weight: Double?,
        reps: Int?,
        workoutId: String = "w1",
        startedAt: Long = 1_000L,
        type: SetType = SetType.NORMAL,
        done: Boolean = true
    ) = HistoricSet(
        set = WorkoutSetEntity(
            id = "s-$workoutId-$weight-$reps", workoutExerciseId = "we", setNumber = 1,
            type = type, weightKg = weight, reps = reps, isCompleted = done
        ),
        workoutId = workoutId,
        startedAt = startedAt
    )

    @Test
    fun `top weight is the weight itself and ignores reps`() {
        assertEquals(100.0, StrengthGoalMath.setValue(historic(100.0, 1), GoalMetric.TOP_WEIGHT), 1e-9)
        assertEquals(100.0, StrengthGoalMath.setValue(historic(100.0, 8), GoalMetric.TOP_WEIGHT), 1e-9)
    }

    @Test
    fun `an estimate comes from one set's own weight and reps`() {
        // Epley: 100 x (1 + 5/30).
        assertEquals(116.667, StrengthGoalMath.setValue(historic(100.0, 5), GoalMetric.ESTIMATED_1RM), 1e-3)
    }

    @Test
    fun `a high-rep set is not estimated from`() {
        assertEquals(0.0, StrengthGoalMath.setValue(historic(60.0, 20), GoalMetric.ESTIMATED_1RM), 1e-9)
        // The weight itself still counts on the other metric.
        assertEquals(60.0, StrengthGoalMath.setValue(historic(60.0, 20), GoalMetric.TOP_WEIGHT), 1e-9)
    }

    @Test
    fun `warm-ups and unticked sets count for nothing`() {
        assertEquals(0.0, StrengthGoalMath.setValue(historic(140.0, 1, type = SetType.WARMUP), GoalMetric.TOP_WEIGHT), 1e-9)
        assertEquals(0.0, StrengthGoalMath.setValue(historic(140.0, 1, done = false), GoalMetric.TOP_WEIGHT), 1e-9)
    }

    @Test
    fun `the best set in a session is never assembled from two different sets`() {
        // A heavy triple and a light set of twelve in one session. The estimate
        // must come from one set, not from 140 kg combined with 12 reps.
        val sets = listOf(historic(140.0, 3), historic(60.0, 12))
        val best = StrengthGoalMath.best(sets, GoalMetric.ESTIMATED_1RM)
        assertEquals(154.0, best, 1e-3)
        assertEquals(140.0, StrengthGoalMath.best(sets, GoalMetric.TOP_WEIGHT), 1e-9)
    }

    @Test
    fun `the series carries one point per session, oldest first`() {
        val sets = listOf(
            historic(100.0, 5, workoutId = "w2", startedAt = 2_000L),
            historic(90.0, 5, workoutId = "w1", startedAt = 1_000L),
            historic(95.0, 5, workoutId = "w1", startedAt = 1_000L)
        )
        val series = StrengthGoalMath.series(sets, GoalMetric.TOP_WEIGHT)
        assertEquals(listOf(1_000L, 2_000L), series.map { it.startedAt })
        assertEquals(listOf(95.0, 100.0), series.map { it.valueKg })
    }

    @Test
    fun `a session with nothing countable is left out rather than plotted as zero`() {
        val sets = listOf(
            historic(100.0, 5, workoutId = "w1", startedAt = 1_000L),
            historic(80.0, 5, workoutId = "w2", startedAt = 2_000L, type = SetType.WARMUP)
        )
        assertEquals(1, StrengthGoalMath.series(sets, GoalMetric.TOP_WEIGHT).size)
    }

    @Test
    fun `progress is the share of the target and never overflows`() {
        assertEquals(0.5f, StrengthGoalMath.progress(90.0, 180.0), 1e-6f)
        assertEquals(1f, StrengthGoalMath.progress(200.0, 180.0), 1e-6f)
        assertEquals(0f, StrengthGoalMath.progress(90.0, 0.0), 1e-6f)
    }

    @Test
    fun `a target is reached once it is matched, and nothing is left to lift`() {
        assertTrue(StrengthGoalMath.isAchieved(180.0, 180.0))
        assertFalse(StrengthGoalMath.isAchieved(179.9, 180.0))
        assertEquals(0.0, StrengthGoalMath.remainingKg(200.0, 180.0), 1e-9)
        assertEquals(40.0, StrengthGoalMath.remainingKg(140.0, 180.0), 1e-9)
    }
}
