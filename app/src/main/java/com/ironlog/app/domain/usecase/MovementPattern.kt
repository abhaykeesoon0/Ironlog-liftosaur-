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
    UPRIGHT_ROW("Upright row", "The bar travels up the body, elbows leading above the wrists."),
    PULLOVER("Pullover", "Arms sweep overhead and back with the elbows nearly fixed."),
    CURL("Curl", "Elbows fixed at the sides, forearms rotate up."),
    EXTENSION("Triceps extension", "Elbows fixed, forearms straighten."),
    LATERAL_RAISE("Raise", "Arms travel out or up with a fixed elbow angle."),
    SHRUG("Shrug", "Shoulders lift straight up, arms stay long."),
    CALF_RAISE("Calf raise", "Heels rise and lower under a straight leg."),
    LEG_CURL("Leg curl", "Knees bend against the pad, hips stay down."),
    LEG_EXTENSION("Leg extension", "Seated, knees straighten against the pad."),
    HIP_ABDUCTION("Hip abduction", "Seated, the thighs drive apart or together against the pads."),
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

            // Order matters, and it is the same order as
            // tools/generate_movement_patterns.py, which decides the shipped
            // library. The two are kept in step deliberately: a lifter who
            // writes out "Leg Extension" as their own exercise should get the
            // same demonstration as the seeded one of the same name.
            return when {
                category == ExerciseCategory.CARDIO -> CARDIO

                // Names carrying a word that means something else elsewhere.
                // "Calf press" is a calf raise, "leg curl" is a knee curl, and
                // "back extension" has nothing to do with the triceps.
                n.containsAny("calf", "heel raise") -> CALF_RAISE
                n.containsAny("shrug") -> SHRUG
                n.containsAny("leg curl", "hamstring curl", "lying curl") -> LEG_CURL
                n.containsAny("leg extension", "knee extension") -> LEG_EXTENSION
                n.containsAny("pullover") -> PULLOVER
                n.containsAny("upright row", "sumo high") || n.containsPhrase("high pull") -> UPRIGHT_ROW
                n.containsAny("abduct", "adduct") -> HIP_ABDUCTION
                n.containsAny("back extension", "hyperextension", "hyper extension") -> HINGE
                n.containsAny("kickback") -> if (primaryMuscle == MuscleGroup.TRICEPS) EXTENSION else HINGE

                // A step-up with a knee raise is still a step-up, so the legs
                // are claimed before the trunk rules can take the phrase.
                n.containsAny("lunge", "split squat", "step up", "step-up") -> LUNGE

                n.containsAny("rollout", "roll-out", "ab wheel") -> CORE
                n.containsAny(
                    "crunch", "sit-up", "sit up", "situp", "twist", "leg raise", "knee raise",
                    "jackknife", "flutter", "leg lift", "leg pull", "judo flip", "cable lift",
                    "elbow to knee", "hip raise", "woodchop", "v-up"
                ) -> CORE
                n.containsAny("plank") -> CARRY

                // The Olympic lifts, before "hang" reads as a hold.
                n.containsWord("clean", "snatch", "jerk") -> HINGE
                n.containsWord("carry", "hold", "hang", "farmer", "farmers") -> CARRY

                n.containsAny("curl") -> CURL
                n.containsAny(
                    "pushdown", "push-down", "skull", "jm press", "triceps extension",
                    "tricep extension", "overhead extension", "french press"
                ) -> EXTENSION

                // A rear delt row is a row; only the fly version is a raise.
                !n.containsWord("row") && n.containsAny(
                    "fly", "flye", "around the world", "rear delt", "lateral raise",
                    "front raise", "side raise", "shoulder raise"
                ) -> LATERAL_RAISE
                !n.containsWord("row") && n.contains("raise") &&
                    !n.containsAny("calf", "leg raise", "knee raise", "hip raise") -> LATERAL_RAISE

                n.containsAny("squat", "leg press", "hack") -> SQUAT
                n.containsAny(
                    "deadlift", "good morning", "hinge", "swing", "hip thrust", "bridge",
                    "rdl", "rack pull", "pull through", "pull-through", "stiff leg",
                    "stiff-leg", "butt lift", "back raise"
                ) -> HINGE

                n.containsWord("row") || n.contains("face pull") -> HORIZONTAL_PULL
                n.containsAny("pulldown", "pull-down", "pullup", "pull-up", "pull up") ||
                    n.containsWord("chin", "chins", "chinup") -> VERTICAL_PULL

                n.containsAny(
                    "overhead press", "shoulder press", "military", "push press",
                    "landmine press", "jammer", "arnold", "bradford", "cuban press", "overhead"
                ) -> VERTICAL_PRESS
                n.containsAny(
                    "bench", "chest press", "push up", "push-up", "pushup",
                    "floor press", "pin press", "board press", "neck press"
                ) || n.containsWord("dip", "dips") -> HORIZONTAL_PRESS
                n.contains("press") -> if (primaryMuscle == MuscleGroup.SHOULDERS) VERTICAL_PRESS else HORIZONTAL_PRESS
                n.contains("pull") -> VERTICAL_PULL

                // Nothing in the name settled it, so fall back to the muscle it
                // trains, which at least gets the limb and direction right.
                else -> when (primaryMuscle) {
                    MuscleGroup.CHEST -> HORIZONTAL_PRESS
                    MuscleGroup.SHOULDERS -> VERTICAL_PRESS
                    MuscleGroup.BACK -> HORIZONTAL_PULL
                    MuscleGroup.TRAPS -> SHRUG
                    MuscleGroup.LATS -> VERTICAL_PULL
                    MuscleGroup.BICEPS -> CURL
                    MuscleGroup.TRICEPS -> EXTENSION
                    MuscleGroup.FOREARMS -> CURL
                    MuscleGroup.QUADS -> SQUAT
                    MuscleGroup.HAMSTRINGS -> LEG_CURL
                    MuscleGroup.GLUTES -> HINGE
                    MuscleGroup.CALVES -> CALF_RAISE
                    MuscleGroup.ABS -> CORE
                    MuscleGroup.FULL_BODY -> HINGE
                    MuscleGroup.CARDIO -> CARDIO
                }
            }
        }

        private fun String.containsAny(vararg needles: String) = needles.any { contains(it) }

        /**
         * A phrase bounded by word edges. "High pull" sits inside "Kneeling
         * High Pulley Row", which is a row off a high pulley and not a high
         * pull at all.
         */
        private fun String.containsPhrase(phrase: String) =
            Regex("\\b${Regex.escape(phrase)}s?\\b").containsMatchIn(this)

        /**
         * Whole words only.
         *
         * Short tokens are dangerous as substrings: "chin" sits inside
         * "machine", which turned a machine press into a pulldown, and "row"
         * sits inside "narrow".
         */
        private fun String.containsWord(vararg needles: String): Boolean {
            val words = split(Regex("[^a-z0-9]+"))
            // Plurals count: "Elevated Cable Rows" is as much a row as
            // "Cable Row", and missing that sent it to the wrong animation.
            return needles.any { it in words || "${it}s" in words || "${it}es" in words }
        }
    }
}
