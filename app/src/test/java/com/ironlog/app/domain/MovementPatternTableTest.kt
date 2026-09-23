package com.ironlog.app.domain

import com.ironlog.app.data.seed.ExerciseSeed
import com.ironlog.app.data.seed.MovementPatterns
import com.ironlog.app.domain.usecase.MovementPattern
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * The demonstrations have to match the exercises they belong to.
 *
 * Every seeded exercise carries a movement decided offline, so the first test
 * is simply that none of them is missing: an exercise with no entry falls
 * through to reading its own name, which is how the wrong animations got
 * shipped in the first place.
 *
 * The rest are the specific lifts the name-reading classifier got wrong. They
 * are kept as named cases rather than folded into a count, because each one is
 * a real thing a lifter would have opened and seen the wrong movement for.
 */
class MovementPatternTableTest {

    @Test
    fun `every seeded exercise has a movement`() {
        val missing = ExerciseSeed.all.filter { it.id !in MovementPatterns.byExerciseId }
        assertTrue("No movement for: ${missing.joinToString { it.name }}", missing.isEmpty())
    }

    @Test
    fun `the table describes only exercises that exist`() {
        val ids = ExerciseSeed.all.map { it.id }.toSet()
        val orphans = MovementPatterns.byExerciseId.keys - ids
        assertTrue("Movements for unknown exercises: $orphans", orphans.isEmpty())
    }

    @Test
    fun `the lifts the name-reading classifier got wrong`() {
        // A leg extension is a knee extension, not a triceps one.
        assertPattern("leg-extension", MovementPattern.LEG_EXTENSION)
        // A leg curl bends the knee; it is not a hip hinge.
        assertPattern("leg-curl-lying", MovementPattern.LEG_CURL)
        assertPattern("leg-curl-seated", MovementPattern.LEG_CURL)
        // "Back extension" is a hinge, whatever the word extension suggests.
        assertPattern("hyperextension", MovementPattern.HINGE)
        // Contains "bench", but it is a row and a rollout respectively.
        assertPattern("fx-straight-bar-bench-mid-rows", MovementPattern.HORIZONTAL_PULL)
        assertPattern("fx-barbell-rollout-from-bench", MovementPattern.CORE)
        // A pullover sweeps overhead on a bench; it is not a pulldown.
        assertPattern("fx-straight-arm-dumbbell-pullover", MovementPattern.PULLOVER)
        // Rack pulls are a partial deadlift, not a vertical pull.
        assertPattern("fx-rack-pulls", MovementPattern.HINGE)
        // "Hang" here is a grip position, not a hold.
        assertPattern("fx-hang-clean", MovementPattern.HINGE)
        // The plural "Rows" has to count as a row.
        assertPattern("fx-elevated-cable-rows", MovementPattern.HORIZONTAL_PULL)
        // A rear delt row is a row; only the fly version is a raise.
        assertPattern("fx-barbell-rear-delt-row", MovementPattern.HORIZONTAL_PULL)
        assertPattern("rear-delt-fly", MovementPattern.LATERAL_RAISE)
        // A step-up with a knee raise is still a step-up.
        assertPattern("fx-step-up-with-knee-raise", MovementPattern.LUNGE)
        // A high pulley is not a high pull.
        assertPattern("fx-kneeling-high-pulley-row", MovementPattern.HORIZONTAL_PULL)
    }

    /**
     * The fallback has to agree with the table on the same lift, or a user who
     * types out an exercise the library already has gets a different demo from
     * the seeded one of the same name.
     */
    @Test
    fun `reading the name agrees with the table on the lifts it can settle`() {
        val cases = listOf(
            "leg-extension", "leg-curl-lying", "hyperextension", "fx-rack-pulls",
            "fx-straight-arm-dumbbell-pullover", "fx-elevated-cable-rows",
            "fx-step-up-with-knee-raise", "upright-row", "bench-press-barbell",
            "squat-back", "deadlift-conventional", "lat-pulldown"
        )
        cases.forEach { id ->
            val exercise = ExerciseSeed.all.first { it.id == id }
            assertEquals(
                "Reading the name of \"${exercise.name}\" disagrees with the table",
                MovementPatterns.byExerciseId.getValue(id),
                MovementPattern.of(exercise.name, exercise.primaryMuscle, exercise.category)
            )
        }
    }

    private fun assertPattern(id: String, expected: MovementPattern) {
        assertEquals("Wrong movement for $id", expected, MovementPatterns.byExerciseId[id])
    }
}
