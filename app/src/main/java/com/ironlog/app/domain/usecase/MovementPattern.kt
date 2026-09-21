package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.MuscleGroup

/**
 * The shape of a lift, used to show what the movement looks like.
 *
 * Patterns rather than one animation per exercise: a library of two hundred
 * movements is really a dozen shapes done with different implements, and an
 * incline dumbbell press is the same arc as a flat barbell press. Recognising
 * the pattern is also what transfers -- a lifter who can see a hinge can
 * perform any of the fifteen hinges in the library.
 *
 * Derived from the exercise rather than stored on it, so it costs no column,
 * needs no migration, and works on a movement the user invented five seconds
 * ago as readily as on a seeded one.
 */
enum class MovementPattern(val label: String, val detail: String) {
    SQUAT("Squat", "Knees and hips bend together, chest stays up."),
    HINGE("Hinge", "Hips travel back, back stays flat, knees soften."),
    LUNGE("Lunge", "One leg forward, both knees bend, torso upright."),
    HORIZONTAL_PRESS("Horizontal press", "Press away from the chest, elbows tucked."),
    VERTICAL_PRESS("Overhead press", "Press overhead, ribs down, finish with the bar over the ears."),
    HORIZONTAL_PULL("Row", "Pull towards the ribs, shoulder blades back."),
    VERTICAL_PULL("Pulldown", "Pull from overhead to the collarbone, elbows down."),
    CURL("Curl", "Elbows fixed at the sides, forearms rotate up."),
    EXTENSION("Triceps extension", "Elbows fixed, forearms straighten."),
    LATERAL_RAISE("Raise", "Arms travel out or up with a fixed elbow angle."),
    SHRUG("Shrug", "Shoulders lift straight up, arms stay long."),
    CALF_RAISE("Calf raise", "Heels rise and lower under a straight leg."),
    CORE("Core", "Ribs draw towards the hips against resistance."),
    CARRY("Carry or hold", "Position is held while the body resists moving."),
    CARDIO("Cardio", "Repeated whole-body movement over time.");

    companion object {

        /**
         * Works out the pattern from the exercise's name, muscle and equipment.
         *
         * Name first, because the name is what actually names the movement: a
         * "Romanian Deadlift" is a hinge no matter which muscle the database
         * files it under. Muscle and equipment only settle what the name leaves
         * open.
         */
        fun of(
            name: String,
            primaryMuscle: MuscleGroup,
            category: ExerciseCategory
        ): MovementPattern {
            val n = name.lowercase()

            // Order matters: the earlier checks are the more specific phrases.
            // "Calf press" is a calf raise, not a press, so calves come first.
            return when {
                category == ExerciseCategory.CARDIO -> CARDIO
                n.containsAny("calf", "heel raise") -> CALF_RAISE
                n.containsAny("shrug") -> SHRUG
                n.containsWord("plank", "carry", "hold", "hang", "farmer") -> CARRY
                n.containsAny("curl") && !n.contains("leg curl") -> CURL
                n.containsAny("leg curl") -> HINGE
                n.containsAny("pushdown", "push-down", "skull", "extension", "kickback", "jm press") -> EXTENSION
                n.containsAny("lateral raise", "front raise", "side raise", "fly", "flye", "reverse fly", "raise") &&
                    !n.containsAny("calf", "leg raise", "knee raise", "hip raise") -> LATERAL_RAISE
                n.containsAny("lunge", "split squat", "step up", "step-up") -> LUNGE
                n.containsAny("squat", "leg press", "hack") -> SQUAT
                n.containsAny(
                    "deadlift", "good morning", "hinge", "swing", "snatch",
                    "pull through", "hip thrust", "bridge", "rdl"
                ) || n.containsWord("clean") -> HINGE
                n.containsWord("row") || n.contains("face pull") -> HORIZONTAL_PULL
                n.containsAny("pulldown", "pull-down", "pullup", "pull-up", "pull up") ||
                    n.containsWord("chin", "chins", "chinup") -> VERTICAL_PULL
                n.containsAny("overhead press", "shoulder press", "military", "push press", "jerk", "landmine press") -> VERTICAL_PRESS
                n.containsAny("bench", "chest press", "push up", "push-up", "pushup", "floor press") ||
                    n.containsWord("dip", "dips") -> HORIZONTAL_PRESS
                n.containsAny("crunch", "sit-up", "sit up", "twist", "leg raise", "knee raise", "rollout", "jackknife", "flutter", "leg lift", "leg pull") -> CORE
                n.contains("press") -> if (primaryMuscle == MuscleGroup.SHOULDERS) VERTICAL_PRESS else HORIZONTAL_PRESS
                n.contains("pull") -> VERTICAL_PULL

                // Nothing in the name settled it, so fall back to the muscle it
                // trains, which at least gets the limb and direction right.
                else -> when (primaryMuscle) {
                    MuscleGroup.CHEST -> HORIZONTAL_PRESS
                    MuscleGroup.SHOULDERS -> VERTICAL_PRESS
                    MuscleGroup.BACK, MuscleGroup.TRAPS -> HORIZONTAL_PULL
                    MuscleGroup.LATS -> VERTICAL_PULL
                    MuscleGroup.BICEPS -> CURL
                    MuscleGroup.TRICEPS -> EXTENSION
                    MuscleGroup.FOREARMS -> CURL
                    MuscleGroup.QUADS -> SQUAT
                    MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES -> HINGE
                    MuscleGroup.CALVES -> CALF_RAISE
                    MuscleGroup.ABS -> CORE
                    MuscleGroup.FULL_BODY -> HINGE
                    MuscleGroup.CARDIO -> CARDIO
                }
            }
        }

        private fun String.containsAny(vararg needles: String) = needles.any { contains(it) }

        /**
         * Whole words only.
         *
         * Short tokens are dangerous as substrings: "chin" sits inside
         * "machine", which turned a machine press into a pulldown, and "row"
         * sits inside "narrow".
         */
        private fun String.containsWord(vararg needles: String): Boolean {
            val words = split(Regex("[^a-z0-9]+"))
            return needles.any { it in words }
        }
    }
}
