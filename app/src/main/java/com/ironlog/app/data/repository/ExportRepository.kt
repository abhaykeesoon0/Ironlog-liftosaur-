package com.ironlog.app.data.repository

import android.content.Intent
import com.ironlog.app.core.media.DataExporter
import com.ironlog.app.data.dao.BodyDao
import com.ironlog.app.data.dao.CardioDao
import com.ironlog.app.data.dao.RoutineDao
import com.ironlog.app.data.dao.WorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportKind(val label: String, val mimeType: String) {
    WORKOUTS_CSV("Workouts (CSV)", "text/csv"),
    MEASUREMENTS_CSV("Body measurements (CSV)", "text/csv"),
    CARDIO_CSV("Cardio sessions (CSV)", "text/csv"),
    FULL_JSON("Full backup (JSON)", "application/json")
}

interface ExportRepository {
    suspend fun export(kind: ExportKind): Result<File>
    fun shareIntent(file: File, kind: ExportKind): Intent
}

@Singleton
class ExportRepositoryImpl @Inject constructor(
    private val exporter: DataExporter,
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val bodyDao: BodyDao,
    private val cardioDao: CardioDao,
    private val currentUser: CurrentUser
) : ExportRepository {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    override suspend fun export(kind: ExportKind): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = currentUser.require()
            exporter.pruneOldExports()
            when (kind) {
                ExportKind.WORKOUTS_CSV -> exporter.exportWorkoutsCsv(workoutDao.allFinished(userId))
                ExportKind.MEASUREMENTS_CSV -> exporter.exportMeasurementsCsv(bodyDao.observeMeasurements(userId).first())
                ExportKind.CARDIO_CSV -> exporter.exportCardioCsv(cardioDao.observeSessions(userId).first())
                ExportKind.FULL_JSON -> exporter.exportJson(json.encodeToString(Backup.serializer(), backup(userId)))
            }.getOrThrow()
        }
    }

    override fun shareIntent(file: File, kind: ExportKind): Intent = exporter.share(file, kind.mimeType)

    private suspend fun backup(userId: String): Backup = Backup(
        exportedAt = System.currentTimeMillis(),
        workouts = workoutDao.allFinished(userId).map { detail ->
            BackupWorkout(
                name = detail.workout.name,
                startedAt = detail.workout.startedAt,
                finishedAt = detail.workout.finishedAt,
                notes = detail.workout.notes,
                exercises = detail.exercises.sortedBy { it.workoutExercise.orderIndex }.map { item ->
                    BackupExercise(
                        exerciseId = item.exercise.id,
                        name = item.exercise.name,
                        notes = item.workoutExercise.notes,
                        sets = item.sets.sortedBy { it.setNumber }.map { set ->
                            BackupSet(
                                setNumber = set.setNumber,
                                type = set.type.name,
                                weightKg = set.weightKg,
                                reps = set.reps,
                                rpe = set.rpe,
                                distanceMeters = set.distanceMeters,
                                durationSeconds = set.durationSeconds
                            )
                        }
                    )
                }
            )
        },
        routines = routineDao.observeRoutines(userId).first().map { routine ->
            BackupRoutine(
                name = routine.routine.name,
                folder = routine.routine.folder,
                exercises = routine.exercises.sortedBy { it.routineExercise.orderIndex }.map {
                    BackupRoutineExercise(
                        exerciseId = it.exercise.id,
                        name = it.exercise.name,
                        targetSets = it.routineExercise.targetSets,
                        targetRepsLow = it.routineExercise.targetRepsLow,
                        targetRepsHigh = it.routineExercise.targetRepsHigh,
                        restSeconds = it.routineExercise.restSeconds
                    )
                }
            )
        },
        measurements = bodyDao.observeMeasurements(userId).first().map {
            BackupMeasurement(it.takenAt, it.weightKg, it.bodyFatPercent, it.waistCm, it.chestCm, it.note)
        },
        cardio = cardioDao.observeSessions(userId).first().map {
            BackupCardio(it.type.name, it.startedAt, it.distanceMeters, it.durationSeconds, it.steps, it.routeJson)
        }
    )
}

// Progress photos are deliberately left out of the backup: the JSON file is meant
// to be shared, and a share sheet is the wrong place for body photos.

@Serializable
data class Backup(
    val format: String = "ironlog-backup",
    val version: Int = 1,
    val exportedAt: Long,
    val workouts: List<BackupWorkout>,
    val routines: List<BackupRoutine>,
    val measurements: List<BackupMeasurement>,
    val cardio: List<BackupCardio>
)

@Serializable
data class BackupWorkout(
    val name: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val notes: String,
    val exercises: List<BackupExercise>
)

@Serializable
data class BackupExercise(val exerciseId: String, val name: String, val notes: String, val sets: List<BackupSet>)

@Serializable
data class BackupSet(
    val setNumber: Int,
    val type: String,
    val weightKg: Double?,
    val reps: Int?,
    val rpe: Double?,
    val distanceMeters: Double?,
    val durationSeconds: Int?
)

@Serializable
data class BackupRoutine(val name: String, val folder: String, val exercises: List<BackupRoutineExercise>)

@Serializable
data class BackupRoutineExercise(
    val exerciseId: String,
    val name: String,
    val targetSets: Int,
    val targetRepsLow: Int,
    val targetRepsHigh: Int,
    val restSeconds: Int
)

@Serializable
data class BackupMeasurement(
    val takenAt: Long,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val waistCm: Double?,
    val chestCm: Double?,
    val note: String
)

@Serializable
data class BackupCardio(
    val type: String,
    val startedAt: Long,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val steps: Int,
    val routeJson: String
)
