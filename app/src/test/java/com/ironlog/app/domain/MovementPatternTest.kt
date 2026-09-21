package com.ironlog.app.domain

import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.seed.ExerciseSeed
import com.ironlog.app.domain.usecase.MovementPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementPatternTest {

    private fun pattern(
        name: String,
        muscle: MuscleGroup = MuscleGroup.CHEST,
        category: ExerciseCategory = ExerciseCategory.BARBELL
    ) = MovementPattern.of(name, muscle, category)

    @Test
    fun `the name decides the pattern, not the muscle it is filed under`() {
        // A Romanian deadlift is a hinge whether the dataset calls it hamstrings
        // or glutes or lower back.
        assertEquals(MovementPattern.HINGE, pattern("Romanian Deadlift", MuscleGroup.BACK))
        assertEquals(MovementPattern.HINGE, pattern("Romanian Deadlift", MuscleGroup.GLUTES))
        assertEquals(MovementPattern.SQUAT, pattern("Front Squat", MuscleGroup.GLUTES))
    }

    @Test
    fun `presses are told apart by where they press`() {
        assertEquals(MovementPattern.HORIZONTAL_PRESS, pattern("Bench Press (Barbell)"))
        assertEquals(MovementPattern.HORIZONTAL_PRESS, pattern("Incline Dumbbell Press"))
        assertEquals(MovementPattern.VERTICAL_PRESS, pattern("Standing Military Press", MuscleGroup.SHOULDERS))
        assertEquals(MovementPattern.VERTICAL_PRESS, pattern("Push Press", MuscleGroup.SHOULDERS))
        // No direction in the name, so the muscle settles it.
        assertEquals(MovementPattern.VERTICAL_PRESS, pattern("Machine Press", MuscleGroup.SHOULDERS))
        assertEquals(MovementPattern.HORIZONTAL_PRESS, pattern("Machine Press", MuscleGroup.CHEST))
    }

    @Test
    fun `pulls are told apart by direction too`() {
        assertEquals(MovementPattern.HORIZONTAL_PULL, pattern("Bent Over Barbell Row", MuscleGroup.BACK))
        assertEquals(MovementPattern.VERTICAL_PULL, pattern("Wide-Grip Lat Pulldown", MuscleGroup.LATS))
        assertEquals(MovementPattern.VERTICAL_PULL, pattern("Pull-Up", MuscleGroup.LATS))
        assertEquals(MovementPattern.VERTICAL_PULL, pattern("Chin-Up", MuscleGroup.LATS))
    }

    @Test
    fun `a calf press is a calf raise, not a press`() {
        assertEquals(MovementPattern.CALF_RAISE, pattern("Calf Press", MuscleGroup.CALVES, ExerciseCategory.MACHINE))
        assertEquals(MovementPattern.CALF_RAISE, pattern("Standing Calf Raises", MuscleGroup.CALVES))
    }

    @Test
    fun `a leg curl is a hinge, not an arm curl`() {
        assertEquals(MovementPattern.HINGE, pattern("Lying Leg Curl", MuscleGroup.HAMSTRINGS, ExerciseCategory.MACHINE))
        assertEquals(MovementPattern.CURL, pattern("Hammer Curls", MuscleGroup.BICEPS, ExerciseCategory.DUMBBELL))
    }

    @Test
    fun `a leg raise is core work rather than a shoulder raise`() {
        assertEquals(MovementPattern.CORE, pattern("Hanging Leg Raise", MuscleGroup.ABS, ExerciseCategory.BODYWEIGHT))
        assertEquals(MovementPattern.LATERAL_RAISE, pattern("Lateral Raise", MuscleGroup.SHOULDERS, ExerciseCategory.DUMBBELL))
    }

    @Test
    fun `cardio is cardio whatever it is called`() {
        assertEquals(MovementPattern.CARDIO, pattern("Rowing Machine", MuscleGroup.CARDIO, ExerciseCategory.CARDIO))
    }

    @Test
    fun `an invented exercise still gets a pattern from its muscle`() {
        assertEquals(MovementPattern.CURL, pattern("Someone's Special", MuscleGroup.BICEPS, ExerciseCategory.CABLE))
        assertEquals(MovementPattern.SQUAT, pattern("Unnamed Leg Thing", MuscleGroup.QUADS, ExerciseCategory.MACHINE))
    }

    @Test
    fun `every exercise in the library resolves to a pattern`() {
        // The classifier has a total fallback, so this cannot throw -- what it
        // guards is the library drifting into names nothing matches, leaving a
        // pile of exercises all demonstrating the same default shape.
        val patterns = ExerciseSeed.all.map {
            MovementPattern.of(it.name, it.primaryMuscle, it.category)
        }
        assertEquals(ExerciseSeed.all.size, patterns.size)
        // Spread rather than everything collapsing onto one shape.
        assertTrue("patterns used: ${patterns.distinct().size}", patterns.distinct().size >= 10)
        val commonest = patterns.groupingBy { it }.eachCount().maxOf { it.value }
        assertTrue("one pattern covers $commonest of ${patterns.size}", commonest < patterns.size / 2)
    }
}
