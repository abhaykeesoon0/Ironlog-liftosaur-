package com.ironlog.app.domain

import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.domain.usecase.Recovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryTest {

    private val now = 1_700_000_000_000L
    private fun hoursAgo(hours: Int) = now - hours * 3_600_000L

    private fun session(muscle: MuscleGroup, hours: Int, volume: Double) =
        Recovery.MuscleSession(muscle, hoursAgo(hours), volume)

    @Test
    fun `a light session recovers over the 36 hour floor`() {
        // Half the usual volume or less sits at the floor.
        assertEquals(36, Recovery.windowHours(sessionVolumeKg = 500.0, averageVolumeKg = 1000.0))
        assertEquals(36, Recovery.windowHours(sessionVolumeKg = 100.0, averageVolumeKg = 1000.0))
    }

    @Test
    fun `a usual session lands at two days and a double one at the ceiling`() {
        assertEquals(48, Recovery.windowHours(sessionVolumeKg = 1000.0, averageVolumeKg = 1000.0))
        assertEquals(72, Recovery.windowHours(sessionVolumeKg = 2000.0, averageVolumeKg = 1000.0))
        // Nothing stretches past the ceiling, however heavy the day was.
        assertEquals(72, Recovery.windowHours(sessionVolumeKg = 9000.0, averageVolumeKg = 1000.0))
    }

    @Test
    fun `a muscle with no history is treated as a normal day`() {
        assertEquals(48, Recovery.windowHours(sessionVolumeKg = 1000.0, averageVolumeKg = 0.0))
    }

    @Test
    fun `recovery climbs from zero at the session to fresh at the window`() {
        val justTrained = Recovery.states(listOf(session(MuscleGroup.CHEST, 0, 1000.0)), now).single()
        assertEquals(0f, justTrained.fraction, 1e-3f)
        assertFalse(justTrained.isFresh)
        // One session, so it is its own average: a normal day, 48 hours.
        assertEquals(48, justTrained.windowHours)
        assertEquals(48, justTrained.hoursRemaining)

        val halfway = Recovery.states(listOf(session(MuscleGroup.CHEST, 24, 1000.0)), now).single()
        assertEquals(0.5f, halfway.fraction, 1e-3f)
        assertEquals(24, halfway.hoursRemaining)

        val done = Recovery.states(listOf(session(MuscleGroup.CHEST, 60, 1000.0)), now).single()
        assertTrue(done.isFresh)
        assertEquals(0, done.hoursRemaining)
    }

    @Test
    fun `only the most recent session sets the clock`() {
        val states = Recovery.states(
            listOf(
                session(MuscleGroup.QUADS, 200, 1000.0),
                session(MuscleGroup.QUADS, 12, 1000.0)
            ),
            now
        )
        assertEquals(1, states.size)
        assertEquals(hoursAgo(12), states.single().lastTrainedAt)
    }

    @Test
    fun `least recovered comes first`() {
        val states = Recovery.states(
            listOf(
                session(MuscleGroup.BACK, 40, 1000.0),
                session(MuscleGroup.CHEST, 6, 1000.0),
                session(MuscleGroup.BICEPS, 24, 1000.0)
            ),
            now
        )
        assertEquals(
            listOf(MuscleGroup.CHEST, MuscleGroup.BICEPS, MuscleGroup.BACK),
            states.map { it.muscle }
        )
    }

    @Test
    fun `cardio is not a muscle and stays out of the readout`() {
        val states = Recovery.states(
            listOf(session(MuscleGroup.CARDIO, 2, 0.0), session(MuscleGroup.CALVES, 2, 100.0)),
            now
        )
        assertEquals(listOf(MuscleGroup.CALVES), states.map { it.muscle })
    }

    @Test
    fun `no training means nothing to report rather than a list of zeroes`() {
        assertTrue(Recovery.states(emptyList(), now).isEmpty())
    }
}
