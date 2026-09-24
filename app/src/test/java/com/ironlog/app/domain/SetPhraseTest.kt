package com.ironlog.app.domain

import com.ironlog.app.data.entity.UnitSystem
import com.ironlog.app.domain.usecase.SetPhrase
import com.ironlog.app.domain.usecase.TrainingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetPhraseTest {

    private fun kg(text: String) = SetPhrase.parse(text, UnitSystem.METRIC)

    @Test
    fun `digits with units are read as said`() {
        val p = kg("60 kg for 8 reps")
        assertEquals(60.0, p.weightKg!!, 1e-6)
        assertEquals(8, p.reps)
    }

    @Test
    fun `number words are folded into numbers`() {
        val p = kg("sixty kilos for eight")
        assertEquals(60.0, p.weightKg!!, 1e-6)
        assertEquals(8, p.reps)
        val big = kg("one hundred and ten kilos ten reps")
        assertEquals(110.0, big.weightKg!!, 1e-6)
        assertEquals(10, big.reps)
        val half = kg("sixty two and a half kilos for five")
        assertEquals(62.5, half.weightKg!!, 1e-6)
        val point = kg("sixty two point five kilos for five")
        assertEquals(62.5, point.weightKg!!, 1e-6)
        assertEquals(5, point.reps)
        assertEquals(5, half.reps)
    }

    @Test
    fun `pounds said aloud are converted, and the user's unit is assumed when none is said`() {
        val said = kg("135 pounds 5 reps")
        assertEquals(TrainingMath.lbToKg(135.0), said.weightKg!!, 1e-6)
        val assumed = SetPhrase.parse("135 for 5", UnitSystem.IMPERIAL)
        assertEquals(TrainingMath.lbToKg(135.0), assumed.weightKg!!, 1e-6)
        assertEquals(5, assumed.reps)
        val metric = SetPhrase.parse("100 for 3", UnitSystem.METRIC)
        assertEquals(100.0, metric.weightKg!!, 1e-6)
    }

    @Test
    fun `two bare numbers are weight then reps`() {
        val p = kg("80 8")
        assertEquals(80.0, p.weightKg!!, 1e-6)
        assertEquals(8, p.reps)
    }

    @Test
    fun `bodyweight work has reps and no weight`() {
        val p = kg("bodyweight twelve")
        assertNull(p.weightKg)
        assertEquals(12, p.reps)
    }

    @Test
    fun `nonsense reads as nothing rather than as a lift`() {
        assertTrue(kg("what a lovely day").isEmpty)
        assertTrue(kg("").isEmpty)
    }
}
