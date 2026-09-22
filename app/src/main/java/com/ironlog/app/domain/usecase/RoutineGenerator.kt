package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.ExerciseCategory
import com.ironlog.app.data.entity.ExerciseEntity
import com.ironlog.app.data.entity.MuscleGroup

/**
 * Builds a training week from the exercise library.
 *
 * The rules are the ordinary ones a coach would apply, written down: how many
 * days you train decides the split, the goal decides sets and rep ranges, and
 * the time you have decides how many exercises fit. Compounds come first
 * because they are the ones worth doing fresh.
 *
 * Deliberately not a language model. A generated programme is advice someone
 * will act on under a loaded barbell, and this way every exercise chosen can be
 * traced to a rule that can be read, argued with and tested -- none of which is
 * true of a sentence a model produced. It also runs offline, instantly, on any
 * phone.
 */
object RoutineGenerator {

    enum class Goal(val label: String, val detail: String) {
        STRENGTH("Strength", "Heavy, low reps, long rests."),
        MUSCLE("Muscle", "Moderate reps near failure, medium rests."),
        GENERAL("General fitness", "Mixed reps, shorter rests, full body.")
    }

    /** What the user asked for. */
    data class Request(
        val goal: Goal = Goal.MUSCLE,
        val daysPerWeek: Int = 3,
        val minutesPerSession: Int = 60
    )

    /** One day of the generated week. */
    data class Day(
        val name: String,
        val focus: String,
        val exercises: List<Slot>
    )

    data class Slot(
        val exercise: ExerciseEntity,
        val sets: Int,
        val repsLow: Int,
        val repsHigh: Int,
        val restSeconds: Int
    )

    data class Plan(val request: Request, val days: List<Day>) {
        val totalExercises: Int get() = days.sumOf { it.exercises.size }
    }

    // ----------------------------------------------------------------- splits

    /**
     * A day is a name and the muscles it covers, in the order they should be
     * trained: the biggest movers while the lifter is fresh.
     */
    private data class Template(val name: String, val focus: String, val muscles: List<MuscleGroup>)

