package com.ironlog.app.domain.usecase

import com.ironlog.app.data.entity.MuscleGroup
import com.ironlog.app.data.entity.MusclePart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which parts of the body an exercise works, and how much each was worked in
 * a week.
 *
 * The library records a coarse group per exercise (chest, shoulders, back).
 * A lifter thinks finer than that: an incline press is the upper chest, a
 * lateral raise the middle delt, a skullcrusher the long head. That finer
 * reading is derived here from the exercise's name and group, with the
 * secondary muscles carrying half the weight of the primary. It is a
 * heuristic, kept in one place and tested, not a claim of anatomy.
 */
object MuscleParts {

    /** One exercise in one workout: what the week is added up from. */
    data class Work(
        val exerciseName: String,
        val primary: MuscleGroup,
        val secondary: List<MuscleGroup>,
        val startedAt: Long,
        val sets: Int,
        val volumeKg: Double
    )

    /** How much one part was worked in a week, and by what. */
    data class PartWork(
        /** Working sets, with secondary work counted at half. */
        val sets: Double,
        val volumeKg: Double,
        /** Exercise name to the sets it contributed, most first. */
        val exercises: Map<String, Int>,
        val lastAt: Long
    )

    data class Week(val label: String, val start: LocalDate, val parts: Map<MusclePart, PartWork>) {
        val isEmpty: Boolean get() = parts.isEmpty()
    }

    /** The parts an exercise works, weighted 1 for its main muscles and 0.5 for the helpers. */
    fun partsFor(name: String, primary: MuscleGroup, secondary: List<MuscleGroup>): Map<MusclePart, Float> {
        val n = name.lowercase()
        val out = LinkedHashMap<MusclePart, Float>()
        refine(n, primary).forEach { out[it] = 1f }
        secondary.filter { it != primary }.forEach { group ->
            refine(n, group).forEach { part -> if (part !in out) out[part] = 0.5f }
        }
        return out
    }

    /**
     * The parts a coarse group means for an exercise with this name. The
     * name decides the split where a split exists; otherwise the group's
     * usual parts are all returned.
     */
    fun refine(n: String, group: MuscleGroup): List<MusclePart> = when (group) {
        MuscleGroup.CHEST -> when {
            "incline" in n -> listOf(MusclePart.UPPER_CHEST)
            "decline" in n || "dip" in n -> listOf(MusclePart.LOWER_CHEST)
            else -> listOf(MusclePart.LOWER_CHEST, MusclePart.UPPER_CHEST)
        }
        MuscleGroup.SHOULDERS -> when {
            "rear" in n || "reverse fl" in n || "face pull" in n -> listOf(MusclePart.REAR_DELT)
            "lateral" in n || "side raise" in n || "upright" in n -> listOf(MusclePart.MIDDLE_DELT)
            "front raise" in n -> listOf(MusclePart.FRONT_DELT)
            "press" in n || "arnold" in n || "overhead" in n -> listOf(MusclePart.FRONT_DELT, MusclePart.MIDDLE_DELT)
            else -> listOf(MusclePart.FRONT_DELT, MusclePart.MIDDLE_DELT, MusclePart.REAR_DELT)
        }
        MuscleGroup.BACK -> when {
            "deadlift" in n || "good morning" in n || "extension" in n || "rack pull" in n || "pull through" in n ->
                listOf(MusclePart.LOWER_BACK)
            "pull up" in n || "pull-up" in n || "chin" in n || "pulldown" in n || "pullover" in n -> listOf(MusclePart.LATS)
            "row" in n -> listOf(MusclePart.UPPER_BACK, MusclePart.LATS)
            else -> listOf(MusclePart.UPPER_BACK)
        }
        MuscleGroup.LATS -> listOf(MusclePart.LATS)
        MuscleGroup.TRAPS -> listOf(MusclePart.TRAPS)
        MuscleGroup.ABS -> when {
            "oblique" in n || "twist" in n || "side" in n || "woodchop" in n || "lift" in n || "judo" in n ->
                listOf(MusclePart.OBLIQUES)
            else -> listOf(MusclePart.ABDOMINALS)
        }
        MuscleGroup.QUADS -> when {
            "sumo" in n -> listOf(MusclePart.QUADS, MusclePart.ADDUCTORS)
            else -> listOf(MusclePart.QUADS)
        }
        MuscleGroup.HAMSTRINGS -> listOf(MusclePart.HAMSTRINGS)
        MuscleGroup.GLUTES -> when {
            "abduct" in n || "clamshell" in n || "side-lying" in n || "side lying" in n -> listOf(MusclePart.ABDUCTORS)
            "adduct" in n -> listOf(MusclePart.ADDUCTORS)
            else -> listOf(MusclePart.GLUTES)
        }
        MuscleGroup.CALVES -> listOf(MusclePart.CALVES)
        MuscleGroup.BICEPS -> when {
            "hammer" in n || "reverse" in n || "zottman" in n || "incline" in n -> listOf(MusclePart.BICEPS_LONG)
            "preacher" in n || "concentration" in n || "spider" in n || "drag" in n -> listOf(MusclePart.BICEPS_SHORT)
            else -> listOf(MusclePart.BICEPS_LONG, MusclePart.BICEPS_SHORT)
        }
        MuscleGroup.TRICEPS -> when {
            "overhead" in n || "skull" in n || "french" in n || "lying" in n -> listOf(MusclePart.TRICEPS_LONG)
            "pushdown" in n || "kickback" in n || "press-down" in n || "pressdown" in n ->
                listOf(MusclePart.TRICEPS_LATERAL, MusclePart.TRICEPS_MEDIAL)
            else -> listOf(MusclePart.TRICEPS_LONG, MusclePart.TRICEPS_LATERAL, MusclePart.TRICEPS_MEDIAL)
        }
        MuscleGroup.FOREARMS -> listOf(MusclePart.FOREARMS)
        MuscleGroup.FULL_BODY, MuscleGroup.CARDIO -> emptyList()
    }

