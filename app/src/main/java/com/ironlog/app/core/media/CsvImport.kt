package com.ironlog.app.core.media

import com.ironlog.app.data.repository.Backup
import com.ironlog.app.data.repository.BackupExercise
import com.ironlog.app.data.repository.BackupSet
import com.ironlog.app.data.repository.BackupWorkout
import com.ironlog.app.domain.usecase.TrainingMath
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Reads the CSV exports of the two apps people most often arrive from --
 * Strong and Hevy -- into the same shape as a Liftosaur backup, so the
 * existing restore path does the writing and its rules (no duplicates,
 * unknown exercises created as custom ones) apply unchanged.
 *
 * Both formats are one row per set. The header names the format. Weights
 * are converted to kilograms here, as the rest of the app expects; a
 * Strong export says which unit it used by its column name.
 *
 * Pure: takes text, returns a [Backup]. Tested against sample rows.
 */
object CsvImport {

    enum class Source(val label: String) { STRONG("Strong"), HEVY("Hevy") }

    data class Result(val backup: Backup, val source: Source, val rowsRead: Int, val rowsSkipped: Int)

    class UnrecognisedException : IllegalArgumentException("Not a Strong or Hevy export")

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Result {
        val rows = readCsv(text)
        if (rows.isEmpty()) throw UnrecognisedException()
        val header = rows.first().map { it.trim().lowercase() }
        val body = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }
        return when {
            "exercise_title" in header && "start_time" in header -> hevy(header, body, zone)
            "exercise name" in header && "date" in header -> strong(header, body, zone)
            else -> throw UnrecognisedException()
        }
    }

    // ----------------------------------------------------------------- Strong

    private fun strong(header: List<String>, body: List<List<String>>, zone: ZoneId): Result {
        val col = header.withIndex().associate { (i, name) -> name to i }
        val weightCol = header.indexOfFirst { it.startsWith("weight") }
        val poundsExport = weightCol >= 0 && "lb" in header[weightCol]
        val date = col.getValue("date")
        val name = col.getValue("exercise name")
        val workoutName = col["workout name"]
        val order = col["set order"]
        val reps = col["reps"]
        val seconds = col["seconds"]
        val distance = col["distance"]
        val notes = col["workout notes"]
        val duration = col["duration"]
        var skipped = 0

        val byWorkout = LinkedHashMap<String, MutableList<List<String>>>()
        body.forEach { row ->
            val key = row.getOrNull(date)?.trim().orEmpty()
            if (key.isBlank()) { skipped++; return@forEach }
            byWorkout.getOrPut(key) { mutableListOf() }.add(row)
        }

        val workouts = byWorkout.mapNotNull { (dateText, rows) ->
            val startedAt = parseDate(dateText, zone) ?: run { skipped += rows.size; return@mapNotNull null }
            val exercises = LinkedHashMap<String, MutableList<BackupSet>>()
            rows.forEach { row ->
                val exerciseName = row.getOrNull(name)?.trim().orEmpty().ifBlank { skipped++; return@forEach }
                val orderText = row.getOrNull(order ?: -1)?.trim().orEmpty()
                // Strong marks warm-ups as a set order of "W", rest timers as "Rest Timer".
                if (orderText.startsWith("rest", ignoreCase = true) || orderText.equals("note", true)) { skipped++; return@forEach }
                val type = if (orderText.startsWith("W", ignoreCase = true)) "WARMUP" else "NORMAL"
                val weight = row.getOrNull(weightCol)?.toDoubleOrNull()?.let { if (poundsExport) TrainingMath.lbToKg(it) else it }
                val list = exercises.getOrPut(exerciseName) { mutableListOf() }
                list += BackupSet(
                    setNumber = list.size + 1,
                    type = type,
                    weightKg = weight?.takeIf { it > 0 },
                    reps = row.getOrNull(reps ?: -1)?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 },
                    rpe = null,
                    distanceMeters = row.getOrNull(distance ?: -1)?.toDoubleOrNull()?.takeIf { it > 0 },
                    durationSeconds = row.getOrNull(seconds ?: -1)?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
                )
            }
            if (exercises.isEmpty()) return@mapNotNull null
            val first = rows.first()
            val length = first.getOrNull(duration ?: -1)?.let { parseDuration(it) }
            BackupWorkout(
                name = first.getOrNull(workoutName ?: -1)?.trim().orEmpty().ifBlank { "Imported workout" },
                startedAt = startedAt,
                finishedAt = length?.let { startedAt + it * 1000L },
                notes = first.getOrNull(notes ?: -1)?.trim().orEmpty(),
                exercises = exercises.map { (n, sets) -> BackupExercise(exerciseId = "strong:$n", name = n, notes = "", sets = sets) }
            )
        }
        return Result(backup(workouts), Source.STRONG, body.size, skipped)
    }

    // ------------------------------------------------------------------- Hevy

    private fun hevy(header: List<String>, body: List<List<String>>, zone: ZoneId): Result {
        val col = header.withIndex().associate { (i, name) -> name to i }
        val title = col["title"]
        val start = col.getValue("start_time")
        val end = col["end_time"]
        val description = col["description"]
        val name = col.getValue("exercise_title")
        val setType = col["set_type"]
        val weightKg = col["weight_kg"]
        val weightLb = col["weight_lbs"]
        val reps = col["reps"]
        val distanceKm = col["distance_km"]
        val distanceMi = col["distance_miles"]
        val seconds = col["duration_seconds"]
        val rpe = col["rpe"]
        var skipped = 0

        val byWorkout = LinkedHashMap<String, MutableList<List<String>>>()
        body.forEach { row ->
            val key = row.getOrNull(start)?.trim().orEmpty()
            if (key.isBlank()) { skipped++; return@forEach }
            byWorkout.getOrPut(key) { mutableListOf() }.add(row)
        }

        val workouts = byWorkout.mapNotNull { (startText, rows) ->
            val startedAt = parseDate(startText, zone) ?: run { skipped += rows.size; return@mapNotNull null }
            val exercises = LinkedHashMap<String, MutableList<BackupSet>>()
            rows.forEach { row ->
                val exerciseName = row.getOrNull(name)?.trim().orEmpty().ifBlank { skipped++; return@forEach }
                val type = when (row.getOrNull(setType ?: -1)?.trim()?.lowercase()) {
                    "warmup" -> "WARMUP"
                    "dropset" -> "DROP"
                    "failure" -> "FAILURE"
                    else -> "NORMAL"
                }
                val weight = row.getOrNull(weightKg ?: -1)?.toDoubleOrNull()
                    ?: row.getOrNull(weightLb ?: -1)?.toDoubleOrNull()?.let { TrainingMath.lbToKg(it) }
                val distance = row.getOrNull(distanceKm ?: -1)?.toDoubleOrNull()?.let { it * 1000 }
                    ?: row.getOrNull(distanceMi ?: -1)?.toDoubleOrNull()?.let { it * 1609.344 }
                val list = exercises.getOrPut(exerciseName) { mutableListOf() }
                list += BackupSet(
                    setNumber = list.size + 1,
                    type = type,
                    weightKg = weight?.takeIf { it > 0 },
                    reps = row.getOrNull(reps ?: -1)?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 },
                    rpe = row.getOrNull(rpe ?: -1)?.toDoubleOrNull(),
                    distanceMeters = distance?.takeIf { it > 0 },
                    durationSeconds = row.getOrNull(seconds ?: -1)?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
                )
            }
            if (exercises.isEmpty()) return@mapNotNull null
            val first = rows.first()
            BackupWorkout(
                name = first.getOrNull(title ?: -1)?.trim().orEmpty().ifBlank { "Imported workout" },
                startedAt = startedAt,
                finishedAt = first.getOrNull(end ?: -1)?.let { parseDate(it, zone) },
                notes = first.getOrNull(description ?: -1)?.trim().orEmpty(),
                exercises = exercises.map { (n, sets) -> BackupExercise(exerciseId = "hevy:$n", name = n, notes = "", sets = sets) }
            )
        }
        return Result(backup(workouts), Source.HEVY, body.size, skipped)
    }

    private fun backup(workouts: List<BackupWorkout>) = Backup(
        exportedAt = System.currentTimeMillis(),
        workouts = workouts,
        routines = emptyList(),
        measurements = emptyList(),
        cardio = emptyList()
    )

    // ---------------------------------------------------------------- helpers

    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd HH:mm:ss",          // Strong
        "d MMM yyyy, HH:mm",            // Hevy
        "dd MMM yyyy, HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm"
    ).map { pattern ->
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH)
    }

    /** Epoch millis from the date styles the two exports use, or null when none fits. */
    fun parseDate(text: String, zone: ZoneId): Long? {
        val clean = text.trim().trim('"')
        DATE_FORMATS.forEach { f ->
            runCatching { LocalDateTime.parse(clean, f) }.getOrNull()
                ?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        }
        return null
    }

    /** Strong writes "1h 5m" or "45m"; seconds out of it. */
    fun parseDuration(text: String): Int? {
        val h = Regex("(\\d+)\\s*h").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = Regex("(\\d+)\\s*m").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val s = Regex("(\\d+)\\s*s").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = h * 3600 + m * 60 + s
        return total.takeIf { it > 0 }
    }

    /** A small RFC 4180 reader: quoted fields, doubled quotes, commas and newlines inside quotes. */
    fun readCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes && c == '"' && s.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(field.toString()); field.setLength(0) }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && s.getOrNull(i + 1) == '\n') i++
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
