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
        ),

        // ------------------------------------------------- IMPORTED LIBRARY
        // Generated by tools/generate_exercise_seed.py from free-exercise-db
        // (github.com/yuhonas/free-exercise-db), which is public domain under
        // the Unlicense -- so these rows can ship inside the app with no
        // attribution or share-alike obligation attached to every copy.
        //
        // Hand-written entries above stay the canonical version of a lift; the
        // importer skips anything they already cover, so there is one Bench
        // Press rather than two spellings of it. Edit the script and re-run it
        // rather than editing below, or the next import will undo the change.
        exercise(
            "fx-barbell-ab-rollout",
            "Barbell Ab Rollout",
            MuscleGroup.ABS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "For this exercise you will need to get into a pushup position, but instead of having your hands of the floor, you will be grabbing on to an Olympic barbell (loaded with 5-10 lbs on each side) instead. This will be your starting position. While keeping a slight arch on your back, lift your hips and roll the barbell towards your feet as you exhale. Tip: As you perform the movement, your glutes shoul"
        ),
        exercise(
            "fx-barbell-rollout-from-bench",
            "Barbell Rollout from Bench",
            MuscleGroup.ABS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.LATS),
            OLYMPIC_BAR,
            "Place a loaded barbell on the ground, near the end of a bench. Kneel with both legs on the bench, and take a medium to narrow grip on the barbell. This will be your starting position. To begin, extend through the hips to slowly roll the bar forward. As you roll out, flex the shoulder to roll the bar above your head. Ensure that your arms remain extended throughout the movement."
        ),
        exercise(
            "fx-bent-knee-hip-raise",
            "Bent-Knee Hip Raise",
            MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT,
            emptyList(),
            null,
            "Lay flat on the floor with your arms next to your sides. Now bend your knees at around a 75 degree angle and lift your feet off the floor by around 2 inches."
        ),
        exercise(
            "fx-cable-judo-flip",
            "Cable Judo Flip",
            MuscleGroup.ABS,
            ExerciseCategory.CABLE,
            emptyList(),
            null,
            "Connect a rope attachment to a tower, and move the cable to the lowest pulley position. Stand with your side to the cable with a wide stance, and grab the rope with both hands. Twist your body away from the pulley as you bring the rope over your shoulder like you're performing a judo flip."
        ),
        exercise(
            "fx-cable-russian-twists",
            "Cable Russian Twists",
            MuscleGroup.ABS,
            ExerciseCategory.CABLE,
            emptyList(),
            null,
            "Connect a standard handle attachment, and position the cable to a middle pulley position. Lie on a stability ball perpendicular to the cable and grab the handle with one hand. You should be approximately arm's length away from the pulley, with the tension of the weight on the cable."
        ),
        exercise(
            "fx-cross-body-crunch",
            "Cross-Body Crunch",
            MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT,
            emptyList(),
            null,
            "Lie flat on your back and bend your knees about 60 degrees. Keep your feet flat on the floor and place your hands loosely behind your head. This will be your starting position."
        ),
        exercise(
            "fx-decline-oblique-crunch",
            "Decline Oblique Crunch",
            MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT,
            emptyList(),
            null,
            "Secure your legs at the end of the decline bench and slowly lay down on the bench. Raise your upper body off the bench until your torso is about 35-45 degrees if measured from the floor."
        ),
        exercise(
            "fx-decline-reverse-crunch",
            "Decline Reverse Crunch",
            MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT,
            emptyList(),
            null,
            "Lie on your back on a decline bench and hold on to the top of the bench with both hands. Don't let your body slip down from this position. Hold your legs parallel to the floor using your abs to hold them there while keeping your knees and feet together. Tip: Your legs should be fully extended with a slight bend on the knee. This will be your starting position."
        ),
        exercise(
            "fx-elbow-to-knee",
            "Elbow to Knee",
            MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT,
            emptyList(),
            null,
            "Lie on the floor, crossing your right leg across your bent left knee. Clasp your hands behind your head, beginning with your shoulder blades on the ground. This will be your starting position. Perform the motion by flexing the spine and rotating your torso to bring the left elbow to the right knee."
        ),
        exercise(
            "fx-gorilla-chin-crunch",
            "Gorilla Chin/Crunch",
            MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS),
            null,
            "Hang from a chin-up bar using an underhand grip (palms facing you) that is slightly wider than shoulder width. Now bend your knees at a 90 degree angle so that the calves are parallel to the floor while the thighs remain perpendicular to it. This will be your starting position."
        ),
        exercise(
            "fx-jackknife-sit-up",
            "Jackknife Sit-Up",
            MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT,
            emptyList(),
            null,
            "Lie flat on the floor (or exercise mat) on your back with your arms extended straight back behind your head and your legs extended also. This will be your starting position. As you exhale, bend at the waist while simultaneously raising your legs and arms to meet in a jackknife position. Tip: The legs should be extended and lifted at approximately a 35-45 degree angle from the floor and the arms sh"
        ),
        exercise(
            "fx-leg-pull-in",
            "Leg Pull-In",
            MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT,
            emptyList(),
            null,
            "Lie on an exercise mat with your legs extended and your hands either palms facing down next to you or under your glutes. Tip: My preference is with the hands next to me. This will be your starting position. Bend your knees and pull your upper thighs into your midsection as you breathe out. Continue the motion until your knees are around chest level. Contract your abs as you execute this movement a"
        ),
        exercise(
            "fx-side-jackknife",
            "Side Jackknife",
            MuscleGroup.ABS,
            ExerciseCategory.BODYWEIGHT,
            emptyList(),
            null,
            ""
        ),
        exercise(
            "fx-standing-cable-lift",
            "Standing Cable Lift",
            MuscleGroup.ABS,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.SHOULDERS),
            null,
            "Connect a standard handle on a tower, and move the cable to the lowest pulley position. With your side to the cable, grab the handle with one hand and step away from the tower. You should be approximately arm's length away from the pulley, with the tension of the weight on the cable. Your outstretched arm should be aligned with the cable."
        ),
        exercise(
            "fx-bent-over-barbell-row",
            "Bent Over Barbell Row",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Holding a barbell with a pronated grip (palms facing down), bend your knees slightly and bring your torso forward, by bending at the waist, while keeping the back straight until it is almost parallel to the floor. Tip: Make sure that you keep the head up. The barbell should hang directly in front of you as your arms hang perpendicular to the floor and your torso. This is your starting position. No"
        ),
        exercise(
            "fx-bent-over-one-arm-long-bar-row",
            "Bent Over One-Arm Long Bar Row",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS, MuscleGroup.TRAPS),
            OLYMPIC_BAR,
            "Put weight on one of the ends of an Olympic barbell. Make sure that you either place the other end of the barbell in the corner of two walls; or put a heavy object on the ground so the barbell cannot slide backward. Bend forward until your torso is as close to parallel with the floor as you can and keep your knees slightly bent."
        ),
        exercise(
            "fx-bent-over-two-arm-long-bar-row",
            "Bent Over Two-Arm Long Bar Row",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS),
            OLYMPIC_BAR,
            "Put weight on one of the ends of an Olympic barbell. Make sure that you either place the other end of the barbell in the corner of two walls; or put a heavy object on the ground so the barbell cannot slide backward. Bend forward until your torso is as close to parallel with the floor as you can and keep your knees slightly bent."
        ),
        exercise(
            "fx-bent-over-two-dumbbell-row",
            "Bent Over Two-Dumbbell Row",
            MuscleGroup.BACK,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS, MuscleGroup.SHOULDERS),
            null,
            "With a dumbbell in each hand (palms facing your torso), bend your knees slightly and bring your torso forward by bending at the waist; as you bend make sure to keep your back straight until it is almost parallel to the floor. Tip: Make sure that you keep the head up. The weights should hang directly in front of you as your arms hang perpendicular to the floor and your torso. This is your starting "
        ),
        exercise(
            "fx-deficit-deadlift",
            "Deficit Deadlift",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "Begin by having a platform or weight plates that you can stand on, usually 1-3 inches in height. Approach the bar so that it is centered over your feet. You feet should be about hip width apart. Bend at the hip to grip the bar at shoulder width, allowing your shoulder blades to protract. Typically, you would use an overhand grip or an over/under grip on heavier sets. With your feet, and your grip "
        ),
        exercise(
            "fx-dumbbell-incline-row",
            "Dumbbell Incline Row",
            MuscleGroup.BACK,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.LATS),
            null,
            "Using a neutral grip, lean into an incline bench. Take a dumbbell in each hand with a neutral grip, beginning with the arms straight. This will be your starting position."
        ),
        exercise(
            "fx-one-arm-dumbbell-row",
            "One-Arm Dumbbell Row",
            MuscleGroup.BACK,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS, MuscleGroup.SHOULDERS),
            null,
            "Choose a flat bench and place a dumbbell on each side of it. Place the right leg on top of the end of the bench, bend your torso forward from the waist until your upper body is parallel to the floor, and place your right hand on the other end of the bench for support."
        ),
        exercise(
            "fx-one-arm-long-bar-row",
            "One-Arm Long Bar Row",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS),
            OLYMPIC_BAR,
            "Position a bar into a landmine or in a corner to keep it from moving. Load an appropriate weight onto your end. Stand next to the bar, and take a grip with one hand close to the collar. Using your hips and legs, rise to a standing position."
        ),
        exercise(
            "fx-rack-pulls",
            "Rack Pulls",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "Set up in a power rack with the bar on the pins. The pins should be set to the desired point; just below the knees, just above, or in the mid thigh position. Position yourself against the bar in proper deadlifting position. Your feet should be under your hips, your grip shoulder width, back arched, and hips back to engage the hamstrings. Since the weight is typically heavy, you may use a mixed gri"
        ),
        exercise(
            "fx-reverse-grip-bent-over-rows",
            "Reverse Grip Bent-Over Rows",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Stand erect while holding a barbell with a supinated grip (palms facing up). Bend your knees slightly and bring your torso forward, by bending at the waist, while keeping the back straight until it is almost parallel to the floor. Tip: Make sure that you keep the head up. The barbell should hang directly in front of you as your arms hang perpendicular to the floor and your torso. This is your star"
        ),
        exercise(
            "fx-seated-good-mornings",
            "Seated Good Mornings",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES),
            OLYMPIC_BAR,
            "Set up a box in a power rack. The pins should be set at an appropriate height. Begin by stepping under the bar and placing it across the back of the shoulders, not on top of your traps. Squeeze your shoulder blades together and rotate your elbows forward, attempting to bend the bar across your shoulders. Remove the bar from the rack, creating a tight arch in your lower back. Keep your head facing "
        ),
        exercise(
            "fx-stiff-leg-barbell-good-morning",
            "Stiff Leg Barbell Good Morning",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "This exercise is best performed inside a squat rack for safety purposes. To begin, first set the bar on a rack that best matches your height. Once the correct height is chosen and the bar is loaded, step under the bar and place the back of your shoulders (slightly below the neck) across it. Hold on to the bar using both arms at each side and lift it off the rack by first pushing with your legs and"
        ),
        exercise(
            "fx-straight-bar-bench-mid-rows",
            "Straight Bar Bench Mid Rows",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS),
            OLYMPIC_BAR,
            "Place a loaded barbell on the end of a bench. Standing on the bench behind the bar, take a medium, pronated grip. Stand with your hips back and chest up, maintaining a neutral spine. This will be your starting position. Row the bar to your torso by retracting the shoulder blades and flexing the elbows. Use a controlled movement with no jerking."
        ),
        exercise(
            "fx-t-bar-row-with-handle",
            "T-Bar Row with Handle",
            MuscleGroup.BACK,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS),
            OLYMPIC_BAR,
            "Position a bar into a landmine or in a corner to keep it from moving. Load an appropriate weight onto your end. Stand over the bar, and position a Double D row handle around the bar next to the collar. Using your hips and legs, rise to a standing position."
        ),
        exercise(
            "fx-alternate-hammer-curl",
            "Alternate Hammer Curl",
            MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.FOREARMS),
            null,
            "Stand up with your torso upright and a dumbbell in each hand being held at arms length. The elbows should be close to the torso. The palms of the hands should be facing your torso. This will be your starting position."
        ),
        exercise(
            "fx-close-grip-ez-bar-curl",
            "Close-Grip EZ Bar Curl",
            MuscleGroup.BICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS),
            OLYMPIC_BAR,
            "Stand up with your torso upright while holding an E-Z Curl Bar at the closer inner handle. The palm of your hands should be facing forward and they should be slightly tilted inwards due to the shape of the bar. The elbows should be close to the torso. This will be your starting position. While holding the upper arms stationary, curl the weights forward while contracting the biceps as you breathe o"
        ),
        exercise(
            "fx-concentration-curls",
            "Concentration Curls",
            MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.FOREARMS),
            null,
            "Sit down on a flat bench with one dumbbell in front of you between your legs. Your legs should be spread with your knees bent and feet on the floor. Use your right arm to pick the dumbbell up. Place the back of your right upper arm on the top of your inner right thigh. Rotate the palm of your hand until it is facing forward away from your thigh. Tip: Your arm should be extended and the dumbbell sh"
        ),
        exercise(
            "fx-drag-curl",
            "Drag Curl",
            MuscleGroup.BICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS),
            OLYMPIC_BAR,
            "Grab a barbell with a supinated grip (palms facing forward) and get your elbows close to your torso and back. This will be your starting position. As you exhale, curl the bar up while keeping the elbows to the back as you \"Drag\" the bar up by keeping it in contact with your torso. Tip: As you can see, you will not be keeping the elbows pinned to your sides, but instead you will be bringing them ba"
        ),
        exercise(
            "fx-dumbbell-bicep-curl",
            "Dumbbell Bicep Curl",
            MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.FOREARMS),
            null,
            "Stand up straight with a dumbbell in each hand at arm's length. Keep your elbows close to your torso and rotate the palms of your hands until they are facing forward. This will be your starting position. Now, keeping the upper arms stationary, exhale and curl the weights while contracting your biceps. Continue to raise the weights until your biceps are fully contracted and the dumbbells are at sho"
        ),
        exercise(
            "fx-hammer-curls",
            "Hammer Curls",
            MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL,
            emptyList(),
            null,
            "Stand up with your torso upright and a dumbbell on each hand being held at arms length. The elbows should be close to the torso. The palms of the hands should be facing your torso. This will be your starting position."
        ),
        exercise(
            "fx-high-cable-curls",
            "High Cable Curls",
            MuscleGroup.BICEPS,
            ExerciseCategory.CABLE,
            emptyList(),
            null,
            "Stand between a couple of high pulleys and grab a handle in each arm. Position your upper arms in a way that they are parallel to the floor with the palms of your hands facing you. This will be your starting position. Curl the handles towards you until they are next to your ears. Make sure that as you do so you flex your biceps and exhale. The upper arms should remain stationary and only the forea"
        ),
        exercise(
            "fx-incline-hammer-curls",
            "Incline Hammer Curls",
            MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL,
            emptyList(),
            null,
            "Seat yourself on an incline bench with a dumbbell in each hand. You should pressed firmly against he back with your feet together. Allow the dumbbells to hang straight down at your side, holding them with a neutral grip. This will be your starting position. Initiate the movement by flexing at the elbow, attempting to keep the upper arm stationary."
        ),
        exercise(
            "fx-reverse-barbell-curl",
            "Reverse Barbell Curl",
            MuscleGroup.BICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS),
            OLYMPIC_BAR,
            "Stand up with your torso upright while holding a barbell at shoulder width with the elbows close to the torso. The palm of your hands should be facing down (pronated grip). This will be your starting position. While holding the upper arms stationary, curl the weights while contracting the biceps as you breathe out. Only the forearms should move. Continue the movement until your biceps are fully co"
        ),
        exercise(
            "fx-seated-dumbbell-curl",
            "Seated Dumbbell Curl",
            MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL,
            emptyList(),
            null,
            "Sit on a flat bench with a dumbbell on each hand being held at arms length. The elbows should be close to the torso. Rotate the palms of the hands so that they are facing your torso. This will be your starting position."
        ),
        exercise(
            "fx-zottman-curl",
            "Zottman Curl",
            MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.FOREARMS),
            null,
            "Stand up with your torso upright and a dumbbell in each hand being held at arms length. The elbows should be close to the torso. Make sure the palms of the hands are facing each other. This will be your starting position."
        ),
        exercise(
            "fx-zottman-preacher-curl",
            "Zottman Preacher Curl",
            MuscleGroup.BICEPS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.FOREARMS),
            null,
            "Grab a dumbbell in each hand and place your upper arms on top of the preacher bench or the incline bench. The dumbbells should be held at shoulder height and the elbows should be flexed. Hold the dumbbells with the palms of your hands facing down. This will be your starting position. As you breathe in, slowly lower the dumbbells keeping the palms down until your upper arm is extended and your bice"
        ),
        exercise(
            "fx-calf-press",
            "Calf Press",
            MuscleGroup.CALVES,
            ExerciseCategory.MACHINE,
            emptyList(),
            null,
            "Adjust the seat so that your legs are only slightly bent in the start position. The balls of your feet should be firmly on the platform. Select an appropriate weight, and grasp the handles. This will be your starting position."
        ),
        exercise(
            "fx-calf-raise-on-a-dumbbell",
            "Calf Raise On A Dumbbell",
            MuscleGroup.CALVES,
            ExerciseCategory.DUMBBELL,
            emptyList(),
            null,
            "Hang on to a sturdy object for balance and stand on a dumbbell handle, preferably one with round plates so that it rolls as in this manner you have to work harder to stabilize yourself; thus increasing the effectiveness of the exercise. Now roll your foot slightly forward so that you can get a nice stretch of the calf. This will be your starting position."
        ),
        exercise(
            "fx-dumbbell-seated-one-leg-calf-raise",
            "Dumbbell Seated One-Leg Calf Raise",
            MuscleGroup.CALVES,
            ExerciseCategory.DUMBBELL,
            emptyList(),
            null,
            "Place a block on the floor about 12 inches from a flat bench. Sit on a flat bench and place a dumbbell on your upper left thigh about 3 inches above your knee."
        ),
        exercise(
            "fx-smith-machine-calf-raise",
            "Smith Machine Calf Raise",
            MuscleGroup.CALVES,
            ExerciseCategory.MACHINE,
            emptyList(),
            null,
            "Place a block or weight plate below the bar on the Smith machine. Set the bar to a position that best matches your height. Once the correct height is chosen and the bar is loaded, step onto the plates with the balls of your feet and place the bar on the back of your shoulders. Take the bar with both hands facing forward. Rotate the bar to unrack it. This will be your starting position."
        ),
        exercise(
            "fx-smith-machine-reverse-calf-raises",
            "Smith Machine Reverse Calf Raises",
            MuscleGroup.CALVES,
            ExerciseCategory.MACHINE,
            emptyList(),
            null,
            "Adjust the barbell on the smith machine to fit your height and align a raised platform right under the bar. Stand on the platform with the heels of your feet secured on top of it with the balls of your feet extending off it. Position your toes facing forward with a shoulder width stance."
        ),
        exercise(
            "fx-standing-calf-raises",
            "Standing Calf Raises",
            MuscleGroup.CALVES,
            ExerciseCategory.MACHINE,
            emptyList(),
            null,
            "Adjust the padded lever of the calf raise machine to fit your height. Place your shoulders under the pads provided and position your toes facing forward (or using any of the two other positions described at the beginning of the chapter). The balls of your feet should be secured on top of the calf block with the heels extending off it. Push the lever up by extending your hips and knees until your t"
        ),
        exercise(
            "fx-around-the-worlds",
            "Around The Worlds",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.SHOULDERS),
            null,
            "Lay down on a flat bench holding a dumbbell in each hand with the palms of the hands facing towards the ceiling. Tip: Your arms should be parallel to the floor and next to your thighs. To avoid injury, make sure that you keep your elbows slightly bent. This will be your starting position. Now move the dumbbells by creating a semi-circle as you displace them from the initial position to over the he"
        ),
        exercise(
            "fx-bent-arm-dumbbell-pullover",
            "Bent-Arm Dumbbell Pullover",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.LATS, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            null,
            "Place a dumbbell standing up on a flat bench. Ensuring that the dumbbell stays securely placed at the top of the bench, lie perpendicular to the bench (torso across it as in forming a cross) with only your shoulders lying on the surface. Hips should be below the bench and legs bent with feet firmly on the floor. The head will be off the bench as well."
        ),
        exercise(
            "fx-decline-barbell-bench-press",
            "Decline Barbell Bench Press",
            MuscleGroup.CHEST,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Secure your legs at the end of the decline bench and slowly lay down on the bench. Using a medium width grip (a grip that creates a 90-degree angle in the middle of the movement between the forearms and the upper arms), lift the bar from the rack and hold it straight over you with your arms locked. The arms should be perpendicular to the floor. This will be your starting position. Tip: In order to"
        ),
        exercise(
            "fx-decline-dumbbell-flyes",
            "Decline Dumbbell Flyes",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            emptyList(),
            null,
            "Secure your legs at the end of the decline bench and lie down with a dumbbell on each hand on top of your thighs. The palms of your hand will be facing each other. Once you are laying down, move the dumbbells in front of you at shoulder width. The palms of the hands should be facing each other and the arms should be perpendicular to the floor and fully extended. This will be your starting position"
        ),
        exercise(
            "fx-dumbbell-bench-press-with-neutral-grip",
            "Dumbbell Bench Press with Neutral Grip",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            null,
            "Take a dumbbell in each hand and lay back onto a flat bench. Your feet should be flat on the floor and your shoulder blades retracted. Maintaining a neutral grip, palms facing each other, begin with your arms extended directly above you, perpendicular to the floor. This will be your starting position."
        ),
        exercise(
            "fx-front-raise-and-pullover",
            "Front Raise And Pullover",
            MuscleGroup.CHEST,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.LATS, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Lie on a flat bench while holding a barbell using a palms down grip that is about 15 inches apart. Place the bar on your upper thighs, extend your arms and lock them while keeping a slight bend on the elbows. This will be your starting position."
        ),
        exercise(
            "fx-hammer-grip-incline-db-bench-press",
            "Hammer Grip Incline DB Bench Press",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            null,
            "Lie back on an incline bench with a dumbbell on each hand on top of your thighs. The palms of your hand will be facing each other. By using your thighs to help you get the dumbbells up, clean the dumbbells one arm at a time so that you can hold them at shoulder width."
        ),
        exercise(
            "fx-incline-dumbbell-bench-with-palms-facing-in",
            "Incline Dumbbell Bench With Palms Facing In",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            null,
            "Lie back on an incline bench with a dumbbell on each hand on top of your thighs. The palms of your hand will be facing each other. By using your thighs to help you get the dumbbells up, clean the dumbbells one arm at a time so that you can hold them at shoulder width."
        ),
        exercise(
            "fx-incline-dumbbell-flyes",
            "Incline Dumbbell Flyes",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.SHOULDERS),
            null,
            "Hold a dumbbell on each hand and lie on an incline bench that is set to an incline angle of no more than 30 degrees. Extend your arms above you with a slight bend at the elbows."
        ),
        exercise(
            "fx-incline-dumbbell-press",
            "Incline Dumbbell Press",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            null,
            "Lie back on an incline bench with a dumbbell in each hand atop your thighs. The palms of your hands will be facing each other. Then, using your thighs to help push the dumbbells up, lift the dumbbells one at a time so that you can hold them at shoulder width."
        ),
        exercise(
            "fx-neck-press",
            "Neck Press",
            MuscleGroup.CHEST,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Lie back on a flat bench. Using a medium-width grip (a grip that creates a 90-degree angle in the middle of the movement between the forearms and the upper arms), lift the bar from the rack and hold it straight over your neck with your arms locked. This will be your starting position. As you breathe in, come down slowly until you feel the bar on your neck."
        ),
        exercise(
            "fx-one-arm-dumbbell-bench-press",
            "One Arm Dumbbell Bench Press",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            null,
            "Lie down on a flat bench with a dumbbell in one hand on top of your thigh. By using your thigh to help you get the dumbbell up, clean the dumbbell up so that you can hold it in front of you at shoulder width. Use the hand you are not lifting with to help position the dumbbell over you properly."
        ),
        exercise(
            "fx-straight-arm-dumbbell-pullover",
            "Straight-Arm Dumbbell Pullover",
            MuscleGroup.CHEST,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.LATS, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            null,
            "Place a dumbbell standing up on a flat bench. Ensuring that the dumbbell stays securely placed at the top of the bench, lie perpendicular to the bench (torso across it as in forming a cross) with only your shoulders lying on the surface. Hips should be below the bench and legs bent with feet firmly on the floor. The head will be off the bench as well."
        ),
        exercise(
            "fx-wide-grip-barbell-bench-press",
            "Wide-Grip Barbell Bench Press",
            MuscleGroup.CHEST,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Lie back on a flat bench with feet firm on the floor. Using a wide, pronated (palms forward) grip that is around 3 inches away from shoulder width (for each hand), lift the bar from the rack and hold it straight over you with your arms locked. The bar will be perpendicular to the torso and the floor. This will be your starting position. As you breathe in, come down slowly until you feel the bar on"
        ),
        exercise(
            "fx-wide-grip-decline-barbell-bench-press",
            "Wide-Grip Decline Barbell Bench Press",
            MuscleGroup.CHEST,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Lie back on a decline bench with the feet securely locked at the front of the bench. Using a wide, pronated (palms forward) grip that is around 3 inches away from shoulder width (for each hand), lift the bar from the rack and hold it straight over you with your arms locked. The bar will be perpendicular to the torso and the floor. This will be your starting position. As you breathe in, come down s"
        ),
        exercise(
            "fx-wide-grip-decline-barbell-pullover",
            "Wide-Grip Decline Barbell Pullover",
            MuscleGroup.CHEST,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Lie down on a decline bench with both legs securely locked in position. Reach for the barbell behind the head using a pronated grip (palms facing out). Make sure to grab the barbell wider than shoulder width apart for this exercise. Slowly lift the barbell up from the floor by using your arms. When positioned properly, your arms should be fully extended and perpendicular to the floor. This is the "
        ),
        exercise(
            "fx-dumbbell-lying-pronation",
            "Dumbbell Lying Pronation",
            MuscleGroup.FOREARMS,
            ExerciseCategory.DUMBBELL,
            emptyList(),
            null,
            "Lie on a flat bench face down with one arm holding a dumbbell and the other hand on top of the bench folded so that you can rest your head on it. Bend the elbows of the arm holding the dumbbell so that it creates a 90-degree angle between the upper arm and the forearm."
        ),
        exercise(
            "fx-dumbbell-lying-supination",
            "Dumbbell Lying Supination",
            MuscleGroup.FOREARMS,
            ExerciseCategory.DUMBBELL,
            emptyList(),
            null,
            "Lie sideways on a flat bench with one arm holding a dumbbell and the other hand on top of the bench folded so that you can rest your head on it. Bend the elbows of the arm holding the dumbbell so that it creates a 90-degree angle between the upper arm and the forearm."
        ),
        exercise(
            "fx-finger-curls",
            "Finger Curls",
            MuscleGroup.FOREARMS,
            ExerciseCategory.BARBELL,
            emptyList(),
            OLYMPIC_BAR,
            "Hold a barbell with both hands and your palms facing up; hands spaced about shoulder width. Place your feet flat on the floor, at a distance that is slightly wider than shoulder width apart. This will be your starting position."
        ),
        exercise(
            "fx-palms-down-wrist-curl-over-a-bench",
            "Palms-Down Wrist Curl Over A Bench",
            MuscleGroup.FOREARMS,
            ExerciseCategory.BARBELL,
            emptyList(),
            OLYMPIC_BAR,
            "Start out by placing a barbell on one side of a flat bench. Kneel down on both of your knees so that your body is facing the flat bench."
        ),
        exercise(
            "fx-seated-palm-up-barbell-wrist-curl",
            "Seated Palm-Up Barbell Wrist Curl",
            MuscleGroup.FOREARMS,
            ExerciseCategory.BARBELL,
            emptyList(),
            OLYMPIC_BAR,
            "Hold a barbell with both hands and your palms facing up; hands spaced about shoulder width. Place your feet flat on the floor, at a distance that is slightly wider than shoulder width apart."
        ),
        exercise(
            "fx-wrist-rotations-with-straight-bar",
            "Wrist Rotations with Straight Bar",
            MuscleGroup.FOREARMS,
            ExerciseCategory.BARBELL,
            emptyList(),
            OLYMPIC_BAR,
            "Hold a barbell with both hands and your palms facing down; hands spaced about shoulder width. This will be your starting position. Alternating between each of your hands, perform the movement by extending the wrist as though you were rolling up a newspaper. Continue alternating back and forth until failure."
        ),
        exercise(
            "fx-butt-lift-bridge",
            "Butt Lift (Bridge)",
            MuscleGroup.GLUTES,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.HAMSTRINGS),
            null,
            "Lie flat on the floor on your back with the hands by your side and your knees bent. Your feet should be placed around shoulder width. This will be your starting position. Pushing mainly with your heels, lift your hips off the floor while keeping your back straight. Breathe out as you perform this part of the motion and hold at the top for a second."
        ),
        exercise(
            "fx-flutter-kicks",
            "Flutter Kicks",
            MuscleGroup.GLUTES,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.HAMSTRINGS),
            null,
            "On a flat bench lie facedown with the hips on the edge of the bench, the legs straight with toes high off the floor and with the arms on top of the bench holding on to the front edge. Squeeze your glutes and hamstrings and straighten the legs until they are level with the hips. This will be your starting position."
        ),
        exercise(
            "fx-glute-kickback",
            "Glute Kickback",
            MuscleGroup.GLUTES,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.HAMSTRINGS),
            null,
            "Kneel on the floor or an exercise mat and bend at the waist with your arms extended in front of you (perpendicular to the torso) in order to get into a kneeling push-up position but with the arms spaced at shoulder width. Your head should be looking forward and the bend of the knees should create a 90-degree angle between the hamstrings and the calves. This will be your starting position. As you e"
        ),
        exercise(
            "fx-kneeling-jump-squat",
            "Kneeling Jump Squat",
            MuscleGroup.GLUTES,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS),
            OLYMPIC_BAR,
            "Begin kneeling on the floor with a barbell racked across the back of your shoulders, or you can use your body weight for this exercise. This can be done inside of a power rack to make unracking easier. Sit back with your hips until your glutes touch your feet, keeping your head and chest up."
        ),
        exercise(
            "fx-kneeling-squat",
            "Kneeling Squat",
            MuscleGroup.GLUTES,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.ABS, MuscleGroup.HAMSTRINGS, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "Set the bar to the proper height in a power rack. Kneel behind the bar; it may be beneficial to put a mat down to pad your knees. Slide under the bar, racking it across the back of your shoulders. Your shoulder blades should be retracted and the bar tight across your back. Unrack the weight. With your head looking forward, sit back with your butt until you touch your calves."
        ),
        exercise(
            "fx-leg-lift",
            "Leg Lift",
            MuscleGroup.GLUTES,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.HAMSTRINGS),
            null,
            "While standing up straight with both feet next to each other at around shoulder width, grab a sturdy surface such as the sides of a squat rack or the top of a chair to brace yourself and keep balance. With or without an ankle weight, lift one leg behind you as if performing a leg curl but standing up while keeping the other leg straight. Breathe out as you perform this movement."
        ),
        exercise(
            "fx-pull-through",
            "Pull Through",
            MuscleGroup.GLUTES,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.BACK),
            null,
            "Begin standing a few feet in front of a low pulley with a rope or handle attached. Face away from the machine, straddling the cable, with your feet set wide apart. Begin the movement by reaching through your legs as far as possible, bending at the hips. Keep your knees slightly bent. Keeping your arms straight, extend through the hip to stand straight up. Avoid pulling upward through the shoulders"
        ),
        exercise(
            "fx-step-up-with-knee-raise",
            "Step-up with Knee Raise",
            MuscleGroup.GLUTES,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS),
            null,
            "Stand facing a box or bench of an appropriate height with your feet together. This will be your starting position. Begin the movement by stepping up, putting your left foot on the top of the bench. Extend through the hip and knee of your front leg to stand up on the box. As you stand on the box with your left leg, flex your right knee and hip, bringing your knee as high as you can."
        ),
        exercise(
            "fx-thigh-abductor",
            "Thigh Abductor",
            MuscleGroup.GLUTES,
            ExerciseCategory.MACHINE,
            emptyList(),
            null,
            "To begin, sit down on the abductor machine and select a weight you are comfortable with. When your legs are positioned properly, grip the handles on each side. Your entire upper body (from the waist up) should be stationary. This is the starting position. Slowly press against the machine with your legs to move them away from each other while exhaling."
        ),
        exercise(
            "fx-thigh-adductor",
            "Thigh Adductor",
            MuscleGroup.GLUTES,
            ExerciseCategory.MACHINE,
            listOf(MuscleGroup.HAMSTRINGS),
            null,
            "To begin, sit down on the adductor machine and select a weight you are comfortable with. When your legs are positioned properly on the leg pads of the machine, grip the handles on each side. Your entire upper body (from the waist up) should be stationary. This is the starting position. Slowly press against the machine with your legs to move them towards each other while exhaling."
        ),
        exercise(
            "fx-clean",
            "Clean",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.FOREARMS, MuscleGroup.GLUTES),
            OLYMPIC_BAR,
            "With a barbell on the floor close to the shins, take an overhand (or hook) grip just outside the legs. Lower your hips with the weight focused on the heels, back straight, head facing forward, chest up, with your shoulders just in front of the bar. This will be your starting position. Begin the first pull by driving through the heels, extending your knees. Your back angle should stay the same, and"
        ),
        exercise(
            "fx-clean-deadlift",
            "Clean Deadlift",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS, MuscleGroup.GLUTES, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "Begin standing with a barbell close to your shins. Your feet should be directly under your hips with your feet turned out slightly. Grip the bar with a double overhand grip or hook grip, about shoulder width apart. Squat down to the bar. Your spine should be in full extension, with a back angle that places your shoulders in front of the bar and your back as vertical as possible. Begin by driving t"
        ),
        exercise(
            "fx-good-morning",
            "Good Morning",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.ABS, MuscleGroup.GLUTES, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "Begin with a bar on a rack at shoulder height. Rack the bar across the rear of your shoulders as you would a power squat, not on top of your shoulders. Keep your back tight, shoulder blades pinched together, and your knees slightly bent. Step back from the rack. Begin by bending at the hips, moving them back as you bend over to near parallel. Keep your back arched and your cervical spine in proper"
        ),
        exercise(
            "fx-hanging-bar-good-morning",
            "Hanging Bar Good Morning",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.ABS, MuscleGroup.GLUTES, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "Begin with a bar on a rack at about the same height as your stomach. Suspend the bar using chains or suspension straps. Bend over underneath the bar and rack the bar across the rear of your shoulders as you would a power squat, not on top of your traps. At the proper height, you should be near parallel to the floor when bent over. Keep your back tight, shoulder blades pinched together, and your kn"
        ),
        exercise(
            "fx-muscle-snatch",
            "Muscle Snatch",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.BACK, MuscleGroup.QUADS),
            OLYMPIC_BAR,
            "Begin with a loaded barbell held at the mid thigh position with a wide grip. The feet should be directly below the hips, with the feet turned out as needed. Lower the hips, with the chest up and the head looking forward. The shoulders should be just in front of the bar. This will be the starting position. Begin the pull by driving through the front of the heels, raising the bar. Transition into th"
        ),
        exercise(
            "fx-power-clean",
            "Power Clean",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.FOREARMS, MuscleGroup.GLUTES),
            OLYMPIC_BAR,
            "Stand with your feet slightly wider than shoulder width apart and toes pointing out slightly. Squat down and grasp bar with a closed, pronated grip. Your hands should be slightly wider than shoulder width apart outside knees with elbows fully extended."
        ),
        exercise(
            "fx-power-clean-from-blocks",
            "Power Clean from Blocks",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.QUADS),
            OLYMPIC_BAR,
            "With a barbell on boxes of the desired height, take a grip just outside the legs. Lower your hips with the weight focused on the heels, back straight, head facing forward, chest up, with your shoulders just in front of the bar. This will be your starting position. Begin the first pull by driving through the heels, extending your knees. Your back angle should stay the same, and your arms should rem"
        ),
        exercise(
            "fx-snatch-deadlift",
            "Snatch Deadlift",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS, MuscleGroup.GLUTES, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "The snatch deadlift strengthens the first pull of the snatch. Begin with a wide snatch grip with the barbell placed on the platform. The feet should be directly under the hips, with the feet turned out. Squat down to the bar, keeping the back in absolute extension with the head facing forward. Initiate the movement by driving through the heels, raising the hips. The back angle should remain the sa"
        ),
        exercise(
            "fx-snatch-pull",
            "Snatch Pull",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "With a barbell on the floor close to the shins, take a wide snatch grip. Lower your hips with the weight focused on the heels, back straight, head facing forward, chest up, with your shoulders just in front of the bar. This will be your starting position. Begin the first pull by driving through the heels, extending your knees. Your back angle should stay the same, and your arms should remain strai"
        ),
        exercise(
            "fx-stiff-legged-barbell-deadlift",
            "Stiff-Legged Barbell Deadlift",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "Grasp a bar using an overhand grip (palms facing down). You may need some wrist wraps if using a significant amount of weight. Stand with your torso straight and your legs spaced using a shoulder width or narrower stance. The knees should be slightly bent. This is your starting position."
        ),
        exercise(
            "fx-sumo-deadlift",
            "Sumo Deadlift",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.FOREARMS, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "Begin with a bar loaded on the ground. Approach the bar so that the bar intersects the middle of the feet. The feet should be set very wide, near the collars. Bend at the hips to grip the bar. The arms should be directly below the shoulders, inside the legs, and you can use a pronated grip, a mixed grip, or hook grip. Relax the shoulders, which in effect lengthens your arms. Take a breath, and the"
        ),
        exercise(
            "fx-wide-stance-stiff-legs",
            "Wide Stance Stiff Legs",
            MuscleGroup.HAMSTRINGS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "Begin with a barbell loaded on the floor. Adopt a wide stance, and then bend at the hips to grab the bar. Your hips should be as far back as possible, and your legs nearly straight. Keep your back straight, and your head and chest up. This will be your starting position. Begin the movement be engaging the hips, driving them forward as you allow the arms to hang straight. Continue until you are sta"
        ),
        exercise(
            "fx-close-grip-front-lat-pulldown",
            "Close-Grip Front Lat Pulldown",
            MuscleGroup.LATS,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            null,
            "Sit down on a pull-down machine with a wide bar attached to the top pulley. Make sure that you adjust the knee pad of the machine to fit your height. These pads will prevent your body from being raised by the resistance attached to the bar. Grab the bar with the palms facing forward using the prescribed grip. Note on grips: For a wide grip, your hands need to be spaced out at a distance wider than"
        ),
        exercise(
            "fx-elevated-cable-rows",
            "Elevated Cable Rows",
            MuscleGroup.LATS,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.BACK, MuscleGroup.TRAPS),
            null,
            "Get a platform of some sort (it can be an aerobics or calf raise platform) that is around 4-6 inches in height. Place it on the seat of the cable row machine."
        ),
        exercise(
            "fx-kneeling-high-pulley-row",
            "Kneeling High Pulley Row",
            MuscleGroup.LATS,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK),
            null,
            "Select the appropriate weight using a pulley that is above your head. Attach a rope to the cable and kneel a couple of feet away, holding the rope out in front of you with both arms extended. This will be your starting position. Initiate the movement by flexing the elbows and fully retracting your shoulders, pulling the rope toward your upper chest with your elbows out."
        ),
        exercise(
            "fx-leverage-iso-row",
            "Leverage Iso Row",
            MuscleGroup.LATS,
            ExerciseCategory.MACHINE,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK),
            null,
            "Load an appropriate weight onto the pins and adjust the seat height so that the handles are at chest level. Grasp the handles with either a neutral or pronated grip. This will be your starting position. Pull the handles towards your torso, retracting your shoulder blades as you flex the elbow."
        ),
        exercise(
            "fx-one-arm-lat-pulldown",
            "One Arm Lat Pulldown",
            MuscleGroup.LATS,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK),
            null,
            "Select an appropriate weight and adjust the knee pad to help keep you down. Grasp the handle with a pronated grip. This will be your starting position. Pull the handle down, squeezing your elbow to your side as you flex the elbow."
        ),
        exercise(
            "fx-pullups",
            "Pullups",
            MuscleGroup.LATS,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK),
            null,
            "Grab the pull-up bar with the palms facing forward using the prescribed grip. Note on grips: For a wide grip, your hands need to be spaced out at a distance wider than your shoulder width. For a medium grip, your hands need to be spaced out at a distance equal to your shoulder width and for a close grip at a distance smaller than your shoulder width. As you have both arms extended in front of you "
        ),
        exercise(
            "fx-shotgun-row",
            "Shotgun Row",
            MuscleGroup.LATS,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK),
            null,
            "Attach a single handle to a low cable. After selecting the correct weight, stand a couple feet back with a wide-split stance. Your arm should be extended and your shoulder forward. This will be your starting position."
        ),
        exercise(
            "fx-underhand-cable-pulldowns",
            "Underhand Cable Pulldowns",
            MuscleGroup.LATS,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            null,
            "Sit down on a pull-down machine with a wide bar attached to the top pulley. Adjust the knee pad of the machine to fit your height. These pads will prevent your body from being raised by the resistance attached to the bar. Grab the pull-down bar with the palms facing your torso (a supinated grip). Make sure that the hands are placed closer than the shoulder width."
        ),
        exercise(
            "fx-v-bar-pulldown",
            "V-Bar Pulldown",
            MuscleGroup.LATS,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            null,
            "Sit down on a pull-down machine with a V-Bar attached to the top pulley. Adjust the knee pad of the machine to fit your height. These pads will prevent your body from being raised by the resistance attached to the bar."
        ),
        exercise(
            "fx-v-bar-pullup",
            "V-Bar Pullup",
            MuscleGroup.LATS,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            null,
            "Start by placing the middle of the V-bar in the middle of the pull-up bar (assuming that the pull-up station you are using does not have neutral grip handles). The V-Bar handles will be facing down so that you can hang from the pull-up bar through the use of the handles. Once you securely place the V-bar, take a hold of the bar from each side and hang from it. Stick your chest out and lean yoursel"
        ),
        exercise(
            "fx-wide-grip-lat-pulldown",
            "Wide-Grip Lat Pulldown",
            MuscleGroup.LATS,
            ExerciseCategory.CABLE,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            null,
            "Sit down on a pull-down machine with a wide bar attached to the top pulley. Make sure that you adjust the knee pad of the machine to fit your height. These pads will prevent your body from being raised by the resistance attached to the bar. Grab the bar with the palms facing forward using the prescribed grip. Note on grips: For a wide grip, your hands need to be spaced out at a distance wider than"
        ),
        exercise(
            "fx-wide-grip-rear-pull-up",
            "Wide-Grip Rear Pull-Up",
            MuscleGroup.LATS,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            null,
            "Grab the pull-up bar with the palms facing forward using a wide grip. As you have both arms extended in front of you holding the bar, bring your torso forward and head so that there is an imaginary line from the pull-up bar to the back of your neck. This is your starting position."
        ),
        exercise(
            "fx-barbell-full-squat",
            "Barbell Full Squat",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "This exercise is best performed inside a squat rack for safety purposes. To begin, first set the bar on a rack just above shoulder level. Once the correct height is chosen and the bar is loaded, step under the bar and place the back of your shoulders (slightly below the neck) across it. Hold on to the bar using both arms at each side and lift it off the rack by first pushing with your legs and at "
        ),
        exercise(
            "fx-barbell-hack-squat",
            "Barbell Hack Squat",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.FOREARMS, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "Stand up straight while holding a barbell behind you at arms length and your feet at shoulder width. Tip: A shoulder width grip is best with the palms of your hands facing back. You can use wrist wraps for this exercise for a better grip. This will be your starting position. While keeping your head and eyes up and back straight, squat until your upper thighs are parallel to the floor. Breathe in a"
        ),
        exercise(
            "fx-barbell-lunge",
            "Barbell Lunge",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "This exercise is best performed inside a squat rack for safety purposes. To begin, first set the bar on a rack just below shoulder level. Once the correct height is chosen and the bar is loaded, step under the bar and place the back of your shoulders (slightly below the neck) across it. Hold on to the bar using both arms at each side and lift it off the rack by first pushing with your legs and at "
        ),
        exercise(
            "fx-barbell-squat",
            "Barbell Squat",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "This exercise is best performed inside a squat rack for safety purposes. To begin, first set the bar on a rack to just below shoulder level. Once the correct height is chosen and the bar is loaded, step under the bar and place the back of your shoulders (slightly below the neck) across it. Hold on to the bar using both arms at each side and lift it off the rack by first pushing with your legs and "
        ),
        exercise(
            "fx-barbell-step-ups",
            "Barbell Step Ups",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "Stand up straight while holding a barbell placed on the back of your shoulders (slightly below the neck) and stand upright behind an elevated platform (such as the one used for spotting behind a flat bench). This is your starting position. Place the right foot on the elevated platform. Step on the platform by extending the hip and the knee of your right leg. Use the heel mainly to lift the rest of"
        ),
        exercise(
            "fx-box-squat",
            "Box Squat",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.CALVES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "The box squat allows you to squat to desired depth and develop explosive strength in the squat movement. Begin in a power rack with a box at the appropriate height behind you. Typically, you would aim for a box height that brings you to a parallel squat, but you can train higher or lower if desired. Begin by stepping under the bar and placing it across the back of the shoulders. Squeeze your shoul"
        ),
        exercise(
            "fx-clean-pull",
            "Clean Pull",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "With a barbell on the floor close to the shins, take an overhand or hook grip just outside the legs. Lower your hips with the weight focused on the heels, back straight, head facing forward, chest up, with your shoulders just in front of the bar. This will be your starting position. Begin the first pull by driving through the heels, extending your knees. Your back angle should stay the same, and y"
        ),
        exercise(
            "fx-clean-from-blocks",
            "Clean from Blocks",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "With a barbell on boxes or stands of the desired height, take an overhand or hook grip just outside the legs. Lower your hips with the weight focused on the heels, back straight, head facing forward, chest up, with your shoulders just in front of the bar. This will be your starting position. Begin the first pull by driving through the heels, extending your knees. Your back angle should stay the sa"
        ),
        exercise(
            "fx-dumbbell-lunges",
            "Dumbbell Lunges",
            MuscleGroup.QUADS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            null,
            "Stand with your torso upright holding two dumbbells in your hands by your sides. This will be your starting position. Step forward with your right leg around 2 feet or so from the foot being left stationary behind and lower your upper body down, while keeping the torso upright and maintaining balance. Inhale as you go down. Note: As in the other exercises, do not allow your knee to go forward beyo"
        ),
        exercise(
            "fx-frankenstein-squat",
            "Frankenstein Squat",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.ABS, MuscleGroup.CALVES, MuscleGroup.GLUTES),
            OLYMPIC_BAR,
            "This drill teaches you the proper positioning of both the bar and your body during the clean and front squat. Place the barbell on the front of the shoulders, releasing your grip and extending your arms out in front of you. The shoulders should be pushed forward to create a shelf, and the bar should be in contact with the throat. Ensure that you only move your shoulder blades forward; don't round "
        ),
        exercise(
            "fx-hang-clean",
            "Hang Clean",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.FOREARMS, MuscleGroup.GLUTES),
            OLYMPIC_BAR,
            "Begin with a shoulder width, double overhand or hook grip, with the bar hanging at the mid thigh position. Your back should be straight and inclined slightly forward. Begin by aggressively extending through the hips, knees and ankles, driving the weight upward. As you do so, shrug your shoulders towards your ears."
        ),
        exercise(
            "fx-jefferson-squats",
            "Jefferson Squats",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "Place a barbell on the floor. Stand in the middle of the bar length wise."
        ),
        exercise(
            "fx-olympic-squat",
            "Olympic Squat",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "Begin with a barbell supported on top of the traps. The chest should be up, and the head facing forward. Adopt a hip width stance with the feet turned out as needed. Descend by flexing the knees, refraining from moving the hips back as much as possible. This requires that the knees travel forward; ensure that they stay aligned with the feet. The goal is to keep the torso as upright as possible. Co"
        ),
        exercise(
            "fx-snatch",
            "Snatch",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "Place your feet at a shoulder width stance with the barbell resting right above the connection between the toes and the rest of the foot. With a palms facing down grip, bend at the knees and keeping the back flat grab the bar using a wider than shoulder width grip. Bring the hips down and make sure that your body drops as if you were going to sit on a chair. This will be your starting position."
        ),
        exercise(
            "fx-snatch-balance",
            "Snatch Balance",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "Begin with the feet in the pulling position, the bar racked across the back of the shoulders, and the hands placed in a wide snatch grip. Pop the bar with an abrupt dip and drive of the knees, and aggressively drive under the bar, transitioning the feet into the receiving position."
        ),
        exercise(
            "fx-speed-box-squat",
            "Speed Box Squat",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            OLYMPIC_BAR,
            "Attach bands to the bar that are securely anchored near the ground. You may need to choke the bands to get adequate tension. Use a box of an appropriate height for this exercise. Load the bar to a weight that still requires effort, but isn't so heavy that speed is compromised. Typically, that will be between 50-70% of your one rep max."
        ),
        exercise(
            "fx-split-clean",
            "Split Clean",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.FOREARMS, MuscleGroup.GLUTES),
            OLYMPIC_BAR,
            "With a barbell on the floor close to the shins, take an overhand grip just outside the legs. Lower your hips with the weight focused on the heels, back straight, head facing forward, chest up, with your shoulders just in front of the bar. This will be your starting position. Begin the first pull by driving through the heels, extending your knees. Your back angle should stay the same, and your arms"
        ),
        exercise(
            "fx-split-jerk",
            "Split Jerk",
            MuscleGroup.QUADS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Standing with the weight racked on the front of the shoulders, begin with the dip. With your feet directly under your hips, flex the knees without moving the hips backward. Go down only slightly, and reverse direction as powerfully as possible. Drive through the heels create as much speed and force as possible, and be sure to move your head out of the way as the bar leaves the shoulders. At this m"
        ),
        exercise(
            "fx-barbell-incline-shoulder-raise",
            "Barbell Incline Shoulder Raise",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CHEST),
            OLYMPIC_BAR,
            "Lie back on an Incline Bench. Using a medium width grip (a grip that is slightly wider than shoulder width), lift the bar from the rack and hold it straight over you with your arms straight. This will be your starting position. While keeping the arms straight, lift the bar by protracting your shoulder blades, raising the shoulders from the bench as you breathe out."
        ),
        exercise(
            "fx-barbell-rear-delt-row",
            "Barbell Rear Delt Row",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.LATS, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "Stand up straight while holding a barbell using a wide (higher than shoulder width) and overhand (palms facing your body) grip. Bend knees slightly and bend over as you keep the natural arch of your back. Let the arms hang in front of you as they hold the bar. Once your torso is parallel to the floor, flare the elbows out and away from your body. Tip: Your torso and your arms should resemble the l"
        ),
        exercise(
            "fx-bradford-rocky-presses",
            "Bradford/Rocky Presses",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Sit on a Military Press Bench with a bar at shoulder level with a pronated grip (palms facing forward). Tip: Your grip should be wider than shoulder width and it should create a 90-degree angle between the forearm and the upper arm as the barbell goes down. This is your starting position. Once you pick up the barbell with the correct grip, lift the bar up over your head by locking your arms."
        ),
        exercise(
            "fx-clean-and-press",
            "Clean and Press",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.ABS, MuscleGroup.CALVES, MuscleGroup.GLUTES),
            OLYMPIC_BAR,
            "Assume a shoulder-width stance, with knees inside the arms. Now while keeping the back flat, bend at the knees and hips so that you can grab the bar with the arms fully extended and a pronated grip that is slightly wider than shoulder width. Point the elbows out to sides. The bar should be close to the shins. Position the shoulders over or slightly ahead of the bar. Establish a flat back posture. "
        ),
        exercise(
            "fx-cuban-press",
            "Cuban Press",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.TRAPS),
            null,
            "Take a dumbbell in each hand with a pronated grip in a standing position. Raise your upper arms so that they are parallel to the floor, allowing your lower arms to hang in the \"scarecrow\" position. This will be your starting position. To initiate the movement, externally rotate the shoulders to move the upper arm 180 degrees. Keep the upper arms in place, rotating the upper arms until the wrists a"
        ),
        exercise(
            "fx-dumbbell-one-arm-upright-row",
            "Dumbbell One-Arm Upright Row",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.TRAPS),
            null,
            "Grab a dumbbell and stand up straight with your arm extended in front of you with a slight bend at the elbows and your back straight. This will be your starting position. Tip: The dumbbell should be resting on top of your thigh with the palm of your hands facing your thighs. Keep the other hand can be kept fully extended to the side, by the waist or grabbing a fixed surface. This will be your star"
        ),
        exercise(
            "fx-dumbbell-raise",
            "Dumbbell Raise",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.BICEPS),
            null,
            "Grab a dumbbell in each arm and stand up straight with your arms extended by your sides with a slight bend at the elbows and your back straight. This will be your starting position. Tip: The dumbbell should be next to your thighs with the palm of your hands facing back. Use your side shoulders to lift the dumbbells as you exhale. The dumbbells should be to the side of the body as you move them up."
        ),
        exercise(
            "fx-jerk-balance",
            "Jerk Balance",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS),
            OLYMPIC_BAR,
            "This drill helps you learn to drive yourself low enough during the jerk and corrects those who move backward during the movement. Begin with the bar racked in the jerk position, with the shoulders forward, torso upright, and the feet split slightly apart. Initiate the movement as you would a normal jerk, dipping at the knees while keeping your torso vertical, and driving back up forcefully, using "
        ),
        exercise(
            "fx-landmine-linear-jammer",
            "Landmine Linear Jammer",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.ABS, MuscleGroup.CALVES, MuscleGroup.CHEST),
            OLYMPIC_BAR,
            "Position a bar into landmine or, lacking one, securely anchor it in a corner. Load the bar to an appropriate weight and position the handle attachment on the bar. Raise the bar from the floor, taking the handles to your shoulders. This will be your starting position."
        ),
        exercise(
            "fx-push-press-behind-the-neck",
            "Push Press - Behind the Neck",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CALVES, MuscleGroup.QUADS, MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Standing with the weight racked on the back of the shoulders, begin with the dip. With your feet directly under your hips, flex the knees without moving the hips backward. Go down only slightly, and reverse direction as powerfully as possible. Drive through the heels create as much speed and force as possible, moving the bar in a vertical path. Using the momentum generated, finish pressing the wei"
        ),
        exercise(
            "fx-seated-barbell-military-press",
            "Seated Barbell Military Press",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Sit on a Military Press Bench with a bar behind your head and either have a spotter give you the bar (better on the rotator cuff this way) or pick it up yourself carefully with a pronated grip (palms facing forward). Tip: Your grip should be wider than shoulder width and it should create a 90-degree angle between the forearm and the upper arm as the barbell goes down. Once you pick up the barbell "
        ),
        exercise(
            "fx-seated-dumbbell-press",
            "Seated Dumbbell Press",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.TRICEPS),
            null,
            "Grab a couple of dumbbells and sit on a military press bench or a utility bench that has a back support on it as you place the dumbbells upright on top of your thighs. Clean the dumbbells up one at a time by using your thighs to bring the dumbbells up to shoulder height at each side."
        ),
        exercise(
            "fx-single-arm-linear-jammer",
            "Single-Arm Linear Jammer",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Position a bar into a landmine or securely anchor it in a corner. Load the bar to an appropriate weight. Raise the bar from the floor, taking it to your shoulders with one or both hands. Adopt a wide stance. This will be your starting position."
        ),
        exercise(
            "fx-standing-bradford-press",
            "Standing Bradford Press",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Place a loaded bar at shoulder level in a rack. With a pronated grip at shoulder width, begin with the bar racked across the front of your shoulders. This is your starting position. Initiate the lift by extending the elbows to press the bar overhead. Avoid locking out the elbow as you move the weight behind your head."
        ),
        exercise(
            "fx-standing-dumbbell-press",
            "Standing Dumbbell Press",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.TRICEPS),
            null,
            "Standing with your feet shoulder width apart, take a dumbbell in each hand. Raise the dumbbells to head height, the elbows out and about 90 degrees. This will be your starting position. Maintaining strict technique with no leg drive or leaning back, extend through the elbow to raise the weights together directly above your head."
        ),
        exercise(
            "fx-standing-military-press",
            "Standing Military Press",
            MuscleGroup.SHOULDERS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.TRICEPS),
            OLYMPIC_BAR,
            "Start by placing a barbell that is about chest high on a squat rack. Once you have selected the weights, grab the barbell using a pronated (palms facing forward) grip. Make sure to grip the bar wider than shoulder width apart from each other. Slightly bend the knees and place the barbell on your collar bone. Lift the barbell up keeping it lying on your chest. Take a step back and position your fee"
        ),
        exercise(
            "fx-barbell-shrug-behind-the-back",
            "Barbell Shrug Behind The Back",
            MuscleGroup.TRAPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS, MuscleGroup.BACK),
            OLYMPIC_BAR,
            "Stand up straight with your feet at shoulder width as you hold a barbell with both hands behind your back using a pronated grip (palms facing back). Tip: Your hands should be a little wider than shoulder width apart. You can use wrist wraps for this exercise for better grip. This will be your starting position. Raise your shoulders up as far as you can go as you breathe out and hold the contractio"
        ),
        exercise(
            "fx-clean-shrug",
            "Clean Shrug",
            MuscleGroup.TRAPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Begin with a shoulder width, double overhand or hook grip, with the bar hanging at the mid thigh position. Your back should be straight and inclined slightly forward. Shrug your shoulders towards your ears. While this exercise can usually by loaded with heavier weight than a clean, avoid overloading to the point that the execution slows down."
        ),
        exercise(
            "fx-kettlebell-sumo-high-pull",
            "Kettlebell Sumo High Pull",
            MuscleGroup.TRAPS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS),
            null,
            "Place a kettlebell on the ground between your feet. Position your feet in a wide stance, and grasp the kettlebell with two hands. Set your hips back as far as possible, with your knees bent. Keep your chest and head up. This will be your starting position. Begin by extending the hips and knees, simultaneously pulling the kettlebell to your shoulders, raising your elbows as you do so. Reverse the m"
        ),
        exercise(
            "fx-smith-machine-upright-row",
            "Smith Machine Upright Row",
            MuscleGroup.TRAPS,
            ExerciseCategory.MACHINE,
            listOf(MuscleGroup.BICEPS, MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            null,
            "To begin, set the bar on the smith machine to a height that is around the middle of your thighs. Once the correct height is chosen and the bar is loaded, grasp the bar using a pronated (palms forward) grip that is shoulder width apart. You may need some wrist wraps if using a significant amount of weight. Lift the barbell up and fully extend your arms with your back straight. There should be a sli"
        ),
        exercise(
            "fx-snatch-shrug",
            "Snatch Shrug",
            MuscleGroup.TRAPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.FOREARMS, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Begin with a wide grip, with the bar hanging at the mid thigh position. You can use a hook or overhand grip. Your back should be straight and inclined slightly forward. Shrug your shoulders towards your ears. While this exercise can usually by loaded with heavier weight than a snatch, avoid overloading to the point that the execution slows down."
        ),
        exercise(
            "fx-standing-dumbbell-upright-row",
            "Standing Dumbbell Upright Row",
            MuscleGroup.TRAPS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.BICEPS, MuscleGroup.SHOULDERS),
            null,
            "Grasp a dumbbell in each hand with a pronated (palms forward) grip that is slightly less than shoulder width. The dumbbells should be resting on top of your thighs. Your arms should be extended with a slight bend at the elbows and your back should be straight. This will be your starting position. Use your side shoulders to lift the dumbbells as you exhale. The dumbbells should be close to the body"
        ),
        exercise(
            "fx-bench-dips",
            "Bench Dips",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            null,
            "For this exercise you will need to place a bench behind your back. With the bench perpendicular to your body, and while looking away from it, hold on to the bench on its edge with the hands fully extended, separated at shoulder width. The legs will be extended forward, bent at the waist and perpendicular to your torso. This will be your starting position. Slowly lower your body as you inhale by be"
        ),
        exercise(
            "fx-board-press",
            "Board Press",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CHEST, MuscleGroup.FOREARMS, MuscleGroup.LATS),
            OLYMPIC_BAR,
            "Begin by lying on the bench, getting your head beyond the bar if possible. One to five boards, made out of 2x6's, can be screwed together and held in place by a training partner, bands, or just tucked under your shirt. Tuck your feet underneath you and arch your back. Using the bar to help support your weight, lift your shoulder off the bench and retract them, squeezing the shoulder blades togethe"
        ),
        exercise(
            "fx-close-grip-dumbbell-press",
            "Close-Grip Dumbbell Press",
            MuscleGroup.TRICEPS,
            ExerciseCategory.DUMBBELL,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            null,
            "Place a dumbbell standing up on a flat bench. Ensuring that the dumbbell stays securely placed at the top of the bench, lie perpendicular to the bench with only your shoulders lying on the surface. Hips should be below the bench and your legs bent with your feet firmly on the floor."
        ),
        exercise(
            "fx-decline-close-grip-bench-to-skull-crusher",
            "Decline Close-Grip Bench To Skull Crusher",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Secure your legs at the end of the decline bench and slowly lay down on the bench. Using a close grip (a grip that is slightly less than shoulder width), lift the bar from the rack and hold it straight over you with your arms locked and elbows in. The arms should be perpendicular to the floor. This will be your starting position. Tip: In order to protect your rotator cuff, it is best if you have a"
        ),
        exercise(
            "fx-dip-machine",
            "Dip Machine",
            MuscleGroup.TRICEPS,
            ExerciseCategory.MACHINE,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            null,
            "Sit securely in a dip machine, select the weight and firmly grasp the handles. Now keep your elbows in at your sides in order to place emphasis on the triceps. The elbows should be bent at a 90 degree angle."
        ),
        exercise(
            "fx-dips-triceps-version",
            "Dips - Triceps Version",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            null,
            "To get into the starting position, hold your body at arm's length with your arms nearly locked above the bars. Now, inhale and slowly lower yourself downward. Your torso should remain upright and your elbows should stay close to your body. This helps to better focus on tricep involvement. Lower yourself until there is a 90 degree angle formed between the upper arm and forearm."
        ),
        exercise(
            "fx-floor-press",
            "Floor Press",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Adjust the j-hooks so they are at the appropriate height to rack the bar. Begin lying on the floor with your head near the end of a power rack. Keeping your shoulder blades pulled together; pull the bar off of the hooks. Lower the bar towards the bottom of your chest or upper stomach, squeezing the bar and attempting to pull it apart as you do so. Ensure that you tuck your elbows throughout the mo"
        ),
        exercise(
            "fx-incline-push-up-close-grip",
            "Incline Push-Up Close-Grip",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BODYWEIGHT,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            null,
            "Stand facing a Smith machine bar or sturdy elevated platform at an appropriate height. Place your hands next to one another on the bar."
        ),
        exercise(
            "fx-jm-press",
            "JM Press",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Start the exercise the same way you would a close grip bench press. You will lie on a flat bench while holding a barbell at arms length (fully extended) with the elbows in. However, instead of having the arms perpendicular to the torso, make sure the bar is set in a direct line above the upper chest. This will be your starting position. Now beginning from a fully extended position lower the bar do"
        ),
        exercise(
            "fx-one-arm-floor-press",
            "One Arm Floor Press",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Lie down on a flat surface with your back pressing against the floor or an exercise mat. Make sure your knees are bent. Have a partner hand you the bar on one hand. When starting, your arm should be just about fully extended, similar to the starting position of a barbell bench press. However, this time your grip will be neutral (palms facing your torso)."
        ),
        exercise(
            "fx-pin-presses",
            "Pin Presses",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CHEST, MuscleGroup.FOREARMS, MuscleGroup.LATS),
            OLYMPIC_BAR,
            "Pin presses remove the eccentric phase of the bench press, developing starting strength. They also allow you to train a desired range of motion. The bench should be set up in a power rack. Set the pins to the desired point in your range of motion, whether it just be lockout or an inch off of your chest. The bar should be moved to the pins and prepared for lifting."
        ),
        exercise(
            "fx-reverse-triceps-bench-press",
            "Reverse Triceps Bench Press",
            MuscleGroup.TRICEPS,
            ExerciseCategory.BARBELL,
            listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS),
            OLYMPIC_BAR,
            "Lie back on a flat bench. Using a close, supinated grip (around shoulder width), lift the bar from the rack and hold it straight over you with your arms locked extended in front of you and perpendicular to the floor. This will be your starting position. As you breathe in, come down slowly until you feel the bar on your middle chest. Tip: Make sure that as opposed to a regular bench press, you keep"
        )
    )
}
