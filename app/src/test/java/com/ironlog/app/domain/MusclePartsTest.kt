package com.ironlog.app.domain

import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.MusclePart
import com.ironlog.app.domain.usecase.MuscleParts
import com.ironlog.app.domain.usecase.MuscleParts.Work
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MusclePartsTest {

    @Test
    fun `the name decides the split within a group`() {
        assertEquals(listOf(MusclePart.UPPER_CHEST), MuscleParts.refine("incline bench press (dumbbell)", MuscleGroup.CHEST))
        assertEquals(listOf(MusclePart.LOWER_CHEST), MuscleParts.refine("chest dip", MuscleGroup.CHEST))
        assertEquals(listOf(MusclePart.MIDDLE_DELT), MuscleParts.refine("lateral raise", MuscleGroup.SHOULDERS))
        assertEquals(listOf(MusclePart.REAR_DELT), MuscleParts.refine("rear delt fly", MuscleGroup.SHOULDERS))
        assertEquals(listOf(MusclePart.FRONT_DELT, MusclePart.MIDDLE_DELT), MuscleParts.refine("arnold press", MuscleGroup.SHOULDERS))
        assertEquals(listOf(MusclePart.LOWER_BACK), MuscleParts.refine("romanian deadlift", MuscleGroup.BACK))
        assertEquals(listOf(MusclePart.LATS), MuscleParts.refine("lat pulldown", MuscleGroup.BACK))
        assertEquals(listOf(MusclePart.OBLIQUES), MuscleParts.refine("russian twist", MuscleGroup.ABS))
        assertEquals(listOf(MusclePart.ABDUCTORS), MuscleParts.refine("hip abduction", MuscleGroup.GLUTES))
        assertEquals(listOf(MusclePart.BICEPS_LONG), MuscleParts.refine("hammer curl", MuscleGroup.BICEPS))
        assertEquals(listOf(MusclePart.BICEPS_SHORT), MuscleParts.refine("preacher curl", MuscleGroup.BICEPS))
        assertEquals(listOf(MusclePart.TRICEPS_LONG), MuscleParts.refine("skullcrusher", MuscleGroup.TRICEPS))
        assertEquals(listOf(MusclePart.TRICEPS_LATERAL, MusclePart.TRICEPS_MEDIAL), MuscleParts.refine("triceps pushdown", MuscleGroup.TRICEPS))
        assertTrue(MuscleParts.refine("outdoor run", MuscleGroup.CARDIO).isEmpty())
    }

    @Test
    fun `primary parts weigh one and helpers half, never overriding`() {
        val parts = MuscleParts.partsFor("Bench Press (Barbell)", MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS, MuscleGroup.CHEST))
        assertEquals(1f, parts.getValue(MusclePart.LOWER_CHEST))
        assertEquals(1f, parts.getValue(MusclePart.UPPER_CHEST))
        assertEquals(0.5f, parts.getValue(MusclePart.TRICEPS_LONG))
        assertEquals(0.5f, parts.getValue(MusclePart.FRONT_DELT))
        assertNull(parts[MusclePart.LATS])
    }

    @Test
    fun `weeks are Monday to Sunday and sum sets by part`() {
        val zone = ZoneId.of("UTC")
        // Thursday 24 September 2026 12:00 UTC.
        val now = 1_790_251_200_000L
        val day = 86_400_000L
        val work = listOf(
            Work("Incline Bench Press (Barbell)", MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS), now - day, sets = 4, volumeKg = 2000.0),
            Work("Chest Dip", MuscleGroup.CHEST, emptyList(), now - 2 * day, sets = 3, volumeKg = 600.0),
            // Last Sunday: the week before.
            Work("Lateral Raise", MuscleGroup.SHOULDERS, emptyList(), now - 4 * day, sets = 5, volumeKg = 300.0)
        )
        val weeks = MuscleParts.weekly(work, weeks = 2, now = now, zone = zone)
        assertEquals("This week", weeks[0].label)
        assertEquals("Last week", weeks[1].label)

        val thisWeek = weeks[0].parts
        assertEquals(4.0, thisWeek.getValue(MusclePart.UPPER_CHEST).sets, 1e-9)
        assertEquals(3.0, thisWeek.getValue(MusclePart.LOWER_CHEST).sets, 1e-9)
        // Triceps helped on the incline press: half of four sets.
        assertEquals(2.0, thisWeek.getValue(MusclePart.TRICEPS_LONG).sets, 1e-9)
        assertEquals(mapOf("Incline Bench Press (Barbell)" to 4), thisWeek.getValue(MusclePart.UPPER_CHEST).exercises)
        assertNull(thisWeek[MusclePart.MIDDLE_DELT])
        assertEquals(5.0, weeks[1].parts.getValue(MusclePart.MIDDLE_DELT).sets, 1e-9)
    }

    @Test
    fun `intensity steps with weekly sets`() {
        assertEquals(0f, MuscleParts.intensity(0.0))
        assertEquals(0.3f, MuscleParts.intensity(2.0))
        assertEquals(0.55f, MuscleParts.intensity(6.0))
        assertEquals(0.8f, MuscleParts.intensity(12.0))
        assertEquals(1f, MuscleParts.intensity(20.0))
    }
}
