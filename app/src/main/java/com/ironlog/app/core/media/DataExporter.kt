package com.ironlog.app.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ironlog.app.core.security.Validators
import com.ironlog.app.data.dao.WorkoutWithDetail
import com.ironlog.app.data.entity.BodyMeasurementEntity
import com.ironlog.app.data.entity.CardioSessionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data portability. In most training apps this sits behind the paywall; here it
 * is a first-class feature, partly on principle — a training log is years of the
 * user's own effort and they should be able to take it elsewhere.
 *
 * Two formats, because they serve different needs: CSV opens in any spreadsheet
 * for ad-hoc analysis, and JSON round-trips the full structure for re-import.
 *
 * Every free-text field goes through [Validators.sanitiseForCsv], which quotes
 * the value and neutralises leading `=`, `+`, `-` and `@`. Without that, a user
 * who typed `=cmd|...` into an exercise note would produce a CSV that attempts to
 * execute a formula when the file is opened in Excel — CSV injection.
 */
@Singleton
class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val exportsDir: File
        get() = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }

    private val fileStamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").withZone(ZoneId.systemDefault())

    private val isoStamp: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT

    suspend fun exportWorkoutsCsv(workouts: List<WorkoutWithDetail>): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(exportsDir, "ironlog_workouts_${stamp()}.csv")
                file.bufferedWriter().use { writer ->
                    writer.appendLine(
                        listOf(
                            "workout_date", "workout_name", "duration_minutes",
                            "exercise", "set_number", "set_type",
                            "weight_kg", "reps", "rpe", "volume_kg", "is_pr", "notes"
                        ).joinToString(",")
                    )

                    workouts.forEach { detail ->
                        val date = isoStamp.format(Instant.ofEpochMilli(detail.workout.startedAt))
                        val minutes = detail.workout.durationSeconds / 60

                        detail.exercises.forEach { exercise ->
                            exercise.sets
                                .filter { it.isCompleted }
                                .sortedBy { it.setNumber }
                                .forEach { set ->
                                    val volume = (set.weightKg ?: 0.0) * (set.reps ?: 0)
                                    writer.appendLine(
                                        listOf(
                                            Validators.sanitiseForCsv(date),
                                            Validators.sanitiseForCsv(detail.workout.name),
                                            minutes.toString(),
                                            Validators.sanitiseForCsv(exercise.exercise.name),
                                            set.setNumber.toString(),
                                            Validators.sanitiseForCsv(set.type.name),
                                            set.weightKg?.toString() ?: "",
                                            set.reps?.toString() ?: "",
                                            set.rpe?.toString() ?: "",
                                            "%.2f".format(volume),
                                            if (set.prType != null) "yes" else "no",
                                            Validators.sanitiseForCsv(exercise.workoutExercise.notes)
                                        ).joinToString(",")
                                    )
                                }
                        }
                    }
                }
                file
            }
        }

    suspend fun exportMeasurementsCsv(items: List<BodyMeasurementEntity>): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(exportsDir, "ironlog_measurements_${stamp()}.csv")
                file.bufferedWriter().use { writer ->
                    writer.appendLine(
                        listOf(
                            "date", "weight_kg", "body_fat_percent", "chest_cm", "waist_cm",
                            "hips_cm", "left_arm_cm", "right_arm_cm", "thigh_cm", "calf_cm",
                            "neck_cm", "note"
                        ).joinToString(",")
                    )
                    items.sortedBy { it.takenAt }.forEach { m ->
                        writer.appendLine(
                            listOf(
                                Validators.sanitiseForCsv(isoStamp.format(Instant.ofEpochMilli(m.takenAt))),
                                m.weightKg?.toString() ?: "",
                                m.bodyFatPercent?.toString() ?: "",
                                m.chestCm?.toString() ?: "",
                                m.waistCm?.toString() ?: "",
                                m.hipsCm?.toString() ?: "",
                                m.leftArmCm?.toString() ?: "",
                                m.rightArmCm?.toString() ?: "",
                                m.thighCm?.toString() ?: "",
                                m.calfCm?.toString() ?: "",
                                m.neckCm?.toString() ?: "",
                                Validators.sanitiseForCsv(m.note)
                            ).joinToString(",")
                        )
                    }
                }
                file
            }
        }

    suspend fun exportCardioCsv(sessions: List<CardioSessionEntity>): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(exportsDir, "ironlog_cardio_${stamp()}.csv")
                file.bufferedWriter().use { writer ->
                    writer.appendLine(
                        listOf(
                            "date", "type", "distance_km", "duration_minutes",
                            "avg_pace_per_km", "elevation_gain_m", "steps", "estimated_calories", "note"
                        ).joinToString(",")
                    )
                    sessions.sortedBy { it.startedAt }.forEach { s ->
                        writer.appendLine(
                            listOf(
                                Validators.sanitiseForCsv(isoStamp.format(Instant.ofEpochMilli(s.startedAt))),
                                Validators.sanitiseForCsv(s.type.label),
                                "%.3f".format(s.distanceMeters / 1000.0),
                                "%.1f".format(s.durationSeconds / 60.0),
                                Validators.sanitiseForCsv(
                                    "%d:%02d".format(s.avgPaceSecondsPerKm / 60, s.avgPaceSecondsPerKm % 60)
                                ),
                                "%.1f".format(s.elevationGainMeters),
                                s.steps.toString(),
                                s.estimatedCalories.toString(),
                                Validators.sanitiseForCsv(s.note)
                            ).joinToString(",")
                        )
                    }
                }
                file
            }
        }

    /** Writes a pre-serialised JSON payload produced by the repository layer. */
    suspend fun exportJson(payload: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(exportsDir, "ironlog_backup_${stamp()}.json")
            file.writeText(payload)
            file
        }
    }

    /** Hands the file to the system share sheet with a temporary read grant. */
    fun share(file: File, mimeType: String = "text/csv"): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Export training data") }
    }

    /** Old exports are cleaned up so the directory does not grow without bound. */
    fun pruneOldExports(keep: Int = 5) {
        exportsDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep)
            ?.forEach { it.delete() }
    }

    private fun stamp(): String = fileStamp.format(Instant.now())
}