    /**
     * Which split suits how often someone trains.
     *
     * Two days has to be full body or a muscle is trained once a fortnight;
     * five or six can afford a body-part split without any muscle going a week
     * between sessions.
     */
    private fun templatesFor(days: Int): List<Template> = when (days.coerceIn(2, 6)) {
        2 -> listOf(
            Template("Full body A", "Squat and press", listOf(MuscleGroup.QUADS, MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.ABS)),
            Template("Full body B", "Hinge and pull", listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.LATS, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.CALVES))
        )
        3 -> listOf(
            Template("Push", "Chest, shoulders, triceps", listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS, MuscleGroup.ABS)),
            Template("Pull", "Back, lats, biceps", listOf(MuscleGroup.LATS, MuscleGroup.BACK, MuscleGroup.TRAPS, MuscleGroup.BICEPS)),
            Template("Legs", "Quads, hamstrings, calves", listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.CALVES))
        )
        4 -> listOf(
            Template("Upper A", "Chest and back", listOf(MuscleGroup.CHEST, MuscleGroup.LATS, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
            Template("Lower A", "Quads first", listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES, MuscleGroup.CALVES, MuscleGroup.ABS)),
            Template("Upper B", "Back and shoulders", listOf(MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.CHEST)),
            Template("Lower B", "Hinge first", listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS, MuscleGroup.GLUTES, MuscleGroup.CALVES))
        )
        5 -> listOf(
            Template("Chest", "Press and fly", listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS)),
            Template("Back", "Pull and row", listOf(MuscleGroup.LATS, MuscleGroup.BACK, MuscleGroup.BICEPS)),
            Template("Legs", "Squat and hinge", listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES)),
            Template("Shoulders", "Press and raise", listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRAPS)),
            Template("Arms and core", "Curls, extensions, abs", listOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.ABS))
        )
        else -> listOf(
            Template("Push A", "Chest led", listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
            Template("Pull A", "Lats led", listOf(MuscleGroup.LATS, MuscleGroup.BACK, MuscleGroup.BICEPS)),
            Template("Legs A", "Squat led", listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES, MuscleGroup.CALVES)),
            Template("Push B", "Shoulders led", listOf(MuscleGroup.SHOULDERS, MuscleGroup.CHEST, MuscleGroup.TRICEPS)),
            Template("Pull B", "Back led", listOf(MuscleGroup.BACK, MuscleGroup.TRAPS, MuscleGroup.BICEPS)),
            Template("Legs B", "Hinge led", listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS, MuscleGroup.ABS))
        )
    }

    // ------------------------------------------------------------ programming

    /** Sets, reps and rest for the goal. These are the numbers the goal *is*. */
    private data class Prescription(val sets: Int, val repsLow: Int, val repsHigh: Int, val restSeconds: Int)

    private fun prescriptionFor(goal: Goal, isCompound: Boolean): Prescription = when (goal) {
        // Heavy and low, with rests long enough to actually recover between sets.
        Goal.STRENGTH -> if (isCompound) Prescription(5, 3, 5, 210) else Prescription(3, 6, 8, 120)
        // The middle ground, where most of the growth work is done.
        Goal.MUSCLE -> if (isCompound) Prescription(4, 6, 10, 150) else Prescription(3, 10, 15, 90)
        // Lighter and quicker, so a session stays inside a lunch hour.
        Goal.GENERAL -> if (isCompound) Prescription(3, 8, 12, 90) else Prescription(2, 12, 15, 60)
    }

    /**
     * How many exercises fit in the time available.
     *
     * Roughly: a set plus its rest, times the sets, plus a few minutes to find
     * the equipment. Deliberately conservative -- a routine that overruns is
     * one people quietly stop following.
     */
    private fun exerciseCount(minutes: Int, goal: Goal): Int {
        val perExercise = when (goal) {
            Goal.STRENGTH -> 12   // five sets with three-minute rests
            Goal.MUSCLE -> 9
            Goal.GENERAL -> 7
        }
        val usable = (minutes - WARM_UP_MINUTES).coerceAtLeast(perExercise)
        return (usable / perExercise).coerceIn(MIN_EXERCISES, MAX_EXERCISES)
    }

    private const val WARM_UP_MINUTES = 8
    private const val MIN_EXERCISES = 3
    private const val MAX_EXERCISES = 7

    /** Compounds train more than one joint, which is what makes them worth doing first. */
    private fun isCompound(exercise: ExerciseEntity): Boolean =
        exercise.secondaryMuscles.isNotEmpty() &&
            exercise.category in setOf(
                ExerciseCategory.BARBELL,
                ExerciseCategory.DUMBBELL,
                ExerciseCategory.BODYWEIGHT,
                ExerciseCategory.WEIGHTED_BODYWEIGHT,
                ExerciseCategory.MACHINE
            )

    /**
     * Builds the plan.
     *
     * [library] is the exercises to choose from. Anything not loggable as sets
     * and reps -- cardio, timed work -- is left out, since a routine of them
     * would generate a workout the app cannot track properly.
     */
    fun generate(request: Request, library: List<ExerciseEntity>): Plan {
        val usable = library.filter { it.category.tracksWeight || it.category == ExerciseCategory.BODYWEIGHT }
            .filterNot { it.primaryMuscle == MuscleGroup.CARDIO }

        val perDay = exerciseCount(request.minutesPerSession, request.goal)
        val used = mutableSetOf<String>()

        val days = templatesFor(request.daysPerWeek).map { template ->
            val slots = mutableListOf<Slot>()

            // One exercise per muscle in the template first, so a day covers
            // what it says it covers before it doubles up on anything.
            template.muscles.forEach { muscle ->
                if (slots.size >= perDay) return@forEach
                pick(usable, muscle, used, compoundFirst = slots.isEmpty())?.let { exercise ->
                    used += exercise.id
                    slots += slot(exercise, request.goal)
                }
            }

            // Spare time goes back to the muscles the day leads with.
            var index = 0
            while (slots.size < perDay && index < template.muscles.size * 2) {
                val muscle = template.muscles[index % template.muscles.size]
                pick(usable, muscle, used, compoundFirst = false)?.let { exercise ->
                    used += exercise.id
                    slots += slot(exercise, request.goal)
                }
                index++
            }

            Day(template.name, template.focus, slots)
        }

        return Plan(request, days)
    }

    private fun slot(exercise: ExerciseEntity, goal: Goal): Slot {
        val p = prescriptionFor(goal, isCompound(exercise))
        return Slot(exercise, p.sets, p.repsLow, p.repsHigh, p.restSeconds)
    }

    /**
     * The best unused exercise for a muscle.
     *
     * Barbell and dumbbell work is preferred for the first exercise of a day,
     * where a lifter is fresh and a compound is worth the most; machines and
     * cables are fine once they are not.
     */
    private fun pick(
        library: List<ExerciseEntity>,
        muscle: MuscleGroup,
        used: Set<String>,
        compoundFirst: Boolean
    ): ExerciseEntity? = library
        .filter { it.primaryMuscle == muscle && it.id !in used }
        .maxByOrNull { exercise -> score(exercise, compoundFirst) }

    /**
     * How suitable an exercise is for a slot.
     *
     * The staple bonus does most of the work. Without it the ranking rewarded
     * whatever had the shortest name, which put obscure variations at the top
     * of a session -- a generated chest day led with "Neck Press" rather than a
     * bench press. A programme should open with the lift people actually build
     * a session around.
     */
    private fun score(exercise: ExerciseEntity, compoundFirst: Boolean): Int {
        var score = 0
        if (isCompound(exercise) == compoundFirst) score += 400
        // Somebody's own movement belongs in their routine, but not ahead of
        // the canonical version of the same lift.
        if (!exercise.isCustom) score += 300
        if (exercise.category in setOf(ExerciseCategory.BARBELL, ExerciseCategory.DUMBBELL)) score += 200
        // Staples are ranked, not just flagged. Flat-scoring them left the tie
        // to name length, which opened a chest day with a fly because it is one
        // character shorter than the bench press.
        score += stapleRank(exercise.name) * 3
        // Last tiebreak only: between two equally suitable lifts, the plainer
        // name is the plainer movement.
        return score - exercise.name.length
    }

    /** How canonical a lift is: earlier in [STAPLES] means more so. */
    private fun stapleRank(name: String): Int {
        val n = name.lowercase()
        val index = STAPLES.indexOfFirst { n.contains(it) }
        return if (index < 0) 0 else 100 - index * 2
    }

    /** The lifts a programme is normally built out of, most canonical first. */
    private val STAPLES = listOf(
        "bench press", "squat", "deadlift", "overhead press", "military press",
        "barbell row", "bent over row", "seated cable row", "lat pulldown",
        "pull up", "pull-up", "chin up", "chin-up", "dip", "leg press",
        "romanian deadlift", "hip thrust", "lunge", "leg curl", "leg extension",
        "calf raise", "lateral raise", "face pull", "bicep curl", "hammer curl",
        "barbell curl", "triceps pushdown", "skullcrusher", "plank", "hanging leg raise",
        "incline bench", "shrug", "chest fly", "pullover"
    )

}
