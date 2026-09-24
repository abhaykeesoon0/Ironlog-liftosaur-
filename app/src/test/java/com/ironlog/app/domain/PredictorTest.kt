package com.ironlog.app.domain

import com.ironlog.app.data.entity.TrainingFocus
import com.ironlog.app.domain.usecase.Predictor
import com.ironlog.app.domain.usecase.Predictor.Confidence
import com.ironlog.app.domain.usecase.Predictor.Sample
import com.ironlog.app.domain.usecase.TrainingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorTest {

    private val day = 86_400_000L
    private val start = 1_700_000_000_000L

    /** One session a week, adding [stepKg] to a 5-rep top set each time. */
    private fun weekly(n: Int, fromKg: Double, stepKg: Double, reps: Int = 5) =
        (0 until n).map { i -> Sample(start + i * 7 * day, fromKg + i * stepKg, reps) }

    @Test
    fun `nothing weighted means no forecast`() {
        assertNull(Predictor.forecast(emptyList(), TrainingFocus.STRENGTH))
        assertNull(Predictor.forecast(listOf(Sample(start, 0.0, 10)), TrainingFocus.STRENGTH))
    }

    @Test
    fun `one session is a rough guess at the same strength`() {
        val f = Predictor.forecast(listOf(Sample(start, 100.0, 5)), TrainingFocus.STRENGTH, now = start + 3 * day)
        assertNotNull(f)
        assertEquals(Confidence.LOW, f!!.confidence)
        assertEquals(TrainingMath.estimatedOneRepMax(100.0, 5), f.oneRmKg, 0.1)
        assertEquals(5, f.reps)
        assertEquals(5, f.sets)
        // 116.7 1RM at five reps is 100 kg on the bar.
        assertEquals(100.0, f.weightKg, 1e-9)
    }

    @Test
    fun `a steady rise is followed, with confidence, and rounded to the plates`() {
        val samples = weekly(6, 80.0, 2.5)
        val f = Predictor.forecast(samples, TrainingFocus.STRENGTH, now = samples.last().at + day)!!
        assertEquals(Confidence.HIGH, f.confidence)
        assertTrue("trend should be positive", f.trendKgPerWeek > 0)
        // 92.5 was the last top set; a week on the line says 95.
        assertEquals(95.0, f.weightKg, 1e-9)
        assertEquals(0.0, f.weightKg % 2.5, 1e-9)
    }

    @Test
    fun `a jump is capped so one lucky day is not a promise`() {
        val samples = listOf(
            Sample(start, 100.0, 5),
            Sample(start + 7 * day, 100.0, 5),
            Sample(start + 14 * day, 130.0, 5)
        )
        val f = Predictor.forecast(samples, TrainingFocus.STRENGTH, now = start + 15 * day)!!
        val lastOneRm = TrainingMath.estimatedOneRepMax(130.0, 5)
        assertTrue(f.oneRmKg <= lastOneRm * (1 + Predictor.MAX_STEP) + 0.1)
    }

    @Test
    fun `the focus decides the reps and sets`() {
        val samples = weekly(4, 60.0, 2.5)
        val strength = Predictor.forecast(samples, TrainingFocus.STRENGTH, now = samples.last().at)!!
        val muscle = Predictor.forecast(samples, TrainingFocus.MUSCLE, now = samples.last().at)!!
        val endurance = Predictor.forecast(samples, TrainingFocus.ENDURANCE, now = samples.last().at)!!
        assertEquals(5, strength.reps); assertEquals(5, strength.sets)
        assertEquals(8, muscle.reps); assertEquals(4, muscle.sets)
        assertEquals(15, endurance.reps); assertEquals(3, endurance.sets)
        // The same strength written for more reps is a lighter bar.
        assertTrue(strength.weightKg > muscle.weightKg && muscle.weightKg > endurance.weightKg)
    }

    @Test
    fun `pound plates round to pound increments`() {
        val samples = weekly(3, 60.0, 2.0)
        val inc = TrainingMath.lbToKg(5.0)
        val f = Predictor.forecast(samples, TrainingFocus.GENERAL, incrementKg = inc, now = samples.last().at)!!
        val lb = TrainingMath.kgToLb(f.weightKg)
        assertEquals(0.0, lb % 5.0, 1e-6)
    }

    @Test
    fun `projection follows the line but no faster than the cap`() {
        val samples = weekly(5, 80.0, 1.0)
        val now = samples.last().at
        val in4 = Predictor.projectOneRm(samples, 4, now)!!
        val last = TrainingMath.estimatedOneRepMax(84.0, 5)
        assertTrue(in4 > last)
        assertTrue(in4 <= last * (1 + 4 * Predictor.MAX_STEP) + 0.1)
        assertNull(Predictor.projectOneRm(samples.take(1), 4, now))
    }

    @Test
    fun `weeks to a goal come from the trend and only when it is rising`() {
        val rising = weekly(5, 80.0, 2.5).map { it.at to it.weightKg }
        val weeks = Predictor.weeksToReach(rising, 100.0)
        assertNotNull(weeks)
        // 90 now, 100 wanted, 2.5 a week: four weeks.
        assertEquals(4, weeks)
        val flat = weekly(5, 80.0, 0.0).map { it.at to it.weightKg }
        assertNull(Predictor.weeksToReach(flat, 100.0))
        assertNull(Predictor.weeksToReach(rising, 85.0))
    }

    @Test
    fun `the description says what it is standing on`() {
        val samples = weekly(4, 60.0, 2.5)
        val f = Predictor.forecast(samples, TrainingFocus.MUSCLE, now = samples.last().at)!!
        val text = Predictor.describe(f)
        assertTrue(text.contains("last 4 sessions"))
        assertTrue(text.contains("up"))
    }
}
