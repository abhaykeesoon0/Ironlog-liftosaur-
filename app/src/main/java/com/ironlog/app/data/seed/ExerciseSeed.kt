package com.ironlog.app.data.seed

import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.MuscleGroup

/**
 * The exercise library the app ships with.
 *
 * Seeded rows have a null ownerUserId, which marks them as shared across every
 * account on the device; user-created movements carry the owner's id and are
 * only visible to them. Ids are stable, human-readable slugs rather than random
 * UUIDs so that a future version can add or correct an entry without orphaning
 * the workout history that references it.
 *
 * Barbell movements carry a default bar weight, which the plate calculator
 * subtracts before working out what to load on each side.
 */
object ExerciseSeed {

    private const val OLYMPIC_BAR = 20.0
    private const val EZ_BAR = 10.0

    private fun exercise(
        id: String,
        name: String,
        primary: MuscleGroup,
        category: ExerciseCategory,
        secondary: List<MuscleGroup> = emptyList(),
        bar: Double? = null,
        instructions: String = ""
    ) = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscle = primary,
        secondaryMuscles = secondary.joinToString(",") { it.name },
        category = category,
        instructions = instructions,
        ownerUserId = null,
        isCustom = false,
        defaultBarWeightKg = bar
    )

    val all: List<ExerciseEntity> = listOf(

        // ---------------------------------------------------------- CHEST
        exercise(
            "bench-press-barbell", "Bench Press (Barbell)", MuscleGroup.CHEST,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS), OLYMPIC_BAR,
            "Lie flat, grip just outside shoulder width. Lower to mid-chest with elbows tucked around 45 degrees, then press back to lockout."
        ),
        exercise(
            "bench-press-incline-barbell", "Incline Bench Press (Barbell)", MuscleGroup.CHEST,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS), OLYMPIC_BAR,
            "Set the bench to 30 degrees. A steeper angle shifts the work to the front delts."
        ),
        exercise(
            "bench-press-dumbbell", "Bench Press (Dumbbell)", MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS)
        ),
        exercise(
            "bench-press-incline-dumbbell", "Incline Bench Press (Dumbbell)", MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)
        ),
        exercise(
            "chest-fly-dumbbell", "Chest Fly (Dumbbell)", MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.SHOULDERS)
        ),
        exercise(
            "cable-fly-mid", "Cable Fly", MuscleGroup.CHEST, ExerciseCategory.CABLE
        ),
        exercise(
            "chest-press-machine", "Chest Press (Machine)", MuscleGroup.CHEST,
            ExerciseCategory.MACHINE, listOf(MuscleGroup.TRICEPS)
        ),
        exercise(
            "push-up", "Push Up", MuscleGroup.CHEST,
            ExerciseCategory.BODYWEIGHT, listOf(MuscleGroup.TRICEPS, MuscleGroup.ABS)
        ),
        exercise(
            "dip-chest", "Chest Dip", MuscleGroup.CHEST,
            ExerciseCategory.WEIGHTED_BODYWEIGHT, listOf(MuscleGroup.TRICEPS)
        ),

        // ----------------------------------------------------------- BACK
        exercise(
            "deadlift-conventional", "Deadlift", MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.TRAPS, MuscleGroup.FOREARMS),
            OLYMPIC_BAR,
            "Bar over mid-foot, shoulders slightly ahead of the bar. Push the floor away rather than pulling with the back."
        ),
        exercise(
            "deadlift-romanian", "Romanian Deadlift", MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.GLUTES, MuscleGroup.BACK), OLYMPIC_BAR,
            "Soft knees, hinge at the hip and push the hips back. Stop when the hamstrings reach full stretch."
        ),
        exercise(
            "pull-up", "Pull Up", MuscleGroup.LATS,
            ExerciseCategory.WEIGHTED_BODYWEIGHT, listOf(MuscleGroup.BICEPS, MuscleGroup.BACK)
        ),
        exercise(
            "chin-up", "Chin Up", MuscleGroup.LATS,
            ExerciseCategory.WEIGHTED_BODYWEIGHT, listOf(MuscleGroup.BICEPS)
        ),
        exercise(
            "lat-pulldown", "Lat Pulldown", MuscleGroup.LATS,
            ExerciseCategory.CABLE, listOf(MuscleGroup.BICEPS)
        ),
        exercise(
            "row-barbell", "Barbell Row", MuscleGroup.BACK,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS), OLYMPIC_BAR
        ),
        exercise(
            "row-dumbbell", "Dumbbell Row", MuscleGroup.BACK,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS)
        ),
        exercise(
            "row-seated-cable", "Seated Cable Row", MuscleGroup.BACK,
            ExerciseCategory.CABLE, listOf(MuscleGroup.LATS, MuscleGroup.BICEPS)
        ),
        exercise(
            "row-t-bar", "T-Bar Row", MuscleGroup.BACK,
            ExerciseCategory.MACHINE, listOf(MuscleGroup.LATS)
        ),
        exercise(
            "face-pull", "Face Pull", MuscleGroup.SHOULDERS,
            ExerciseCategory.CABLE, listOf(MuscleGroup.TRAPS, MuscleGroup.BACK)
        ),
        exercise(
            "shrug-barbell", "Barbell Shrug", MuscleGroup.TRAPS,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.FOREARMS), OLYMPIC_BAR
        ),
        exercise(
            "shrug-dumbbell", "Dumbbell Shrug", MuscleGroup.TRAPS, ExerciseCategory.DUMBBELL
        ),
        exercise(
            "hyperextension", "Back Extension", MuscleGroup.BACK,
            ExerciseCategory.WEIGHTED_BODYWEIGHT, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS)
        ),

        // ------------------------------------------------------ SHOULDERS
        exercise(
            "overhead-press-barbell", "Overhead Press (Barbell)", MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.TRICEPS), OLYMPIC_BAR,
            "Squeeze the glutes and brace hard. Move the head back out of the bar path, then push it forward under the bar at lockout."
        ),
        exercise(
            "overhead-press-dumbbell", "Shoulder Press (Dumbbell)", MuscleGroup.SHOULDERS,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.TRICEPS)
        ),
        exercise(
            "lateral-raise-dumbbell", "Lateral Raise", MuscleGroup.SHOULDERS, ExerciseCategory.DUMBBELL
        ),
        exercise(
            "lateral-raise-cable", "Cable Lateral Raise", MuscleGroup.SHOULDERS, ExerciseCategory.CABLE
        ),
        exercise(
            "rear-delt-fly", "Rear Delt Fly", MuscleGroup.SHOULDERS,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.BACK)
        ),
        exercise(
            "arnold-press", "Arnold Press", MuscleGroup.SHOULDERS,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.TRICEPS)
        ),
        exercise(
            "upright-row", "Upright Row", MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.TRAPS), OLYMPIC_BAR
        ),

        // ---------------------------------------------------------- ARMS
        exercise(
            "curl-barbell", "Barbell Curl", MuscleGroup.BICEPS,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.FOREARMS), OLYMPIC_BAR
        ),
        exercise(
            "curl-ez-bar", "EZ-Bar Curl", MuscleGroup.BICEPS,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.FOREARMS), EZ_BAR
        ),
        exercise(
            "curl-dumbbell", "Dumbbell Curl", MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.FOREARMS)
        ),
        exercise(
            "curl-hammer", "Hammer Curl", MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.FOREARMS)
        ),
        exercise(
            "curl-incline", "Incline Dumbbell Curl", MuscleGroup.BICEPS, ExerciseCategory.DUMBBELL
        ),
        exercise(
            "curl-preacher", "Preacher Curl", MuscleGroup.BICEPS, ExerciseCategory.MACHINE
        ),
        exercise(
            "curl-cable", "Cable Curl", MuscleGroup.BICEPS, ExerciseCategory.CABLE
        ),
        exercise(
            "triceps-pushdown", "Triceps Pushdown", MuscleGroup.TRICEPS, ExerciseCategory.CABLE
        ),
        exercise(
            "triceps-overhead-cable", "Overhead Triceps Extension", MuscleGroup.TRICEPS,
            ExerciseCategory.CABLE
        ),
        exercise(
            "skullcrusher", "Skullcrusher", MuscleGroup.TRICEPS, ExerciseCategory.BARBELL,
            emptyList(), EZ_BAR
        ),
        exercise(
            "dip-triceps", "Triceps Dip", MuscleGroup.TRICEPS,
            ExerciseCategory.WEIGHTED_BODYWEIGHT, listOf(MuscleGroup.CHEST)
        ),
        exercise(
            "close-grip-bench", "Close-Grip Bench Press", MuscleGroup.TRICEPS,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.CHEST), OLYMPIC_BAR
        ),
        exercise(
            "wrist-curl", "Wrist Curl", MuscleGroup.FOREARMS, ExerciseCategory.DUMBBELL
        ),
        exercise(
            "farmers-carry", "Farmer's Carry", MuscleGroup.FOREARMS,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.TRAPS, MuscleGroup.ABS)
        ),

        // ---------------------------------------------------------- LEGS
        exercise(
            "squat-back", "Back Squat", MuscleGroup.QUADS,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS), OLYMPIC_BAR,
            "Brace before unracking. Break at the hips and knees together, and keep the bar tracking over mid-foot throughout."
        ),
        exercise(
            "squat-front", "Front Squat", MuscleGroup.QUADS,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.GLUTES, MuscleGroup.ABS), OLYMPIC_BAR
        ),
        exercise(
            "squat-goblet", "Goblet Squat", MuscleGroup.QUADS,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.GLUTES)
        ),
        exercise(
            "leg-press", "Leg Press", MuscleGroup.QUADS,
            ExerciseCategory.MACHINE, listOf(MuscleGroup.GLUTES)
        ),
        exercise(
            "lunge-walking", "Walking Lunge", MuscleGroup.QUADS,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS)
        ),
        exercise(
            "bulgarian-split-squat", "Bulgarian Split Squat", MuscleGroup.QUADS,
            ExerciseCategory.DUMBBELL, listOf(MuscleGroup.GLUTES)
        ),
        exercise(
            "leg-extension", "Leg Extension", MuscleGroup.QUADS, ExerciseCategory.MACHINE
        ),
        exercise(
            "leg-curl-lying", "Lying Leg Curl", MuscleGroup.HAMSTRINGS, ExerciseCategory.MACHINE
        ),
        exercise(
            "leg-curl-seated", "Seated Leg Curl", MuscleGroup.HAMSTRINGS, ExerciseCategory.MACHINE
        ),
        exercise(
            "hip-thrust", "Hip Thrust", MuscleGroup.GLUTES,
            ExerciseCategory.BARBELL, listOf(MuscleGroup.HAMSTRINGS), OLYMPIC_BAR
        ),
        exercise(
            "glute-bridge", "Glute Bridge", MuscleGroup.GLUTES, ExerciseCategory.BODYWEIGHT
        ),
        exercise(
            "hip-abduction", "Hip Abduction", MuscleGroup.GLUTES, ExerciseCategory.MACHINE
        ),
        exercise(
            "calf-raise-standing", "Standing Calf Raise", MuscleGroup.CALVES, ExerciseCategory.MACHINE
        ),
        exercise(
            "calf-raise-seated", "Seated Calf Raise", MuscleGroup.CALVES, ExerciseCategory.MACHINE
        ),

        // ----------------------------------------------------------- CORE
        exercise(
            "plank", "Plank", MuscleGroup.ABS, ExerciseCategory.DURATION
        ),
        exercise(
            "hanging-leg-raise", "Hanging Leg Raise", MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT, listOf(MuscleGroup.FOREARMS)
        ),
        exercise(
            "cable-crunch", "Cable Crunch", MuscleGroup.ABS, ExerciseCategory.CABLE
        ),
        exercise(
            "russian-twist", "Russian Twist", MuscleGroup.ABS, ExerciseCategory.DUMBBELL
        ),
        exercise(
            "ab-wheel", "Ab Wheel Rollout", MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT, listOf(MuscleGroup.LATS)
        ),
        exercise(
            "sit-up", "Sit Up", MuscleGroup.ABS, ExerciseCategory.BODYWEIGHT
        ),
        exercise(
            "side-plank", "Side Plank", MuscleGroup.ABS, ExerciseCategory.DURATION
        ),

        // --------------------------------------------------------- CARDIO
        exercise(
            "run-outdoor", "Outdoor Run", MuscleGroup.CARDIO, ExerciseCategory.CARDIO
        ),
        exercise(
            "run-treadmill", "Treadmill Run", MuscleGroup.CARDIO, ExerciseCategory.CARDIO
        ),
        exercise(
            "cycling", "Cycling", MuscleGroup.CARDIO, ExerciseCategory.CARDIO
        ),
        exercise(
            "rowing-machine", "Rowing Machine", MuscleGroup.CARDIO,
            ExerciseCategory.CARDIO, listOf(MuscleGroup.BACK)
        ),
        exercise(
            "elliptical", "Elliptical", MuscleGroup.CARDIO, ExerciseCategory.CARDIO
        ),
        exercise(
            "stair-climber", "Stair Climber", MuscleGroup.CARDIO,
            ExerciseCategory.CARDIO, listOf(MuscleGroup.GLUTES)
        ),
        exercise(
            "jump-rope", "Jump Rope", MuscleGroup.CARDIO,
            ExerciseCategory.DURATION, listOf(MuscleGroup.CALVES)
        )
    )
}