    /**
     * The last [weeks] weeks, newest first, each summed by part. Weeks run
     * Monday to Sunday, the way the streak and the Home card count them.
     */
    fun weekly(
        work: List<Work>,
        weeks: Int = 4,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Week> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return (0 until weeks).map { back ->
            val start = thisMonday.minusWeeks(back.toLong())
            val end = start.plusWeeks(1)
            val inWeek = work.filter {
                val day = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
                !day.isBefore(start) && day.isBefore(end)
            }
            Week(
                label = when (back) { 0 -> "This week"; 1 -> "Last week"; else -> "$back weeks ago" },
                start = start,
                parts = sum(inWeek)
            )
        }
    }

    private fun sum(work: List<Work>): Map<MusclePart, PartWork> {
        data class Acc(var sets: Double = 0.0, var volume: Double = 0.0, val byExercise: MutableMap<String, Int> = mutableMapOf(), var lastAt: Long = 0L)
        val acc = mutableMapOf<MusclePart, Acc>()
        work.forEach { w ->
            partsFor(w.exerciseName, w.primary, w.secondary).forEach { (part, weight) ->
                val a = acc.getOrPut(part) { Acc() }
                a.sets += w.sets * weight
                a.volume += w.volumeKg * weight
                a.byExercise[w.exerciseName] = (a.byExercise[w.exerciseName] ?: 0) + w.sets
                if (w.startedAt > a.lastAt) a.lastAt = w.startedAt
            }
        }
        return acc.filterValues { it.sets > 0 }.mapValues { (_, a) ->
            PartWork(
                sets = a.sets,
                volumeKg = a.volume,
                exercises = a.byExercise.entries.sortedByDescending { it.value }.associate { it.key to it.value },
                lastAt = a.lastAt
            )
        }
    }

    /**
     * How dark a part is drawn, 0 (untouched) to 1 (hammered), on a scale of
     * weekly working sets that matches how volume is usually talked about:
     * a few sets is a touch, ten is a proper session, twenty is a lot.
     */
    fun intensity(sets: Double): Float = when {
        sets <= 0.0 -> 0f
        sets < 4 -> 0.3f
        sets < 9 -> 0.55f
        sets < 15 -> 0.8f
        else -> 1f
    }
}
